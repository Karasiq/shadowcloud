package com.karasiq.shadowcloud.streams.chunk

import java.security.SecureRandom

import scala.language.postfixOps

import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.providers.SCModules

private[shadowcloud] object ChunkKeyStream {
  def apply(registry: SCModules,
            method: EncryptionMethod = EncryptionMethod.default,
            maxKeyReuse: Int = 256): ChunkKeyStream = {
    new ChunkKeyStream(registry, method, maxKeyReuse)
  }

  private def isKeyReused(p1: EncryptionParameters, p2: EncryptionParameters) = (p1, p2) match {
    case (_, _) if CryptoMethod.isNoOpMethod(p1.method) && CryptoMethod.isNoOpMethod(p2.method) ⇒
      false

    case (sp1: SymmetricEncryptionParameters, sp2: SymmetricEncryptionParameters) ⇒
      sp1.key == sp2.key

    case (ap1: AsymmetricEncryptionParameters, ap2: AsymmetricEncryptionParameters) ⇒
      ap1.publicKey == ap2.publicKey || ap1.privateKey == ap2.privateKey

    case _ ⇒
      false
  }

  private def isNonceReused(p1: EncryptionParameters, p2: EncryptionParameters) = (p1, p2) match {
    case (_, _) if CryptoMethod.isNoOpMethod(p1.method) && CryptoMethod.isNoOpMethod(p2.method) ⇒
      false

    case (sp1: SymmetricEncryptionParameters, sp2: SymmetricEncryptionParameters) ⇒
      sp1.nonce == sp2.nonce

    case _ ⇒
      false
  }
}

private[shadowcloud] final class ChunkKeyStream(modules: SCModules, method: EncryptionMethod, maxKeyReuse: Int)
  extends GraphStage[SourceShape[EncryptionParameters]] {

  val outlet = Outlet[EncryptionParameters]("ChunkKeyStream.out")
  val shape = SourceShape(outlet)

  def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with OutHandler {
    private[this] val encryptionModule = modules.crypto.encryptionModule(method)
    private[this] var secureRandom: SecureRandom = _
    private[this] var keyParameters: EncryptionParameters = _
    private[this] var encryptedCount: Int = _
    private[this] var changeKeyIn: Int = _

    private[this] def resetParametersAndCounter(): Unit = { // TODO: Log key changes
      this.keyParameters = encryptionModule.createParameters()
      this.encryptedCount = 0
      this.changeKeyIn = if (maxKeyReuse > 0) {
        if (secureRandom == null) secureRandom = new SecureRandom()
        secureRandom.nextInt(maxKeyReuse)
      } else {
        maxKeyReuse
      }
    }

    private[this] def updateParameters(): Unit = {
      this.keyParameters = encryptionModule.updateParameters(this.keyParameters)
    }

    // Update IV/key
    private[this] def updateOrResetKey(): Unit = {
      val oldParameters = this.keyParameters
      if (encryptedCount > changeKeyIn) {
        resetParametersAndCounter()
        if (ChunkKeyStream.isKeyReused(oldParameters, keyParameters) || ChunkKeyStream.isNonceReused(oldParameters, keyParameters)) {
          failStage(CryptoException.ReuseError(new IllegalArgumentException("Key or nonce is reused")))
        }
      } else {
        updateParameters()
        if (ChunkKeyStream.isNonceReused(oldParameters, keyParameters)) {
          failStage(CryptoException.ReuseError(new IllegalArgumentException("Nonce is reused")))
        }
      }
    }

    def onPull(): Unit = {
      if (keyParameters == null) resetParametersAndCounter()
      push(outlet, keyParameters)
      encryptedCount += 1
      updateOrResetKey()
    }

    setHandler(outlet, this)
  }
}