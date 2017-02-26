package com.karasiq.shadowcloud.streams

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import com.karasiq.shadowcloud.crypto._

import scala.language.postfixOps
import scala.util.Random

object ChunkKeyStream {
  def apply(encryptionMethod: EncryptionMethod = EncryptionMethod.default): ChunkKeyStream = {
    new ChunkKeyStream(encryptionMethod)
  }
}

final class ChunkKeyStream(encryptionMethod: EncryptionMethod) extends GraphStage[SourceShape[EncryptionParameters]] {
  val outlet = Outlet[EncryptionParameters]("ChunkKeyStream.out")
  val shape = SourceShape(outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    private[this] val encryptionModule = EncryptionModule(encryptionMethod)
    private[this] var keyParameters = encryptionModule.createParameters()
    private[this] var encryptedCount = 0
    private[this] var changeKeyIn = Random.nextInt(256)

    // Update IV/key
    private[this] def updateKey(): Unit = {
      encryptedCount += 1
      if (encryptedCount > changeKeyIn) {
        // TODO: Log key changes
        keyParameters = encryptionModule.createParameters()
        changeKeyIn = Random.nextInt(256)
        encryptedCount = 0
      } else {
        keyParameters = encryptionModule.updateParameters(keyParameters)
      }
    }

    def onPull(): Unit = {
      push(outlet, keyParameters)
      updateKey()
    }

    setHandler(outlet, this)
  }
}