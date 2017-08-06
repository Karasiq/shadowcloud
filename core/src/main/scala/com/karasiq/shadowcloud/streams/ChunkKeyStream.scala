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

  private def isKeyReused(p1: EncryptionParameters, p2: EncryptionParameters) = (p1, p2) match {
    case (_, _)
      if CryptoMethod.isNoOpMethod(p1.method) && CryptoMethod.isNoOpMethod(p2.method) ⇒ false

    case (sp1: SymmetricEncryptionParameters, sp2: SymmetricEncryptionParameters) ⇒
      sp1.key == sp2.key

    case (ap1: AsymmetricEncryptionParameters, ap2: AsymmetricEncryptionParameters) ⇒
      ap1.publicKey == ap2.publicKey || ap1.privateKey == ap2.privateKey

    case _ ⇒ false
  }

  private def isNonceReused(p1: EncryptionParameters, p2: EncryptionParameters) = (p1, p2) match {
    case (_, _)
      if CryptoMethod.isNoOpMethod(p1.method) && CryptoMethod.isNoOpMethod(p2.method) ⇒ false

    case (sp1: SymmetricEncryptionParameters, sp2: SymmetricEncryptionParameters) ⇒
      sp1.nonce == sp2.nonce

    case _ ⇒ false
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
        val newParameters = encryptionModule.createParameters()
        if (ChunkKeyStream.isKeyReused(keyParameters, newParameters) || ChunkKeyStream.isNonceReused(keyParameters, newParameters)) {
          failStage(new IllegalArgumentException("Key or nonce is reused"))
        }
        keyParameters = newParameters
        changeKeyIn = secureRandom.nextInt(256)
        encryptedCount = 0
      } else {
        val updatedParameters = encryptionModule.updateParameters(keyParameters)
        if (ChunkKeyStream.isNonceReused(keyParameters, updatedParameters)) {
          failStage(new IllegalArgumentException("Nonce is reused"))
        }
        keyParameters = updatedParameters
      }
    }

    def onPull(): Unit = {
      push(outlet, keyParameters)
      updateKey()
    }

    setHandler(outlet, this)
  }
}