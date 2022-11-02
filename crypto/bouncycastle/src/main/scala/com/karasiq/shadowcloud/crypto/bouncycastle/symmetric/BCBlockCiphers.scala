package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import java.security.NoSuchAlgorithmException

import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.engines._
import org.bouncycastle.crypto.modes._
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher

import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCUtils

//noinspection SpellCheckingInspection
private[bouncycastle] object BCBlockCiphers {
  private[this] sealed trait BlockCipherSpec {
    def algorithm: String
    def createInstance(): BlockCipher
    def createInstanceWithBlockSize(blockSize: Int): BlockCipher
    def keySize: Int
    def blockSize: Int
  }

  private[this] val blockCipherSpecs = {
    def baseCipher(_algorithm: String, _createInstance: ⇒ BlockCipher, _keySize: Int, _blockSize: Int): BlockCipherSpec = new BlockCipherSpec {
      def algorithm: String                                        = _algorithm
      def createInstance(): BlockCipher                            = _createInstance
      def createInstanceWithBlockSize(blockSize: Int): BlockCipher = throw new NotImplementedError()
      def keySize: Int                                             = _keySize
      def blockSize: Int                                           = _blockSize
    }

    def tweakableCipher(_algorithm: String, _createInstance: Int ⇒ BlockCipher, _keySize: Int, _blockSize: Int): BlockCipherSpec =
      new BlockCipherSpec {
        def algorithm: String                                        = _algorithm
        def createInstance(): BlockCipher                            = createInstanceWithBlockSize(this.blockSize)
        def createInstanceWithBlockSize(blockSize: Int): BlockCipher = _createInstance(blockSize)
        def keySize: Int                                             = _keySize
        def blockSize: Int                                           = _blockSize
      }

    // https://www.bouncycastle.org/specifications.html
    // (\w+)Engine\t(?:[0 ,..]+)?([\d]+)(?:[\d ,..]+)?\t(\d+)(?:.*) => baseCipher("$1", new $1Engine, $2, $3),
    // 256 bit keys is preferred
    val specs = Seq(
      baseCipher("AES", new AESEngine, 256, 128),
      // baseCipher("AESWrap", new AESWrapEngine, 256, 128),
      baseCipher("Blowfish", new BlowfishEngine, 256, 64),
      baseCipher("Camellia", new CamelliaEngine, 256, 128),
      // baseCipher("CamelliaWrap", new CamelliaWrapEngine, 128, 128),
      baseCipher("CAST5", new CAST5Engine, 128, 64),
      baseCipher("CAST6", new CAST6Engine, 256, 128),
      baseCipher("DES", new DESEngine, 64, 64),
      baseCipher("DESede", new DESedeEngine, 192, 64),
      // baseCipher("DESedeWrap", new DESedeWrapEngine, 128, 64),
      baseCipher("GOST28147", new GOST28147Engine, 256, 64),
      baseCipher("IDEA", new IDEAEngine, 128, 64),
      baseCipher("Noekeon", new NoekeonEngine, 128, 128),
      baseCipher("RC2", new RC2Engine, 256, 64),
      // baseCipher("RC532", new RC532Engine, 128, 64),
      // baseCipher("RC564", new RC564Engine, 128, 128),
      baseCipher("RC6", new RC6Engine, 256, 128),
      tweakableCipher("Rijndael", new RijndaelEngine(_), 256, 128),
      baseCipher("SEED", new SEEDEngine, 128, 128),
      // baseCipher("SEEDWrap", new SEEDWrapEngine, 128, 128),
      baseCipher("Shacal2", new Shacal2Engine, 512, 256),
      baseCipher("Serpent", new SerpentEngine, 256, 128),
      baseCipher("Skipjack", new SkipjackEngine, 128, 64),
      baseCipher("TEA", new TEAEngine, 128, 64),
      tweakableCipher("Threefish", new ThreefishEngine(_), 256, 256),
      baseCipher("Twofish", new TwofishEngine, 256, 128),
      baseCipher("XTEA", new XTEAEngine, 128, 64)
    )

    specs.map(spec ⇒ (spec.algorithm, spec)).toMap
  }

  val algorithms: Set[String] = blockCipherSpecs.keySet
  val blockModes: Set[String] = Set("CBC", "CFB", "OFB", "CTR")
  val aeadModes: Set[String]  = Set("GCM", "CCM", "EAX", "OCB")

  val blockAlgorithms: Set[String] =
    for (alg ← algorithms; mode ← blockModes)
      yield s"$alg/$mode"

  val aeadAlgorithms: Set[String] =
    for (alg ← algorithms if getBlockSize(alg) == 128; mode ← aeadModes)
      yield s"$alg/$mode"

  def isBlockAlgorithm(algorithm: String): Boolean = {
    blockAlgorithms.contains(algorithm)
  }

  def isAEADAlgorithm(algorithm: String): Boolean = {
    aeadAlgorithms.contains(algorithm)
  }

  /** @see [[org.bouncycastle.jce.provider.BouncyCastleProvider#SYMMETRIC_CIPHERS]]
    *     [[https://www.bouncycastle.org/specifications.html]]
    */
  def createBaseEngine(algorithm: String, blockSize: Option[Int] = None): BlockCipher = {
    blockCipherSpecs.get(algorithm) match {
      case Some(spec) ⇒
        blockSize.fold(spec.createInstance())(bs ⇒ spec.createInstanceWithBlockSize(bs))

      case None ⇒
        throw new NoSuchAlgorithmException(algorithm)
    }
  }

  def getBlockSize(algorithm: String): Int = {
    val (baseAlgorithm, _) = BCUtils.algorithmAndMode(algorithm)
    blockCipherSpecs.get(baseAlgorithm) match {
      case Some(spec) ⇒
        spec.blockSize

      case None ⇒
        throw new NoSuchAlgorithmException(algorithm)
    }
  }

  def getKeySize(algorithm: String): Int = {
    val (baseAlgorithm, _) = BCUtils.algorithmAndMode(algorithm)
    blockCipherSpecs.get(baseAlgorithm) match {
      case Some(spec) ⇒
        spec.keySize

      case None ⇒
        throw new NoSuchAlgorithmException(algorithm)
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

  def getAeadMacSize(algorithm: String, blockSize: Option[Int] = None): Int = {
    val (baseAlgorithm, mode) = BCUtils.algorithmAndMode(algorithm)
    val cipherBlockBits       = blockSize.getOrElse(getBlockSize(baseAlgorithm))
    mode match {
      case "CCM" | "EAX" ⇒
        cipherBlockBits / 2

      case _ ⇒
        cipherBlockBits
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

      case "CTR" | "SIC" ⇒
        new SICBlockCipher(baseCipher)

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

  def toPaddedBufferedBlockCipher(bc: BlockCipher): PaddedBufferedBlockCipher = bc match {
    case bbc: PaddedBufferedBlockCipher ⇒
      bbc

    case _ ⇒
      new PaddedBufferedBlockCipher(bc)
  }
}
