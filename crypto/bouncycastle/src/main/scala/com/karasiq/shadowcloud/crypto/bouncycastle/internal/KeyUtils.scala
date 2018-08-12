package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import akka.util.ByteString
import org.bouncycastle.crypto.params.AsymmetricKeyParameter
import org.bouncycastle.crypto.util.{PrivateKeyFactory, PrivateKeyInfoFactory, PublicKeyFactory, SubjectPublicKeyInfoFactory}

import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._

private[bouncycastle] object KeyUtils {
  def encodePublicKey(key: AsymmetricKeyParameter): ByteString = {
    ByteString.fromArrayUnsafe(SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(key).getEncoded)
  }

  def encodePrivateKey(key: AsymmetricKeyParameter): ByteString = {
    ByteString.fromArrayUnsafe(PrivateKeyInfoFactory.createPrivateKeyInfo(key).getEncoded)
  }

  def decodePublicKey(bytes: ByteString): AsymmetricKeyParameter = {
    PublicKeyFactory.createKey(bytes.toArrayUnsafe)
  }

  def decodePrivateKey(bytes: ByteString): AsymmetricKeyParameter = {
    PrivateKeyFactory.createKey(bytes.toArrayUnsafe)
  }
}
