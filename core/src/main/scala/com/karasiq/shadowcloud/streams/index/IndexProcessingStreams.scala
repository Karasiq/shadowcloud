package com.karasiq.shadowcloud.streams.index

import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.NotUsed
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.{Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.compression.StreamCompression
import com.karasiq.shadowcloud.config.{CryptoConfig, StorageConfig}
import com.karasiq.shadowcloud.crypto.index.IndexEncryption
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.model.keys.KeyChain
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.serialization.StreamSerialization
import com.karasiq.shadowcloud.serialization.protobuf.index.{EncryptedIndexData, SerializedIndexData}
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

object IndexProcessingStreams {
  def apply(sc: ShadowCloudExtension): IndexProcessingStreams = {
    new IndexProcessingStreams(sc)
  }
}

final class IndexProcessingStreams(sc: ShadowCloudExtension) {
  // -----------------------------------------------------------------------
  // Flows
  // -----------------------------------------------------------------------
  def preWrite(storageConfig: StorageConfig): Flow[IndexData, ByteString, NotUsed] = {
    Flow[IndexData]
      .via(internalStreams.serialize)
      .via(internalStreams.compress)
      .via(internalStreams.encrypt)
      .log("index-frames-write")
      .via(internalStreams.writeEncrypted)
      .named("indexPreWrite")
  }

  val postRead: Flow[ByteString, IndexData, NotUsed] = Flow[ByteString]
    .via(internalStreams.readEncrypted)
    .log("index-frames-read-encrypted")
    .via(internalStreams.decrypt)
    .via(internalStreams.decompress)
    .via(internalStreams.deserialize)
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .log("index-frames-read")
    .named("indexPostRead")

  private[this] object internalStreams {
    private[this] val keyChainSource = Source.single(NotUsed)
      .flatMapConcat(_ ⇒ Source.fromFuture(sc.keys.provider.getKeyChain()))
      .named("keyChainSource")

    val serialize = Flow[IndexData].map { data ⇒
      ByteString(SerializedIndexData(sc.serialization.toBytes(data)).toByteArray)
    }

    val deserialize = Flow[ByteString].map { data ⇒
      val sd = SerializedIndexData.parseFrom(data.toArray)
      sc.serialization.fromBytes[IndexData](sd.data)
    }

    val encrypt = Flow[ByteString]
      .via(AkkaStreamUtils.extractUpstream)
      .zip(keyChainSource)
      .flatMapConcat { case (source, keyChain) ⇒
        source.via(new IndexEncryptStage(sc.modules.crypto, sc.config.crypto, keyChain))
      }

    val decrypt = Flow[EncryptedIndexData]
      .via(AkkaStreamUtils.extractUpstream)
      .zip(keyChainSource)
      .flatMapConcat { case (source, keyChain) ⇒
        source.via(new IndexDecryptStage(sc.modules.crypto, sc.config.crypto, keyChain))
      }

    private[this] val framedCompress = Flow[ByteString]
      .via(StreamSerialization.frame(sc.config.serialization.frameLimit))
      .via(StreamCompression.compress(sc.config.serialization.compression))

    private[this] val framedDecompress = Flow[ByteString]
      .via(StreamCompression.decompress)
      .via(StreamSerialization.deframe(sc.config.serialization.frameLimit))

    val compress = Flow[ByteString].flatMapConcat { bytes ⇒
      Source.single(bytes)
        .via(framedCompress)
        .via(ByteStreams.concat)
    }

    val decompress = Flow[ByteString].flatMapConcat { compBytes ⇒
      Source.single(compBytes)
        .via(framedDecompress)
        .via(ByteStreams.concat)
    }

    val writeEncrypted = Flow[EncryptedIndexData]
      .via(StreamSerialization.serializeFramed[EncryptedIndexData](sc.serialization, sc.config.serialization.frameLimit))

    val readEncrypted = Flow[ByteString]
      .via(StreamSerialization.deserializeFramed[EncryptedIndexData](sc.serialization, sc.config.serialization.frameLimit))
  }

  // -----------------------------------------------------------------------
  // Encryption stages
  // -----------------------------------------------------------------------
  private final class IndexEncryptStage(cryptoModules: CryptoModuleRegistry, config: CryptoConfig, keyChain: KeyChain)
    extends GraphStage[FlowShape[ByteString, EncryptedIndexData]] {

    val inlet = Inlet[ByteString]("IndexEncrypt.in")
    val outlet = Outlet[EncryptedIndexData]("IndexEncrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val log = Logging(sc.implicits.actorSystem, "IndexEncryptStage")
      private[this] val encryption = IndexEncryption(cryptoModules, sc.serialization)

      private[this] def encryptData(data: ByteString): Unit = {
        require(keyChain.encKeys.nonEmpty, "No keys available")
        log.debug("Encrypting index frame with keys: {}", keyChain.encKeys)
        val result = encryption.encrypt(data, config.encryption.index, keyChain)
        push(outlet, result)
      }

      def onPull(): Unit = {
        if (isClosed(inlet)) {
          complete(outlet)
        } else {
          pull(inlet)
        }
      }

      def onPush(): Unit = {
        val data = grab(inlet)
        encryptData(data)
      }

      setHandlers(inlet, outlet, this)
    }
  }

  private final class IndexDecryptStage(cryptoModules: CryptoModuleRegistry, config: CryptoConfig, keyChain: KeyChain)
    extends GraphStage[FlowShape[EncryptedIndexData, ByteString]] {

    val inlet = Inlet[EncryptedIndexData]("IndexDecrypt.in")
    val outlet = Outlet[ByteString]("IndexDecrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val log = Logging(sc.implicits.actorSystem, "IndexDecryptStage")
      private[this] val encryption = IndexEncryption(cryptoModules, sc.serialization)

      private[this] def pullInlet(): Unit = {
        if (isClosed(inlet)) {
          complete(outlet)
        } else {
          pull(inlet)
        }
      }

      private[this] def tryDecrypt(data: EncryptedIndexData): Unit = {
        try {
          log.debug("Decrypting index frame {} with keys: {}", data.id, keyChain.decKeys)
          val result = encryption.decrypt(data, keyChain)
          push(outlet, result)
        } catch { case NonFatal(exc) ⇒
          log.error(CryptoException.DecryptError(exc), "Index file decrypt error")
          pullInlet()
        }
      }                                        

      def onPull(): Unit = {
        pullInlet()
      }

      def onPush(): Unit = {
        val encryptedData = grab(inlet)
        tryDecrypt(encryptedData)
      }

      setHandlers(inlet, outlet, this)
    }
  }
}
