package com.karasiq.shadowcloud.test.utils

import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._

trait ActorSpecImplicits { self: ActorSpec ⇒
  implicit val defaultTimeout = Timeout(15 seconds)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
}
