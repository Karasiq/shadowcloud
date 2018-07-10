package com.karasiq.shadowcloud.persistence.h2

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

import akka.persistence._
import akka.persistence.serialization.Snapshot
import akka.persistence.snapshot.SnapshotStore
import akka.serialization.SerializationExtension
import akka.util.ByteString

import com.karasiq.shadowcloud.persistence.utils.SCQuillEncoders

final class H2AkkaSnapshotStore extends SnapshotStore {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val h2db = H2DB(context.system)
  import h2db.context.{run ⇒ runQuery, _}

  // -----------------------------------------------------------------------
  // Schema
  // -----------------------------------------------------------------------
  private[this] object schema extends SCQuillEncoders {
    case class DBSnapshot(persistenceId: String, sequenceNr: Long, timestamp: Long, snapshot: ByteString)
    implicit val snapshotSchemaMeta = schemaMeta[DBSnapshot]("sc_akka_snapshots")
  }

  import schema._
  //noinspection TypeAnnotation
  private[this] object queries {
    def snapshotBySequenceNr(persistenceId: String, sequenceNr: Long) = quote {
      query[DBSnapshot]
        .filter(s ⇒ s.persistenceId == lift(persistenceId) && s.sequenceNr == lift(sequenceNr))
    }

    def snapshotsByCriteria(persistenceId: String, minSequenceNr: Long, maxSequenceNr: Long,
                           minTimestamp: Long, maxTimestamp: Long) = quote {
      query[DBSnapshot].filter(s ⇒ s.persistenceId == lift(persistenceId) &&
        s.sequenceNr >= lift(minSequenceNr) && s.sequenceNr <= lift(maxSequenceNr) &&
        s.timestamp >= lift(minTimestamp) && s.timestamp <= lift(maxTimestamp))
    }

    def saveSnapshot(data: DBSnapshot) = quote {
      query[DBSnapshot].insert(lift(data))
    }
  }

  // -----------------------------------------------------------------------
  // Conversions
  // -----------------------------------------------------------------------
  private[this] object conversions {
    private[this] val serializer = SerializationExtension(context.system).serializerFor(classOf[Snapshot])

    private[this] def serializeSnapshot(data: Any): ByteString = {
      ByteString.fromArrayUnsafe(serializer.toBinary(Snapshot(data)))
    }

    private[this] def deserializeSnapshot(data: ByteString): Any = {
      serializer.fromBinary(data.toArray, classOf[Snapshot]).asInstanceOf[Snapshot].data
    }

    def toSelectedSnapshot(snapshot: DBSnapshot): SelectedSnapshot = {
      SelectedSnapshot(SnapshotMetadata(snapshot.persistenceId, snapshot.sequenceNr,
        snapshot.timestamp), deserializeSnapshot(snapshot.snapshot))
    }

    def toDBSnapshot(metadata: SnapshotMetadata, data: Any): DBSnapshot = {
      DBSnapshot(metadata.persistenceId, metadata.sequenceNr, metadata.timestamp, serializeSnapshot(data))
    }
  }

  // -----------------------------------------------------------------------
  // Snapshot store functions
  // -----------------------------------------------------------------------
  def loadAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Option[SelectedSnapshot]] = {
    val query = quote {
      queries.snapshotsByCriteria(persistenceId, criteria.minSequenceNr,
        criteria.maxSequenceNr, criteria.minTimestamp, criteria.maxTimestamp)
        .sortBy(_.sequenceNr)(Ord.desc)
        .take(1)
    }
    Future.fromTry(Try(runQuery(query).headOption.map(conversions.toSelectedSnapshot)))
  }

  def saveAsync(metadata: SnapshotMetadata, snapshot: Any): Future[Unit] = {
    val query = queries.saveSnapshot(conversions.toDBSnapshot(metadata, snapshot))
    Future.fromTry(Try(runQuery(query)))
  }

  def deleteAsync(metadata: SnapshotMetadata): Future[Unit] = {
    val query = quote(queries.snapshotBySequenceNr(metadata.persistenceId, metadata.sequenceNr).delete)
    Future.fromTry(Try(runQuery(query)))
  }

  def deleteAsync(persistenceId: String, criteria: SnapshotSelectionCriteria): Future[Unit] = {
    val query = quote(queries.snapshotsByCriteria(persistenceId, criteria.minSequenceNr,
      criteria.maxSequenceNr, criteria.minTimestamp, criteria.maxTimestamp).delete)
    Future.fromTry(Try(runQuery(query)))
  }
}
