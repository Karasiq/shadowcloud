package com.karasiq.shadowcloud.shell

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.util.Timeout

import com.karasiq.shadowcloud.ShadowCloud

private[shell] object ShellContext {
  def apply(): ShellContext = {
    new ShellContext()
  }
}

private[shell] final class ShellContext {
  implicit val actorSystem = ActorSystem("shadowcloud-shell")
  val sc = ShadowCloud(actorSystem)
  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val materializer = sc.implicits.materializer
  implicit val executionContext = sc.implicits.executionContext
}
