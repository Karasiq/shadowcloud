package com.karasiq.shadowcloud.drive.utils

import akka.NotUsed
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.Flow
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

private[drive] object WritesOptimizeStage {
  def apply(chunkSize: Long): Flow[PendingChunkIO, PendingChunkIO, NotUsed] = {
    Flow.fromGraph(new WritesOptimizeStage(chunkSize))
  }
}

private[drive] class WritesOptimizeStage(chunkSize: Long) extends GraphStage[FlowShape[PendingChunkIO, PendingChunkIO]] {
  val inlet = Inlet[PendingChunkIO]("WritesFoldStage.in")
  val outlet = Outlet[PendingChunkIO]("WritesFoldStage.out")
  val shape = FlowShape(inlet, outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    var appendOffset = 0L
    var currentOffset = 0L
    var appendBuffer = ByteString.empty

    def addAppendBytes(bs: ByteString): Unit = {
      if (appendBuffer.isEmpty) appendOffset = currentOffset
      appendBuffer ++= bs
    }

    def emitAppends(threshold: Int = 0, splitSize: Int = chunkSize.toInt): Unit = {
      require(threshold >= 0 && splitSize > 0)
      if (appendBuffer.isEmpty || appendBuffer.length < threshold) return

      val (drop, keep) = appendBuffer.splitAt(splitSize)
      val appendOp = PendingChunkIO.Append(ChunkRanges.Range(appendOffset, appendOffset + drop.length), drop)
      appendBuffer = keep
      appendOffset += drop.length

      emit(outlet, appendOp)
      emitAppends(threshold, splitSize)
    }

    def emitFullAppends(): Unit = {
      emitAppends(threshold = chunkSize.toInt, splitSize = chunkSize.toInt)
    }

    def onPull(): Unit = {
      if (!hasBeenPulled(inlet)) tryPull(inlet)
    }

    def onPush(): Unit = {
      val element = grab(inlet)
      element match {
        case PendingChunkIO.Rewrite(range, _, patches) if element.range.start == currentOffset && range.size < chunkSize && patches.canReplace(range.size) ⇒
          addAppendBytes(patches.toBytes(range.size.toInt))
          emitFullAppends()

        case PendingChunkIO.Append(_, newData) if element.range.start == currentOffset ⇒
          addAppendBytes(newData)
          emitFullAppends()

        case _ ⇒
          emitAppends()
          emit(outlet, element)
      }

      currentOffset = element.range.end
      if (!hasBeenPulled(inlet)) tryPull(inlet)
    }

    override def onUpstreamFinish(): Unit = {
      emitAppends()
      super.onUpstreamFinish()
    }
    
    setHandlers(inlet, outlet, this)
  }
}

