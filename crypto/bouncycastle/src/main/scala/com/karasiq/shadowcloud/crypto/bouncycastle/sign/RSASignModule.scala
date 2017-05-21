package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import java.math.BigInteger
import java.security.SecureRandom

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.crypto.signers.RSADigestSigner
import org.bouncycastle.crypto.util.{PrivateKeyFactory, PrivateKeyInfoFactory, PublicKeyFactory, SubjectPublicKeyInfoFactory}

import com.karasiq.shadowcloud.crypto.{HashingMethod, SignMethod, SignParameters, StreamSignModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{DigestWrapper, MessageDigestModule}

private[bouncycastle] object RSASignModule {
  def apply(method: SignMethod = SignMethod("RSA", HashingMethod("SHA-512"), 4096)): RSASignModule = {
    new RSASignModule(method)
  }
}

private[bouncycastle] final class RSASignModule(val method: SignMethod) extends StreamSignModule {
  private[this] var signer: RSADigestSigner = _
  private[this] val keyPairGenerator = {
    val generator = new RSAKeyPairGenerator
    generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(65537), new SecureRandom(), method.keySize, 12))
    generator
  }

  def init(sign: Boolean, parameters: SignParameters): Unit = {
    val cipherParameters = {
      if (sign) {
        PrivateKeyFactory.createKey(parameters.privateKey.toArray)
      } else {
        PublicKeyFactory.createKey(parameters.publicKey.toArray)
      }
    }
    this.signer = new RSADigestSigner(DigestWrapper(MessageDigestModule(parameters.method.hashingMethod).messageDigest))
    signer.init(sign, cipherParameters)
  }

  def process(data: ByteString): Unit = {
    signer.update(data.toArray, 0, data.length)
  }

  def finishVerify(signature: ByteString): Boolean = {
    signer.verifySignature(signature.toArray)
  }

  def finishSign(): ByteString = {
    ByteString(signer.generateSignature())
  }

  def createParameters(): SignParameters = {
    val keyPair = keyPairGenerator.generateKeyPair()
    SignParameters(method,
      ByteString(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(keyPair.getPublic).getEncoded),
      ByteString(PrivateKeyInfoFactory.createPrivateKeyInfo(keyPair.getPrivate).getEncoded))
  }
}
