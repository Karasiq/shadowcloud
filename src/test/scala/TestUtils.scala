import akka.util.ByteString
import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps
import scala.util.Random

object TestUtils {
  implicit class ByteStringOps(private val bs: ByteString) {
    def toHexString: String = Hex.encodeHexString(bs.toArray)
  }

  def toByteString(hexString: String): ByteString = {
    ByteString(Hex.decodeHex(hexString.toCharArray))
  }

  def randomBytes(length: Int): ByteString = {
    val array = Array.ofDim[Byte](length)
    Random.nextBytes(array)
    ByteString(array)
  }
}
