package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import java.security.MessageDigest

import scala.language.postfixOps

import org.bouncycastle.crypto.Digest

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCUtils
import com.karasiq.shadowcloud.model.crypto.HashingMethod

private[bouncycastle] object BCDigests extends ConfigImplicits {
  /** [[org.bouncycastle.jce.provider.BouncyCastleProvider.DIGESTS]] */
  val algorithms: Set[String] = Set(
    "GOST3411", "Keccak", "MD2", "MD4", "MD5", "SHA1", "RIPEMD128", "RIPEMD160", "RIPEMD256", "RIPEMD320", "SHA224",
    "SHA256", "SHA384", "SHA512", "SHA3", "Skein", "SM3", "Tiger", "Whirlpool", "Blake2b"
  )

  private[this] val digestsWithSize: Set[String] = Set(
    "Keccak", "SHA3", "Blake2b"
  )

  private[this] val digestsWithTwoSizes: Set[String] = Set(
    "Skein"
  )

  def isDigestAlgorithm(algorithm: String): Boolean = {
    algorithms.contains(algorithm)
  }

  def createDigest(method: HashingMethod): Digest = {
    method.algorithm match {
      case "Blake2b" ⇒
        Blake2b.createDigest(method)

      case _ ⇒ // Generic
        BCDigestWrapper(createMessageDigest(method))
    }
  }

  private[this] def hasSize(algorithm: String): Boolean = {
    digestsWithSize.contains(algorithm)
  }

  private[this] def hasTwoSizes(algorithm: String): Boolean = {
    digestsWithTwoSizes.contains(algorithm)
  }

  private[this] def createMessageDigest(method: HashingMethod): MessageDigest = {
    if (hasTwoSizes(method.algorithm)) {
      createMDWithTwoSizes(method.algorithm, method)
    } else if (hasSize(method.algorithm)) {
      createMDWithSize(method.algorithm, method)
    } else {
      getMDInstance(method.algorithm)
    }
  }

  private[this] def createMDWithSize(algorithm: String, method: HashingMethod, defaultSize: Int = 256): MessageDigest = {
    val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(defaultSize, _.getInt("digest-size"))
    getMDInstance(s"$algorithm-$digestSize")
  }

  private[this] def createMDWithTwoSizes(alg: String, method: HashingMethod, defaultStateSize: Int = 256): MessageDigest = {
    val config = ConfigProps.toConfig(method.config)
    val stateSize = config.withDefault(defaultStateSize, _.getInt("state-size"))
    val digestSize = config.withDefault(stateSize, _.getInt("digest-size"))
    getMDInstance(s"$alg-$stateSize-$digestSize")
  }

  private[this] def getMDInstance(algorithm: String): MessageDigest = {
    MessageDigest.getInstance(algorithm, BCUtils.provider)
  }
}
