package com.karasiq.shadowcloud.crypto.bouncycastle

import java.security.MessageDigest

import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{AEADBlockCipherEncryptionModule, BCUtils, MessageDigestHashingModule}
import com.karasiq.shadowcloud.providers.CryptoProvider
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher

import scala.language.postfixOps

final class BouncyCastleCryptoProvider extends CryptoProvider {
  /** [[org.bouncycastle.jce.provider.BouncyCastleProvider.DIGESTS]] */
  override val hashingAlgorithms: Set[String] = Set("GOST3411", "Keccak", "MD2", "MD4", "MD5", "SHA1", "RIPEMD128",
    "RIPEMD160", "RIPEMD256", "RIPEMD320", "SHA224", "SHA256", "SHA384", "SHA512", "SHA3", "Skein",
    "SM3", "Tiger", "Whirlpool", "Blake2b")

  override def hashing: HashingPF = {
    case method if hashingAlgorithms.contains(method.algorithm) ⇒
      val messageDigest = MessageDigest.getInstance(method.algorithm, BCUtils.provider)
      new MessageDigestHashingModule(method, messageDigest)
  }

  override val encryptionAlgorithms: Set[String] = {
    Set("AES", "AES/GCM")
  }

  // TODO: AESFastEngine, Salsa20
  override def encryption: EncryptionPF = {
    case method @ EncryptionMethod("AES" | "AES/GCM", 128 | 256, _, _, _) ⇒
      new AEADBlockCipherEncryptionModule(new GCMBlockCipher(new AESEngine), "AES", method)
  }
}
