package com.karasiq.shadowcloud.persistence.h2

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.Try

import akka.persistence.{AtomicWrite, PersistentRepr}
import akka.persistence.journal.{AsyncWriteJournal, Tagged}
import akka.serialization.SerializationExtension
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.persistence.utils.SCQuillEncoders

final class H2AkkaJournal extends AsyncWriteJournal {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val h2DB = H2DB(context.system)

  import context.dispatcher
  import h2DB.context.db.{run => runQuery, _}
  import h2DB.sc.implicits.materializer

  // -----------------------------------------------------------------------
  // Schema
  // -----------------------------------------------------------------------
  private[this] object schema extends SCQuillEncoders {
    case class DBMessage(ordering: Long, persistenceId: String, sequenceNr: Long, tags: Set[String], message: ByteString)

    implicit val tagsEncoder: Encoder[Set[String]] = encoder(java.sql.Types.ARRAY, (index, value, row) ⇒
      row.setObject(index, value.toArray, java.sql.Types.ARRAY))

    implicit val tagsDecoder: Decoder[Set[String]] = decoder(java.sql.Types.ARRAY, (index, row) ⇒
      row.getArray(index).getArray().asInstanceOf[Array[java.lang.Object]].toSet.asInstanceOf[Set[String]]
    )

    implicit val journalRowSchemaMeta = schemaMeta[DBMessage]("sc_akka_journal")
  }

  import schema._

  //noinspection TypeAnnotation
  private[this] object queries {
    def saveMessages(messages: List[DBMessage]) = quote {
      liftQuery(messages)
        .foreach(jr ⇒ query[DBMessage].insert(jr).returning(_.ordering))
    }

    def deleteMessages(persistenceId: String, toSequenceNr: Long) = quote {
      query[DBMessage]
        .filter(jr ⇒ jr.persistenceId == lift(persistenceId) && jr.sequenceNr <= lift(toSequenceNr))
        .delete
    }

    def messagesForPersistenceId(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long) = quote {
      query[DBMessage]
        .filter(jr ⇒ jr.persistenceId == lift(persistenceId) && jr.sequenceNr >= lift(fromSequenceNr) && jr.sequenceNr <= lift(toSequenceNr))
        .sortBy(_.sequenceNr)(Ord.asc)
    }

    def highestSequenceNr(persistenceId: String) = quote {
      query[DBMessage]
        .filter(jr ⇒ jr.persistenceId == lift(persistenceId))
        .map(_.sequenceNr)
        .max
    }
  }

  // -----------------------------------------------------------------------
  // Conversions
  // -----------------------------------------------------------------------
  private[this] object conversions {
    private[this] val serializer = SerializationExtension(context.system).serializerFor(classOf[PersistentRepr])

    def deserialize(message: ByteString): PersistentRepr = {
      serializer.fromBinary(message.toArray, classOf[PersistentRepr]).asInstanceOf[PersistentRepr]
    }

    def serialize(pr: PersistentRepr): ByteString = {
      ByteString(serializer.toBinary(pr))
    }

    def toJournalRow(pr: PersistentRepr): DBMessage = {
      val (serialized, tags) = pr.payload match {
        case Tagged(payload, tags) ⇒
          serialize(pr.withPayload(payload)) → tags

        case _ ⇒
          serialize(pr) → Set.empty[String]
      }
      DBMessage(0L, pr.persistenceId, pr.sequenceNr, tags, serialized)
    }
  }

  // -----------------------------------------------------------------------
  // Write functions
  // -----------------------------------------------------------------------
  def asyncWriteMessages(messages: immutable.Seq[AtomicWrite]): Future[immutable.Seq[Try[Unit]]] = {
    Source(messages)
      .map(_.payload.filterNot(_.deleted).map(conversions.toJournalRow))
      .mapAsync(1)(rows ⇒ Future(Try[Unit](runQuery(queries.saveMessages(rows.toList)))))
      .runWith(Sink.seq)
  }

  def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    val query = queries.deleteMessages(persistenceId, toSequenceNr)
    Future.fromTry(Try(runQuery(query)))
  }

  def asyncReplayMessages(persistenceId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)
                         (recoveryCallback: (PersistentRepr) ⇒ Unit): Future[Unit] = {
    val maxInt = Math.min(max, Int.MaxValue).toInt
    val query = quote {
      queries.messagesForPersistenceId(persistenceId, fromSequenceNr, toSequenceNr)
        .take(lift(maxInt))
        .map(_.message)
    }
    Source
      .fromFuture(Future.fromTry(Try(runQuery(query))))
      .mapConcat(identity)
      .map(conversions.deserialize)
      .runForeach(recoveryCallback)
      .map(_ ⇒ ())
  }

  def asyncReadHighestSequenceNr(persistenceId: String, fromSequenceNr: Long): Future[Long] = {
    val query = queries.highestSequenceNr(persistenceId)
    Future.fromTry(Try(runQuery(query).getOrElse(0L)))
  }
}
