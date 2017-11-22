package com.karasiq.shadowcloud.actors.utils

import scala.concurrent.Future

import akka.actor.ActorRef

trait SessionAuthenticator[Session] {
  def getSession(): Future[Session]
  def createDispatcher(session: Session): ActorRef
}
