package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import java.security.NoSuchAlgorithmException

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.engines._
import org.bouncycastle.crypto.modes._
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher

import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCUtils

//noinspection SpellCheckingInspection
private[bouncycastle] object BCBlockCiphers {
  val algorithms = Set(
    "AES", "Blowfish", "Camellia", "CAST5", "CAST6", "DES", "DESede", "GOST28147", "IDEA", "Noekeon", "RC2", /* "RC532", "RC564", */
    "RC6", "Rijndael", "SEED", "Serpent", "Shacal2", "Skipjack", "TEA", "Twofish", "Threefish", "XTEA"
  )
  val blockModes = Set("CBC", "CFB", "OFB")
  val aeadModes = Set("GCM", "CCM", "EAX", "OCB")

  val blockAlgorithms = algorithms.flatMap(alg ⇒ blockModes.map(alg + "/" + _))
  val aeadAlgorithms = algorithms.flatMap(alg ⇒ aeadModes.map(alg + "/" + _))

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

  /**
    * @see [[org.bouncycastle.jce.provider.BouncyCastleProvider#SYMMETRIC_CIPHERS]]
    *     [[https://www.bouncycastle.org/specifications.html]]
    */
  def createBaseEngine(algorithm: String, blockSize: Option[Int] = None): BlockCipher = algorithm match {
    case "AES" ⇒
      new AESEngine // new AESFastEngine

    case "IDEA" ⇒
      new IDEAEngine

    case "Camellia" ⇒
      new CamelliaEngine

    case "Blowfish" ⇒
      new BlowfishEngine

    case "Twofish" ⇒
      new TwofishEngine

    case "XTEA" ⇒
      new XTEAEngine

    case "Rijndael" ⇒
      new RijndaelEngine(blockSize.getOrElse(getBlockSize("Rijndael")))

    case "SEED" ⇒
      new SEEDEngine

    case "Shacal2" ⇒
      new Shacal2Engine

    case "Serpent" ⇒
      new SerpentEngine

    case "Skipjack" ⇒
      new SkipjackEngine

    case "TEA" ⇒
      new TEAEngine

    case "Threefish" ⇒
      new ThreefishEngine(blockSize.getOrElse(ThreefishEngine.BLOCKSIZE_256))

    case "CAST5" ⇒
      new CAST5Engine

    case "CAST6" ⇒
      new CAST6Engine

    case "Noekeon" ⇒
      new NoekeonEngine

    case "RC2" ⇒
      new RC2Engine

    /* case "RC532" ⇒
      new RC532Engine

    case "RC564" ⇒
      new RC564Engine */

    case "RC6" ⇒
      new RC6Engine

    case "DESede" ⇒
      new DESedeEngine

    case "GOST28147" ⇒
      new GOST28147Engine

    case "DES" ⇒
      new DESEngine

    case _ ⇒
      throw new NoSuchAlgorithmException(algorithm)
  }

  def getBlockSize(algorithm: String): Int = {
    val (baseAlgorithm, _) = BCUtils.algorithmAndMode(algorithm)
    baseAlgorithm match {
      case "AES" | "Camellia" | "Twofish" | "Rijndael" | "SEED" | "Serpent" | "CAST6" | "Noekeon" | "RC564" | "RC6" ⇒
        128

      case "Threefish" | "Shacal2" ⇒
        256

      case "IDEA" | "Blowfish" | "XTEA" | "TEA" | "Skipjack" | "CAST5" | "RC2" | "RC532" | "DES" | "DESede" | "GOST28147" ⇒
        64

      case _ ⇒
        128 // throw new NoSuchAlgorithmException(algorithm)
    }
  }

  def getKeySize(algorithm: String): Int = {
    val (baseAlgorithm, _) = BCUtils.algorithmAndMode(algorithm)
    baseAlgorithm match {
      case "AES" | "Camellia" | "Threefish" | "Twofish" | "Rijndael" | "Serpent" | "CAST6"  | "RC6" | "GOST28147" ⇒
        256

      case "CAST5" | "RC532" | "RC564" | "XTEA" | "TEA" | "SEED" | "Skipjack" ⇒
        128 

      case "Shacal2" ⇒
        512

      case "DESede" ⇒
        192 

      case "DES" ⇒
        64

      case _ ⇒
        128 // throw new NoSuchAlgorithmException(algorithm)
    }
  }

  def getNonceSize(algorithm: String, blockSize: Option[Int] = None): Int = {
    val (baseAlgorithm, mode) = BCUtils.algorithmAndMode(algorithm)
    mode match {
      case m if aeadModes.contains(m) ⇒
        12

      case _ ⇒
        blockSize.getOrElse(getBlockSize(baseAlgorithm)) / 8
    }
  }

  def createBlockCipher(algorithm: String, blockSize: Option[Int] = None): BlockCipher = {
    def wrapEngine(baseCipher: BlockCipher, blockSize: Option[Int], mode: String): BlockCipher = mode match {
      case "CBC" ⇒
        new CBCBlockCipher(baseCipher)

      case "CFB" ⇒
        new CFBBlockCipher(baseCipher, blockSize.getOrElse(baseCipher.getBlockSize * 8))

      case "OFB" ⇒
        new OFBBlockCipher(baseCipher, blockSize.getOrElse(baseCipher.getBlockSize * 8))

      case _ ⇒
        throw new NoSuchAlgorithmException(s"No such encryption mode: $mode")
    }

    val (engineAlg, mode) = BCUtils.algorithmAndMode(algorithm)
    wrapEngine(createBaseEngine(engineAlg, blockSize), blockSize, mode)
  }

  def createAEADCipher(algorithm: String, blockSize: Option[Int] = None): AEADBlockCipher = {
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

    val (engineAlg, mode) = BCUtils.algorithmAndMode(algorithm)
    wrapEngine(() ⇒ createBaseEngine(engineAlg, blockSize), mode)
  }

  def buffered(bc: BlockCipher): PaddedBufferedBlockCipher = bc match {
    case bbc: PaddedBufferedBlockCipher ⇒
      bbc

    case _ ⇒
      new PaddedBufferedBlockCipher(bc)
  }
}
