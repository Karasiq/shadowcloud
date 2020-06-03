package com.karasiq.shadowcloud.test.utils

import akka.stream.Materializer
import akka.util.Timeout

import scala.concurrent.duration._

trait ActorSpecImplicits { self: ActorSpec ⇒
  implicit val defaultTimeout   = Timeout(15 seconds)
  implicit val materializer     = Materializer.matFromSystem
  implicit val executionContext = system.dispatcher
}
