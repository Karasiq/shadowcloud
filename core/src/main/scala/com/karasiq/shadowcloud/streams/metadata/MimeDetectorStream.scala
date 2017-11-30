package com.karasiq.shadowcloud.streams.metadata

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.actors.SCDispatchers
import com.karasiq.shadowcloud.metadata.MimeDetector

object MimeDetectorStream {
  def apply(detector: MimeDetector, fileName: String, probeSize: Int): Flow[ByteString, String, NotUsed] = {
    val defaultMimeSource = Source.single(MimeDetector.DefaultMime)
    Flow.fromGraph(new MimeDetectorStream(detector, fileName, probeSize))
      .recoverWithRetries(1, { case _ ⇒ defaultMimeSource })
      .orElse(defaultMimeSource)
      .withAttributes(ActorAttributes.dispatcher(SCDispatchers.metadata))
      .named("mimeDetect")
  }
}

private final class MimeDetectorStream(detector: MimeDetector, fileName: String, probeSize: Int)
  extends GraphStage[FlowShape[ByteString, String]] {

  val inBytes = Inlet[ByteString]("MimeDetectorStream.in")
  val outContentType = Outlet[String]("MimeDetectorStream.out")
  val shape = FlowShape(inBytes, outContentType)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] var buffer = ByteString.empty

    private[this] def pushContentType(): Unit = {
      if (buffer.length >= probeSize || isClosed(inBytes)) {
        val contentType = detector.getMimeType(fileName, buffer/*.take(probeSize)*/)
        buffer = ByteString.empty
        contentType.foreach(push(outContentType, _))
        completeStage()
      } else {
        tryPull(inBytes)
      }
    }

    def onPull(): Unit = {
      tryPull(inBytes)
    }

    def onPush(): Unit = {
      val element = grab(inBytes)
      buffer ++= element
      pushContentType()
    }

    override def onUpstreamFinish(): Unit = {
      pushContentType()
    }

    setHandlers(inBytes, outContentType, this)
  }
}