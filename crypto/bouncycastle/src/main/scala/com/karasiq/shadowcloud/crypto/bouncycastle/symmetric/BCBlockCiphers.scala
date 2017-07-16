package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import java.security.NoSuchAlgorithmException

import org.bouncycastle.crypto.{BlockCipher, BufferedBlockCipher}
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes._
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher

private[bouncycastle] object BCBlockCiphers {
  val algorithms = Set("AES")
  val blockModes = Set("CBC", "CFB", "OFB")
  val aeadModes = Set("GCM", "CCM", "EAX", "OCB")

  private[this] def testAlgorithm(algorithm: String, algorithms: Set[String], modes: Set[String]): Boolean = {
    algorithm.split("/", 2) match {
      case Array(alg, mode) ⇒
        algorithms.contains(alg) && modes.contains(mode)

      case _ ⇒
        false
    }
  }

  def isBlockAlgorithm(algorithm: String): Boolean = {
    testAlgorithm(algorithm, algorithms, blockModes)
  }

  def isAEADAlgorithm(algorithm: String): Boolean = {
    testAlgorithm(algorithm, algorithms, aeadModes)
  }

  def createAesEngine(): AESEngine = {
    new AESEngine
  }

  def createBlockCipher(algorithm: String, blockSize: Int = 256): BlockCipher = {
    def wrapEngine(baseCipher: BlockCipher, blockSize: Int, mode: String): BlockCipher = mode match {
      case "CBC" ⇒
        new CBCBlockCipher(baseCipher)

      case "CFB" ⇒
        new CFBBlockCipher(baseCipher, blockSize)

      case "OFB" ⇒
        new OFBBlockCipher(baseCipher, blockSize)

      case _ ⇒
        throw new NoSuchAlgorithmException(s"No such encryption mode: $mode")
    }

    algorithm.split("/", 2) match {
      case Array("AES", mode) ⇒
        wrapEngine(createAesEngine(), blockSize, mode)

      case _ ⇒
        throw new NoSuchAlgorithmException(algorithm)
    }
  }

  def createAEADCipher(algorithm: String): AEADBlockCipher = {
    def wrapEngine(createBaseCipher: () ⇒ BlockCipher, mode: String): AEADBlockCipher = mode match {
      case "GCM" ⇒
        new GCMBlockCipher(createBaseCipher())

      case "CCM" ⇒
        new CCMBlockCipher(createBaseCipher())

      case "EAX" ⇒
        new EAXBlockCipher(createBaseCipher())

      case "OCB" ⇒
        new OCBBlockCipher(createBaseCipher(), createBaseCipher())

      case _ ⇒
        throw new NoSuchAlgorithmException(s"No such encryption mode: $mode")
    }

    algorithm.split("/", 2) match {
      case Array("AES", mode) ⇒
        wrapEngine(createAesEngine, mode)

      case _ ⇒
        throw new NoSuchAlgorithmException(algorithm)
    }
  }

  def buffered(bc: BlockCipher): BufferedBlockCipher = bc match {
    case bbc: BufferedBlockCipher ⇒
      bbc

    case _ ⇒
      new PaddedBufferedBlockCipher(bc)
  }
}
