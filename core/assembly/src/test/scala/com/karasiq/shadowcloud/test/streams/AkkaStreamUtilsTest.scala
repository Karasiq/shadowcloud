package com.karasiq.shadowcloud.test.streams

import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import akka.util.ByteString
import org.apache.commons.io.IOUtils
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils
import com.karasiq.shadowcloud.test.utils.{ActorSpec, ActorSpecImplicits}

class AkkaStreamUtilsTest extends ActorSpec with ActorSpecImplicits with FlatSpecLike {
  "Input stream" should "be created" in {
    val probe = Source(List(ByteString(1, 2, 3, 4, 5), ByteString(6, 7, 8, 9, 10)))
      .via(AkkaStreamUtils.writeInputStream { inputStream ⇒
        val bytes = ByteString.fromArrayUnsafe(IOUtils.toByteArray(inputStream))
        Source.single(bytes)
      })
      .runWith(TestSink.probe)

    probe.request(2)
    probe.expectNext(ByteString(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    probe.expectComplete()
  }
}
