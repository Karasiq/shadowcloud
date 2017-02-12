import akka.util.ByteString
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class HashCodesTest extends FlatSpec with Matchers {
  "Chunks" should "be compared" in {
    val chunk1 = TestUtils.randomChunk
    val chunk2 = chunk1.withoutData
    val chunk3 = chunk2.copy(checksum = chunk2.checksum.copy(encryptedSize = 0, encryptedHash = ByteString.empty))
    val chunk4 = chunk2.copy(checksum = chunk2.checksum.copy(encryptedSize = 0, encryptedHash = TestUtils.randomBytes(20)))
    val chunk5 = chunk2.copy(checksum = chunk2.checksum.copy(hash = TestUtils.randomBytes(20)))
    val chunk6 = chunk2.copy(checksum = chunk2.checksum.copy(size = chunk2.checksum.size + 1))
    chunk1 shouldBe chunk2
    chunk1 shouldBe chunk3
    chunk1 shouldNot be (chunk4)
    chunk1 shouldNot be (chunk5)
    chunk1 shouldNot be (chunk6)
    chunk1.hashCode() shouldBe chunk2.hashCode()
    chunk1.hashCode() shouldBe chunk3.hashCode()
    chunk1.hashCode() shouldBe chunk4.hashCode()
    chunk1.hashCode() shouldNot be (chunk5.hashCode())
    chunk1.hashCode() shouldNot be (chunk6.hashCode())
  }

  "Files" should "be compared" in {
    val file1 = TestUtils.randomFile()
    val file2 = file1.copy(checksum = file1.checksum.copy(encryptedSize = 0, encryptedHash = ByteString.empty))
    val file3 = file1.copy(checksum = file1.checksum.copy(encryptedSize = 0, encryptedHash = TestUtils.randomBytes(20)))
    val file4 = file1.copy(checksum = file1.checksum.copy(hash = TestUtils.randomBytes(20)))
    val file5 = file1.copy(checksum = file1.checksum.copy(size = file1.checksum.size + 1))
    val file6 = file1.copy(chunks = file1.chunks :+ TestUtils.randomChunk)
    file1 shouldBe file2
    file1 shouldNot be (file3)
    file1 shouldNot be (file4)
    file1 shouldNot be (file5)
    file1.hashCode() shouldBe file2.hashCode()
    file1.hashCode() shouldBe file3.hashCode()
    file1.hashCode() shouldNot be (file5.hashCode())
    file1.hashCode() shouldNot be (file6.hashCode())
  }
}
