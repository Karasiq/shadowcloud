package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.language.postfixOps

private[bouncycastle] object BCUtils {
  val provider = new BouncyCastleProvider

  /** [[org.bouncycastle.jce.provider.BouncyCastleProvider.DIGESTS]] */
  val DIGESTS: Seq[String] = Seq(
    "GOST3411", /* "Keccak", */ "MD2", "MD4", "MD5", "SHA1", "RIPEMD128", "RIPEMD160", "RIPEMD256", "RIPEMD320", "SHA224",
    "SHA256", "SHA384", "SHA512", /* "SHA3", "Skein", */ "SM3", "Tiger", "Whirlpool"/*, "Blake2b" */
  )
}
