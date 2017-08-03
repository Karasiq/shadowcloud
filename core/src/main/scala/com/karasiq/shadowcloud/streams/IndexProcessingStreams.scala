package com.karasiq.shadowcloud.streams

import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.NotUsed
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.{Compression, Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.{CryptoConfig, StorageConfig}
import com.karasiq.shadowcloud.config.keys.KeyChain
import com.karasiq.shadowcloud.crypto.index.IndexEncryption
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.serialization.StreamSerialization
import com.karasiq.shadowcloud.serialization.protobuf.index.{EncryptedIndexData, SerializedIndexData}
import com.karasiq.shadowcloud.streams.utils.ByteStringConcat
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
  def serialize(storageConfig: StorageConfig): Flow[IndexData, ByteString, NotUsed] = {
    Flow[IndexData]
      .flatMapConcat { indexData ⇒
        val dataSrc = Source.single(indexData)
          .via(StreamSerialization(sc.serialization).toBytes)
        compress(storageConfig.indexCompression, dataSrc)
          .via(ByteStringConcat())
          .map(data ⇒ SerializedIndexData(storageConfig.indexCompression, data))
      }
      .map(sd ⇒ ByteString(sd.toByteArray))
  }

  val deserialize = Flow[ByteString]
    .map(bs ⇒ SerializedIndexData.parseFrom(bs.toArray))
    .flatMapConcat { data ⇒
      decompress(data.compression, Source.single(data.data))
        .via(ByteStringConcat())
        .via(StreamSerialization(sc.serialization).fromBytes[IndexData])
    }

  val encrypt = Flow[ByteString]
    .via(AkkaStreamUtils.extractStream)
    .zip(Source.fromFuture(sc.keys.provider.getKeyChain()))
    .flatMapConcat { case (source, keyChain) ⇒
      source.via(new IndexEncryptStage(sc.modules, sc.config.crypto, keyChain))
    }

  val decrypt = Flow[EncryptedIndexData]
    .via(AkkaStreamUtils.extractStream)
    .zip(Source.fromFuture(sc.keys.provider.getKeyChain()))
    .flatMapConcat { case (source, keyChain) ⇒
      source.via(new IndexDecryptStage(sc.modules, sc.config.crypto, keyChain))
    }

  def preWrite(storageConfig: StorageConfig): Flow[IndexData, ByteString, NotUsed] = {
    Flow[IndexData]
      .via(serialize(storageConfig))
      .via(encrypt)
      .map(ed ⇒ ByteString(ed.toByteArray))
      .named("indexPreWrite")
  }

  // TODO: Limit index frame size
  val postRead: Flow[ByteString, IndexData, NotUsed] = Flow[ByteString]
    .map(bs ⇒ EncryptedIndexData.parseFrom(bs.toArray))
    .via(decrypt)
    .via(deserialize)
    .addAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .named("indexPostRead")

  // -----------------------------------------------------------------------
  // Encryption stages
  // -----------------------------------------------------------------------
  private final class IndexEncryptStage(modules: SCModules, config: CryptoConfig, keyChain: KeyChain)
    extends GraphStage[FlowShape[ByteString, EncryptedIndexData]] {

    val inlet = Inlet[ByteString]("IndexEncrypt.in")
    val outlet = Outlet[EncryptedIndexData]("IndexEncrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val encryption = IndexEncryption(modules, config.encryption.keys, config.signing.index, sc.serialization)

      private[this] def encryptData(data: ByteString): Unit = {
        require(keyChain.encKeys.nonEmpty, "No keys available")
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

  private final class IndexDecryptStage(modules: SCModules, config: CryptoConfig, keyChain: KeyChain)
    extends GraphStage[FlowShape[EncryptedIndexData, ByteString]] {

    val inlet = Inlet[EncryptedIndexData]("IndexDecrypt.in")
    val outlet = Outlet[ByteString]("IndexDecrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val log = Logging(sc.implicits.actorSystem, "IndexDecryptStage")
      private[this] val encryption = IndexEncryption(modules, config.encryption.keys, config.signing.index, sc.serialization)

      private[this] def pullInlet(): Unit = {
        if (isClosed(inlet)) {
          complete(outlet)
        } else {
          pull(inlet)
        }
      }

      private[this] def tryDecrypt(data: EncryptedIndexData): Unit = {
        try {
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

  // -----------------------------------------------------------------------
  // Compression
  // -----------------------------------------------------------------------
  private[this] def compress[Mat](compression: SerializedIndexData.Compression, src: Source[ByteString, Mat]): Source[ByteString, Mat] = {
    compression match {
      case SerializedIndexData.Compression.GZIP ⇒
        src.via(Compression.gzip)

      case SerializedIndexData.Compression.NONE ⇒
        src

      case SerializedIndexData.Compression.Unrecognized(c) ⇒
        throw new IllegalArgumentException("Compression not supported: " + c)
    }
  }

  private[this] def decompress[Mat](compression: SerializedIndexData.Compression, src: Source[ByteString, Mat]): Source[ByteString, Mat] = {
    compression match {
      case SerializedIndexData.Compression.GZIP ⇒
        src.via(Compression.gunzip())

      case SerializedIndexData.Compression.NONE ⇒
        src

      case SerializedIndexData.Compression.Unrecognized(c) ⇒
        throw new IllegalArgumentException("Compression not supported: " + c)
    }
  }
}
