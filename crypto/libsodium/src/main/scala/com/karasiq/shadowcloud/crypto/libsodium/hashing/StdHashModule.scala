package com.karasiq.shadowcloud.crypto.libsodium.hashing

import akka.util.ByteString
import org.abstractj.kalium.crypto.Hash

import com.karasiq.shadowcloud.crypto.HashingModule
import com.karasiq.shadowcloud.model.crypto.HashingMethod
import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._

private[libsodium] object StdHashModule {
  private[this] val Algorithms = Set("sha256", "sha512", "blake2b")

  def apply(method: HashingMethod): StdHashModule = {
    val hash = new Hash()
    val function = unifyAlgorithmName(method.algorithm) match {
      case "sha256" ⇒ hash.sha256(_)
      case "sha512" ⇒ hash.sha512(_)
      case "blake2b" ⇒ hash.blake2(_)
    }
    new StdHashModule(method, function)
  }

  def isAlgorithmSupported(alg: String): Boolean = {
    val alg1 = unifyAlgorithmName(alg)
    Algorithms.contains(alg1)
  }

  private[this] def unifyAlgorithmName(alg: String): String = {
    alg.replaceAllLiterally("-", "").toLowerCase
  }
}

private[libsodium] final class StdHashModule(val method: HashingMethod,
                                             hashFunction: Array[Byte] ⇒ Array[Byte])
  extends HashingModule {

  def createHash(data: ByteString): ByteString = {
    val outArray = hashFunction(data.toArrayUnsafe)
    ByteString.fromArrayUnsafe(outArray)
  }
}
