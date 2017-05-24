package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.math.BigInteger
import java.security.SecureRandom

import akka.util.ByteString
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.generators.{ECKeyPairGenerator, RSAKeyPairGenerator}
import org.bouncycastle.crypto.params.{AsymmetricKeyParameter, ECKeyGenerationParameters, RSAKeyGenerationParameters}
import org.bouncycastle.crypto.util.{PrivateKeyFactory, PrivateKeyInfoFactory, PublicKeyFactory, SubjectPublicKeyInfoFactory}

import com.karasiq.shadowcloud.crypto.CryptoMethod

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

  def ecKeyGenerator(method: CryptoMethod): AsymmetricCipherKeyPairGenerator = {
    val generator = new ECKeyPairGenerator
    generator.init(new ECKeyGenerationParameters(ECUtils.getCurveDomainParameters(method), new SecureRandom()))
    generator
  }
}
