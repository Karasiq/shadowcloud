package com.karasiq.shadowcloud.streams

import java.io.IOException

import akka.stream.stage._
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule}
import com.karasiq.shadowcloud.index.{Checksum, Chunk}

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

case class IndexedFile(checksum: Checksum, chunks: Seq[Chunk])

class FileIndexer(hashingMethod: HashingMethod) extends GraphStageWithMaterializedValue[FlowShape[Chunk, Chunk], Future[IndexedFile]] {
  val inlet = Inlet[Chunk]("FileIndexer.in")
  val outlet = Outlet[Chunk]("FileIndexer.out")

  val shape = FlowShape(inlet, outlet)

  @scala.throws[Exception](classOf[Exception])
  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[IndexedFile]) = {
    val promise = Promise[IndexedFile]
    val plainHash = HashingModule(hashingMethod)
    val encryptedHash = HashingModule(hashingMethod)
    var plainSize = 0L
    var encryptedSize = 0L
    val chunks = Vector.newBuilder[Chunk]
    val logic = new GraphStageLogic(shape) {
      setHandler(inlet, new InHandler {
        def onPush(): Unit = {
          val chunk = grab(inlet)
          plainHash.update(chunk.data.plain)
          encryptedHash.update(chunk.data.encrypted)
          plainSize += chunk.data.plain.length
          encryptedSize += chunk.data.encrypted.length
          chunks += chunk.withoutData
          push(outlet, chunk)
        }

        override def onUpstreamFinish(): Unit = {
          val indexedFile = IndexedFile(Checksum(hashingMethod, plainSize, plainHash.createHash(), encryptedSize, encryptedHash.createHash()), chunks.result())
          promise.trySuccess(indexedFile)
          completeStage()
        }
      })

      setHandler(outlet, new OutHandler {
        def onPull(): Unit = {
          tryPull(inlet)
        }

        override def onDownstreamFinish(): Unit = {
          val exception = new IOException("Downstream terminated")
          if (promise.tryFailure(exception)) {
            failStage(exception)
          } else {
            completeStage()
          }
        }
      })

      override def postStop(): Unit = {
        promise.tryFailure(new IOException("Stream terminated"))
        super.postStop()
      }
    }
    (logic, promise.future)
  }
}
