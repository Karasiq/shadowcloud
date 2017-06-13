package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import java.security.MessageDigest

import scala.language.postfixOps

import org.bouncycastle.crypto.Digest

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCUtils

private[bouncycastle] object BCDigests extends ConfigImplicits {
  /** [[org.bouncycastle.jce.provider.BouncyCastleProvider.DIGESTS]] */
  val algorithms: Seq[String] = Seq(
    "GOST3411", "Keccak", "MD2", "MD4", "MD5", "SHA1", "RIPEMD128", "RIPEMD160", "RIPEMD256", "RIPEMD320", "SHA224",
    "SHA256", "SHA384", "SHA512", "SHA3", "Skein", "SM3", "Tiger", "Whirlpool", "Blake2b"
  )

  private[this] val digestsWithSize: Set[String] = Set(
    "Keccak", "SHA3", "Blake2b"
  )

  private[this] val digestsWithTwoSizes: Set[String] = Set(
    "Skein"
  )

  def createDigest(method: HashingMethod): Digest = {
    BCDigestWrapper(createMessageDigest(method))
  }

  def createMessageDigest(method: HashingMethod): MessageDigest = {
    if (BCDigests.hasTwoSizes(method.algorithm)) {
      createMDWithTwoSizes(method.algorithm, method)
    } else if (BCDigests.hasSize(method.algorithm)) {
      createMDWithSize(method.algorithm, method)
    } else {
      getMDInstance(method.algorithm)
    }
  }

  def hasSize(algorithm: String): Boolean = {
    digestsWithSize.contains(algorithm)
  }

  def hasTwoSizes(algorithm: String): Boolean = {
    digestsWithTwoSizes.contains(algorithm)
  }

  def createMDWithSize(algorithm: String, method: HashingMethod, defaultSize: Int = 256): MessageDigest = {
    val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(defaultSize, _.getInt("digest-size"))
    getMDInstance(s"$algorithm-$digestSize")
  }

  private[this] def getMDInstance(algorithm: String): MessageDigest = {
    MessageDigest.getInstance(algorithm, BCUtils.provider)
  }

  def createMDWithTwoSizes(alg: String, method: HashingMethod, defaultStateSize: Int = 256): MessageDigest = {
    val config = ConfigProps.toConfig(method.config)
    val stateSize = config.withDefault(defaultStateSize, _.getInt("state-size"))
    val digestSize = config.withDefault(stateSize, _.getInt("digest-size"))
    getMDInstance(s"$alg-$stateSize-$digestSize")
  }

  def createDigest(algorithm: String): Digest = {
    BCDigestWrapper(createMessageDigest(algorithm))
  }

  def createMessageDigest(algorithm: String): MessageDigest = {
    createMessageDigest(HashingMethod(algorithm))
  }
}
