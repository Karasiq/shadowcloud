package com.karasiq.shadowcloud.streams

import java.security.SecureRandom

import scala.language.postfixOps

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.providers.SCModules

private[shadowcloud] object ChunkKeyStream {
  def apply(registry: SCModules, method: EncryptionMethod = EncryptionMethod.default): ChunkKeyStream = {
    new ChunkKeyStream(registry, method)
  }
}

private[shadowcloud] final class ChunkKeyStream(modules: SCModules, method: EncryptionMethod) extends GraphStage[SourceShape[EncryptionParameters]] {
  val outlet = Outlet[EncryptionParameters]("ChunkKeyStream.out")
  val shape = SourceShape(outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    private[this] val secureRandom = new SecureRandom()
    private[this] val encryptionModule = modules.crypto.encryptionModule(method)
    private[this] var keyParameters = encryptionModule.createParameters()
    private[this] var encryptedCount = 0
    private[this] var changeKeyIn = secureRandom.nextInt(256)

    // Update IV/key
    private[this] def updateKey(): Unit = {
      encryptedCount += 1
      if (encryptedCount > changeKeyIn) {
        // TODO: Log key changes
        keyParameters = encryptionModule.createParameters()
        changeKeyIn = secureRandom.nextInt(256)
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