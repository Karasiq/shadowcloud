package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import scala.language.postfixOps

import org.bouncycastle.crypto.encodings.OAEPEncoding
import org.bouncycastle.crypto.engines.RSAEngine

import com.karasiq.shadowcloud.crypto.{EncryptionModule, EncryptionModuleStreamer}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.RSAUtils
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters}

private[bouncycastle] object RSACipherModule {
  def apply(method: EncryptionMethod = EncryptionMethod("RSA", 4096)): RSACipherModule = {
    new RSACipherModule(method)
  }
}

private[bouncycastle] final class RSACipherModule(val method: EncryptionMethod) extends BCAsymmetricCipherModule {
  protected val keyPairGenerator = RSAUtils.createKeyGenerator(method)

  def createStreamer(): EncryptionModuleStreamer = {
    new RSACipherStreamer
  }

  protected class RSACipherStreamer extends BCAsymmetricBlockCipherStreamer {
    protected val cipher = new OAEPEncoding(new RSAEngine)

    def module: EncryptionModule = {
      RSACipherModule.this
    }

    def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
      cipher.init(encrypt, BCAsymmetricCipherKeys.getCipherKey(parameters, encrypt))
    }
  }
}
