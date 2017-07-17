package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import akka.util.ByteString
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
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
}
