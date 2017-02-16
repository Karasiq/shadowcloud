package com.karasiq.shadowcloud.streams

import akka.NotUsed
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.util.ByteString

import scala.language.postfixOps

object ByteStringConcat {
  def apply(): Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new ByteStringConcat)
  }
}

private final class ByteStringConcat extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("ByteStringConcat.in")
  val out = Outlet[ByteString]("ByteStringConcat.out")
  val shape = FlowShape(in, out)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    val builder = ByteString.newBuilder
    builder.sizeHint(256)

    setHandler(in, new InHandler {
      def onPush(): Unit = {
        builder.append(grab(in))
        pull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (isAvailable(out)) {
          push(out, builder.result())
          completeStage()
        }
      }
    })

    setHandler(out, new OutHandler {
      def onPull(): Unit = {
        if (isClosed(in)) {
          push(out, builder.result())
          completeStage()
        } else {
          pull(in)
        }
      }
    })
  }
}
