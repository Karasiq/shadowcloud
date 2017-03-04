package com.karasiq.shadowcloud.crypto.bouncycastle

import java.security.MessageDigest

import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{AEADBlockCipherEncryptionModule, BCUtils, MessageDigestHashingModule}
import com.karasiq.shadowcloud.providers.ModuleProvider
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher

import scala.language.postfixOps

final class BouncyCastleCryptoProvider extends ModuleProvider {
  /** [[org.bouncycastle.jce.provider.BouncyCastleProvider.DIGESTS]] */
  private[this] val DIGESTS = Set("GOST3411", "Keccak", "MD2", "MD4", "MD5", "SHA1", "RIPEMD128",
    "RIPEMD160", "RIPEMD256", "RIPEMD320", "SHA224", "SHA256", "SHA384", "SHA512", "SHA3", "Skein",
    "SM3", "Tiger", "Whirlpool", "Blake2b")

  override val name = "bouncy-castle"

  override def hashing: HashingPF = {
    case method if DIGESTS.contains(method.algorithm) ⇒
      val messageDigest = MessageDigest.getInstance(method.algorithm, BCUtils.provider)
      new MessageDigestHashingModule(method, messageDigest)
  }

  override def encryption: EncryptionPF = {
    case method @ EncryptionMethod("AES" | "AES/GCM", 128 | 256, _, _) ⇒
      new AEADBlockCipherEncryptionModule(new GCMBlockCipher(new AESEngine), "AES", method)
  }
}
