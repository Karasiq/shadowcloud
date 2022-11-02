package com.karasiq.shadowcloud.crypto.libsodium.signing

import akka.util.ByteString
import org.abstractj.kalium.keys.{SigningKey, VerifyKey}

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.model.crypto.{HashingMethod, SignMethod, SignParameters}
import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._

private[libsodium] object CryptoSignModule {
  val Algorithm = "Ed25519"

  def apply(method: SignMethod = SignMethod(Algorithm, HashingMethod.default)): CryptoSignModule = {
    new CryptoSignModule(method)
  }
}

/** @see [[https://download.libsodium.org/doc/public-key_cryptography/public-key_signatures.html]]
  */
private[libsodium] final class CryptoSignModule(val method: SignMethod) extends SignModule {
  def createParameters(): SignParameters = {
    val signingKey = new SigningKey()
    val verifyKey  = signingKey.getVerifyKey
    SignParameters(method, publicKey = ByteString.fromArrayUnsafe(verifyKey.toBytes), privateKey = ByteString.fromArrayUnsafe(signingKey.toBytes))
  }

  def sign(data: ByteString, parameters: SignParameters): ByteString = {
    val signingKey = new SigningKey(parameters.privateKey.toArrayUnsafe)
    val signature  = signingKey.sign(data.toArrayUnsafe)
    ByteString.fromArrayUnsafe(signature)
  }

  def verify(data: ByteString, signature: ByteString, parameters: SignParameters): Boolean = {
    val verifyKey = new VerifyKey(parameters.publicKey.toArrayUnsafe)
    try {
      verifyKey.verify(data.toArrayUnsafe, signature.toArrayUnsafe)
    } catch {
      case exc: RuntimeException if exc.getMessage.contains("signature was forged or corrupted") ⇒
        false
    }
  }
}
