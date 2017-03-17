package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import scala.language.postfixOps

private[bouncycastle] object BCDigests {
  /** [[org.bouncycastle.jce.provider.BouncyCastleProvider.DIGESTS]] */
  val algorithms: Seq[String] = Seq(
    "GOST3411", "Keccak", "MD2", "MD4", "MD5", "SHA1", "RIPEMD128", "RIPEMD160", "RIPEMD256", "RIPEMD320", "SHA224",
    "SHA256", "SHA384", "SHA512", "SHA3", "Skein", "SM3", "Tiger", "Whirlpool", "Blake2b"
  )

  private[this] val DIGESTS_WITH_SIZE: Set[String] = Set(
    "Keccak", "SHA3", "Blake2b"
  )

  private[this] val DIGESTS_WITH_TWO_SIZES: Set[String] = Set(
    "Skein"
  )

  def hasSize(alg: String): Boolean = {
    DIGESTS_WITH_SIZE.contains(alg)
  }

  def hasTwoSizes(alg: String): Boolean = {
    DIGESTS_WITH_TWO_SIZES.contains(alg)
  }
}
