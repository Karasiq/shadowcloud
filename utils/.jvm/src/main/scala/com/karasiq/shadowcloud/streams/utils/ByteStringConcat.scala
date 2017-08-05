package com.karasiq.shadowcloud.streams.utils

import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

object ByteStringConcat {
  def apply(): Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new ByteStringConcat)
  }
}

private final class ByteStringConcat extends GraphStage[FlowShape[ByteString, ByteString]] {
  val inlet = Inlet[ByteString]("ByteStringConcat.in")
  val outlet = Outlet[ByteString]("ByteStringConcat.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) with InHandler with OutHandler {
    private[this] val builder = ByteString.newBuilder
    builder.sizeHint(256)

    def onPull(): Unit = {
      if (isClosed(inlet)) {
        push(outlet, builder.result())
        completeStage()
      } else {
        pull(inlet)
      }
    }

    def onPush(): Unit = {
      builder.append(grab(inlet))
      pull(inlet)
    }

    override def onUpstreamFinish(): Unit = {
      if (isAvailable(outlet)) {
        push(outlet, builder.result())
        completeStage()
      }
    }

    setHandlers(inlet, outlet, this)
  }
}
