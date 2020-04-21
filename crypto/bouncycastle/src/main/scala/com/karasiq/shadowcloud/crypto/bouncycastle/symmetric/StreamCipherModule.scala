package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric



import akka.util.ByteString
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCSymmetricKeys, BCUtils}
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters}
import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
import org.bouncycastle.crypto.StreamCipher

//noinspection RedundantDefaultArgument
private[bouncycastle] object StreamCipherModule {
  def apply(method: EncryptionMethod): StreamCipherModule = {
    new StreamCipherModule(method)
  }

  def Salsa20(): StreamCipherModule = {
    apply(EncryptionMethod("Salsa20", 256))
  }

  def XSalsa20(): StreamCipherModule = {
    apply(EncryptionMethod("XSalsa20", 256))
  }

  def ChaCha20(): StreamCipherModule = {
    apply(EncryptionMethod("ChaCha20", 256))
  }
}

private[bouncycastle] final class StreamCipherModule(val method: EncryptionMethod)
  extends OnlyStreamEncryptionModule with BCSymmetricKeys {

  protected val nonceSize: Int = BCStreamCiphers.getNonceSize(method.algorithm)

  def createStreamer(): EncryptionModuleStreamer = {
    new StreamCipherStreamer
  }

  protected class StreamCipherStreamer extends EncryptionModuleStreamer {
    private[this] var cipher: StreamCipher = _

    def module: EncryptionModule = {
      StreamCipherModule.this
    }

    def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
      cipher = BCStreamCiphers.createStreamCipher(parameters.method.algorithm)
      cipher.init(encrypt, BCUtils.toParametersWithIV(parameters))
    }

    def process(data: ByteString): ByteString = {
      require(cipher ne null, "Not initialized")
      val outArray = new Array[Byte](data.length)
      val outLength = cipher.processBytes(data.toArrayUnsafe, 0, data.length, outArray, 0)
      if (outArray.length == outLength) ByteString.fromArrayUnsafe(outArray)
      else ByteString.fromArray(outArray, 0, outLength)
    }

    def finish(): ByteString = {
      ByteString.empty
    }
  }
}
