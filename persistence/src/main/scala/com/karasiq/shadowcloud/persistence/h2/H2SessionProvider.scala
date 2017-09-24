package com.karasiq.shadowcloud.persistence.h2

import scala.concurrent.Future

import akka.Done
import akka.actor.ActorSystem
import akka.util.ByteString

import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.persistence.utils.SCQuillEncoders
import com.karasiq.shadowcloud.providers.SessionProvider

final class H2SessionProvider(actorSystem: ActorSystem) extends SessionProvider {
  private[this] val h2DB = H2DB(actorSystem)
  import h2DB.context.{run ⇒ runQuery, _}
  import h2DB.settings.executionContext

  private[this] object schema extends SCQuillEncoders {
    case class DBSession(storageId: StorageId, key: String, data: ByteString)
    implicit val sessionsSchemaMeta = schemaMeta[DBSession]("sc_sessions")
  }

  import schema._

  private[this] object queries {
    def getSessions(storageId: StorageId) = quote {
      query[DBSession]
        .filter(_.storageId == lift(storageId))
        .map(_.key)
    }

    def createSession(storageId: StorageId, key: String, data: ByteString) = quote {
      query[DBSession]
        .insert(lift(DBSession(storageId, key, data)))
    }

    def updateSession(storageId: StorageId, key: String, data: ByteString) = quote {
      query[DBSession]
        .filter(s ⇒ s.storageId == lift(storageId) && s.key == lift(key))
        .update(_.data → lift(data))
    }

    def getSession(storageId: StorageId, key: String) = quote {
      query[DBSession]
        .filter(s ⇒ s.storageId == lift(storageId) && s.key == lift(key))
        .map(_.data)
    }

    def deleteSessions(storageId: StorageId) = quote {
      query[DBSession]
        .filter(_.storageId == lift(storageId))
        .delete
    }

    def deleteSession(storageId: StorageId, key: String) = quote {
      query[DBSession]
        .filter(s ⇒ s.storageId == lift(storageId) && s.key == lift(key))
        .delete
    }
  }

  def getSessions(storageId: StorageId) = {
    val query = queries.getSessions(storageId)
    Future(runQuery(query).toSet)
  }

  def storeSession(storageId: StorageId, key: String, data: ByteString) = {
    Future(runQuery(queries.createSession(storageId, key, data)))
      .recoverWith { case _ ⇒ Future(runQuery(queries.updateSession(storageId, key, data))) }
      .map(_ ⇒ Done)
  }

  def loadSession(storageId: StorageId, key: String) = {
    val query = queries.getSession(storageId, key)
    Future(runQuery(query).head)
  }

  def dropSessions(storageId: StorageId) = {
    val query = queries.deleteSessions(storageId)
    Future(runQuery(query)).map(_ ⇒ Done)
  }

  def dropSession(storageId: StorageId, key: String) = {
    val query = queries.deleteSession(storageId, key)
    Future(runQuery(query)).map(_ ⇒ Done)
  }
}
