import com.karasiq.shadowcloud.index.{Chunk, File, Folder, IndexDiff}
import com.karasiq.shadowcloud.serialization.kryo.KryoSerializationModule
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class SerializeTest extends FlatSpec with Matchers {
  val kryo = new KryoSerializationModule

  "Kryo serializer" should "serialize chunk" in {
    val chunk = TestUtils.randomChunk
    val bytes = kryo.toBytes(chunk)
    kryo.fromBytes[Chunk](bytes) shouldBe chunk
  }

  it should "serialize file" in {
    val file = TestUtils.randomFile()
    val bytes = kryo.toBytes(file)
    kryo.fromBytes[File](bytes) shouldBe file
  }

  it should "serialize folder" in {
    val folder = TestUtils.randomFolder()
    val bytes = kryo.toBytes(folder)
    kryo.fromBytes[Folder](bytes) shouldBe folder
  }

  it should "serialize diff" in {
    val diff = TestUtils.randomDiff
    val bytes = kryo.toBytes(diff)
    kryo.fromBytes[IndexDiff](bytes) shouldBe diff
  }
}
