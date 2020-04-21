package com.karasiq.shadowcloud.crypto.internal



import akka.util.ByteString
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.model.crypto.{SignMethod, SignParameters}

private[crypto] final class NoOpSignModule extends StreamSignModule {
  def method: SignMethod = SignMethod.none
  def createParameters(): SignParameters = SignParameters.empty
  def sign(data: ByteString, parameters: SignParameters) = ByteString.empty
  def verify(data: ByteString, signature: ByteString, parameters: SignParameters) = signature.isEmpty
  def createStreamer(): SignModuleStreamer = NoOpSignStreamer

  private[this] object NoOpSignStreamer extends SignModuleStreamer {
    def module: SignModule = NoOpSignModule.this
    def init(sign: Boolean, parameters: SignParameters): Unit = ()
    def update(data: ByteString): Unit = ()
    def finishVerify(signature: ByteString): Boolean = signature.isEmpty
    def finishSign(): ByteString = ByteString.empty
  }
}
