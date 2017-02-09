import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionParameters, HashingMethod}
import com.karasiq.shadowcloud.index.{IndexDiff, _}
import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps
import scala.util.Random

object TestUtils {
  implicit class ByteStringOps(private val bs: ByteString) {
    def toHexString: String = Hex.encodeHexString(bs.toArray)
  }

  implicit class ByteStringObjOps(private val bs: ByteString.type) {
    def fromHexString(hexString: String): ByteString = {
      ByteString(Hex.decodeHex(hexString.toCharArray))
    }
  }

  def randomBytes(length: Int): ByteString = {
    val array = Array.ofDim[Byte](length)
    Random.nextBytes(array)
    ByteString(array)
  }

  def indexedBytes: (ByteString, File) = {
    val text = ByteString("""You may have noticed various code patterns that emerge when testing stream pipelines. Akka Stream has a separate akka-stream-testkit module that provides tools specifically for writing stream tests. This module comes with two main components that are TestSource and TestSink which provide sources and sinks that materialize to probes that allow fluent API.""")
    val hashingMethod = HashingMethod("SHA1")
    val textHash = ByteString.fromHexString("2f5a0c419cfeb92f05888ae3468e54fee3ee1726")
    val preCalcHashes = Vector("f660847d03634f41c45f7be337b02973a083721a", "dfa6cbe4eb725d390e3339075fe420791a5a394f", "e63bf72054623e911ce6a995dc520527d7fe2e2d", "802f6e7f54ca13c650741e65f188b0bdb023cb15").map(ByteString.fromHexString)

    val chunks = Seq(
      Chunk(Checksum(hashingMethod, 100, preCalcHashes(0), 100, preCalcHashes(0)), EncryptionParameters.empty, Data(text.slice(0, 100), text.slice(0, 100))),
      Chunk(Checksum(hashingMethod, 100, preCalcHashes(1), 100, preCalcHashes(1)), EncryptionParameters.empty, Data(text.slice(100, 200), text.slice(100, 200))),
      Chunk(Checksum(hashingMethod, 100, preCalcHashes(2), 100, preCalcHashes(2)), EncryptionParameters.empty, Data(text.slice(200, 300), text.slice(200, 300))),
      Chunk(Checksum(hashingMethod, 56, preCalcHashes(3), 56, preCalcHashes(3)), EncryptionParameters.empty, Data(text.slice(300, 356), text.slice(300, 356)))
    )
    (text, File(Path.root, "test.txt", System.currentTimeMillis(), System.currentTimeMillis(), Checksum(hashingMethod, 356, textHash, 356, textHash), chunks))
  }

  def testChunk: Chunk = {
    indexedBytes._2.chunks.head
  }

  def testDiff: IndexDiff = {
    val (_, file) = indexedBytes
    IndexDiff(System.currentTimeMillis(), Seq(FolderDiff(Path.root, newFiles = Set(file))), ChunkIndexDiff(file.chunks.toSet))
  }
}
