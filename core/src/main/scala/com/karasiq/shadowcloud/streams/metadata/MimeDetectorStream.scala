package com.karasiq.shadowcloud.streams.metadata

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.metadata.MimeDetector

object MimeDetectorStream {
  def apply(detector: MimeDetector, fileName: String, probeSize: Int): Flow[ByteString, String, NotUsed] = {
    Flow.fromGraph(new MimeDetectorStream(detector, fileName, probeSize))
      .recoverWithRetries(1, { case _ ⇒ Source.single(MimeDetector.DefaultMime) })
      .orElse(Source.single(MimeDetector.DefaultMime))
      .take(1)
  }
}

private final class MimeDetectorStream(detector: MimeDetector, fileName: String, probeSize: Int)
  extends GraphStage[FlowShape[ByteString, String]] {

  val inlet = Inlet[ByteString]("MimeDetectorStream.in")
  val outlet = Outlet[String]("MimeDetectorStream.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] var buffer = ByteString.empty

    private[this] def pushContentType(): Unit = {
      if (buffer.length >= probeSize || isClosed(inlet)) {
        val contentType = detector.getMimeType(fileName, buffer.take(probeSize))
        buffer = ByteString.empty
        push(outlet, contentType.getOrElse(MimeDetector.DefaultMime))
        completeStage()
      } else {
        tryPull(inlet)
      }
    }

    def onPull(): Unit = {
      tryPull(inlet)
    }

    def onPush(): Unit = {
      val element = grab(inlet)
      buffer ++= element
      pushContentType()
    }

    setHandlers(inlet, outlet, this)
  }
}