package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.math.BigInteger
import java.security.SecureRandom

import akka.util.ByteString
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.{AsymmetricKeyParameter, RSAKeyGenerationParameters}
import org.bouncycastle.crypto.util.{PrivateKeyFactory, PrivateKeyInfoFactory, PublicKeyFactory, SubjectPublicKeyInfoFactory}

private[bouncycastle] object KeyUtils {
  def encodePublicKey(key: AsymmetricKeyParameter): ByteString = {
    ByteString(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(key).getEncoded)
  }

  def encodePrivateKey(key: AsymmetricKeyParameter): ByteString = {
    ByteString(PrivateKeyInfoFactory.createPrivateKeyInfo(key).getEncoded)
  }

  def decodePublicKey(bytes: ByteString): AsymmetricKeyParameter = {
    PublicKeyFactory.createKey(bytes.toArray)
  }

  def decodePrivateKey(bytes: ByteString): AsymmetricKeyParameter = {
    PrivateKeyFactory.createKey(bytes.toArray)
  }

  def rsaKeyGenerator(keySize: Int): AsymmetricCipherKeyPairGenerator = {
    val generator = new RSAKeyPairGenerator
    generator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(65537), new SecureRandom(), keySize, 12))
    generator
  }
}
