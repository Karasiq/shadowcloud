package com.karasiq.shadowcloud.streams

import java.io.IOException

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import akka.Done
import akka.stream._
import akka.stream.stage._

import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.index.{Checksum, Chunk}
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.streams.FileIndexer.Result

private[shadowcloud] object FileIndexer {
  case class Result(checksum: Checksum, chunks: Seq[Chunk], ioResult: IOResult)

  def apply(registry: SCModules, plainHashing: HashingMethod = HashingMethod.default,
            encryptedHashing: HashingMethod = HashingMethod.default): FileIndexer = {
    new FileIndexer(registry, plainHashing, encryptedHashing)
  }
}

// TODO: Content type
private[shadowcloud] final class FileIndexer(registry: SCModules, plainHashing: HashingMethod, encryptedHashing: HashingMethod)
  extends GraphStageWithMaterializedValue[SinkShape[Chunk], Future[Result]] {

  val inlet = Inlet[Chunk]("FileIndexer.in")
  val shape = SinkShape(inlet)

  @scala.throws[Exception](classOf[Exception])
  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Result]) = {
    val promise = Promise[Result]
    val logic = new GraphStageLogic(shape) with InHandler {
      private[this] val hasher = registry.streamHashingModule(plainHashing)
      private[this] val encHasher = registry.streamHashingModule(encryptedHashing)
      private[this] var plainSize = 0L
      private[this] var encryptedSize = 0L
      private[this] val chunks = Vector.newBuilder[Chunk]

      private[this] def update(chunk: Chunk): Unit = {
        hasher.update(chunk.data.plain)
        encHasher.update(chunk.data.encrypted)
        plainSize += chunk.data.plain.length
        encryptedSize += chunk.data.encrypted.length
        chunks += chunk.withoutData
      }

      private[this] def finish(status: Try[Done]): Unit = {
        val checksum = Checksum(plainHashing, encryptedHashing, plainSize, hasher.createHash(),
          encryptedSize, encHasher.createHash())
        val indexedFile = Result(checksum, chunks.result(), IOResult(plainSize, status))
        promise.trySuccess(indexedFile)
      }

      def onPush(): Unit = {
        val chunk = grab(inlet)
        pull(inlet)
        update(chunk)
      }

      override def onUpstreamFinish(): Unit = {
        finish(Success(Done))
        completeStage()
      }


      override def onUpstreamFailure(ex: Throwable): Unit = {
        finish(Failure(ex))
        super.onUpstreamFailure(ex)
      }

      override def preStart(): Unit = {
        super.preStart()
        tryPull(inlet)
      }

      override def postStop(): Unit = {
        finish(Failure(new IOException("Stream terminated")))
        super.postStop()
      }

      setHandler(inlet, this)
    }
    (logic, promise.future)
  }
}
