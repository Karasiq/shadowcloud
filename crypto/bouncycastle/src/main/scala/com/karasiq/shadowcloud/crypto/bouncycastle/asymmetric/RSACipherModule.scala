package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import scala.language.postfixOps

import org.bouncycastle.crypto.encodings.OAEPEncoding
import org.bouncycastle.crypto.engines.RSAEngine

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.KeyUtils

private[bouncycastle] object RSACipherModule {
  def apply(method: EncryptionMethod = EncryptionMethod("RSA", 4096)): RSACipherModule = {
    new RSACipherModule(method)
  }
}

private[bouncycastle] final class RSACipherModule(val method: EncryptionMethod) extends BCAsymmetricCipherModule {
  protected val cipher = new OAEPEncoding(new RSAEngine)
  protected val keyPairGenerator = KeyUtils.rsaKeyGenerator(method.keySize)

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    cipher.init(encrypt, getCipherKey(parameters, encrypt))
  }
}
