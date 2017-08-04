package com.karasiq.shadowcloud.compression

import java.io.InputStream

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, StreamConverters}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.utils.ByteStringOutputStream

package object lz4 {
  // TODO: LZ4FrameInputStream, customize compression level
  private type LZ4InputStream = net.jpountz.lz4.LZ4BlockInputStream
  private type LZ4OutputStream = net.jpountz.lz4.LZ4BlockOutputStream

  object LZ4Streams {
    def compress: Flow[ByteString, ByteString, NotUsed] = {
      Flow.fromGraph(new LZ4Compress()).named("lz4Compress")
    }

    // TODO: Non-blocking decompress
    def decompress: Flow[ByteString, ByteString, NotUsed] = {
      val graph = GraphDSL.create(StreamConverters.asInputStream()) { implicit builder ⇒ inputStream ⇒
        import GraphDSL.Implicits._

        val processInputStream = builder.add(Flow[InputStream].flatMapConcat { inputStream ⇒
          StreamConverters.fromInputStream(() ⇒ new LZ4InputStream(inputStream))
        })

        builder.materializedValue ~> processInputStream
        FlowShape(inputStream.in, processInputStream.out)
      }
      Flow.fromGraph(graph).mapMaterializedValue(_ ⇒ NotUsed).named("lz4Decompress")
    }
  }

  private class LZ4Compress extends GraphStage[FlowShape[ByteString, ByteString]] {
    val inlet = Inlet[ByteString]("LZ4Compress.in")
    val outlet = Outlet[ByteString]("LZ4Compress.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val bsOutputStream = ByteStringOutputStream()
      private[this] val lz4OutputStream = new LZ4OutputStream(bsOutputStream)

      def onPull(): Unit = {
        tryPull(inlet)
      }

      def onPush(): Unit = {
        val element = grab(inlet)
        lz4OutputStream.write(element.toArray)

        val output = bsOutputStream.toByteString
        if (output.nonEmpty) {
          push(outlet, output)
          bsOutputStream.clear()
        } else {
          tryPull(inlet)
        }
      }

      override def onUpstreamFinish(): Unit = {
        lz4OutputStream.close()
        val lastBlock = bsOutputStream.toByteString
        if(lastBlock.nonEmpty) {
          bsOutputStream.clear()
          emit(outlet, lastBlock, () ⇒ complete(outlet))
        } else {
          complete(outlet)
        }
      }

      override def postStop(): Unit = {
        lz4OutputStream.close()
        super.postStop()
      }

      setHandlers(inlet, outlet, this)
    }
  }
}
