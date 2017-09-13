package com.karasiq.shadowcloud.test.utils

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.stream.ActorMaterializer
import akka.util.Timeout

trait ActorSpecImplicits { self: ActorSpec ⇒
  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
}
