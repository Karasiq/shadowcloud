package com.karasiq.shadowcloud.streams

import scala.language.postfixOps

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.scaladsl.{Compression, Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.config.{AppConfig, CryptoConfig, StorageConfig}
import com.karasiq.shadowcloud.config.keys.{KeyProvider, KeySet}
import com.karasiq.shadowcloud.crypto.index.IndexEncryption
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.serialization.{SerializationModules, StreamSerialization}
import com.karasiq.shadowcloud.serialization.protobuf.index.{EncryptedIndexData, SerializedIndexData}

object IndexProcessingStreams {
  def apply(modules: ModuleRegistry, config: AppConfig, keyProvider: KeyProvider)
           (implicit as: ActorSystem): IndexProcessingStreams = {
    new IndexProcessingStreams(modules, config, keyProvider)
  }
}

final class IndexProcessingStreams(val modules: ModuleRegistry, val config: AppConfig,
                                   val keyProvider: KeyProvider)(implicit as: ActorSystem) {
  
  private[this] val serializationModule = SerializationModules.fromActorSystem

  def serialize(storageConfig: StorageConfig): Flow[IndexData, ByteString, NotUsed] = {
    Flow[IndexData]
      .flatMapConcat { indexData ⇒
        val dataSrc = Source.single(indexData)
          .via(StreamSerialization(serializationModule).toBytes)
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
        .via(StreamSerialization(serializationModule).fromBytes[IndexData])
    }

  val encrypt = Flow[ByteString]
    .via(new IndexEncryptStage(modules, config.crypto, keyProvider.forEncryption()))

  val decrypt = Flow[EncryptedIndexData]
    .via(new IndexDecryptStage(modules, config.crypto, keyProvider))

  def preWrite(storageConfig: StorageConfig): Flow[IndexData, ByteString, NotUsed] = {
    Flow[IndexData]
      .via(serialize(storageConfig))
      .via(encrypt)
      .map(ed ⇒ ByteString(ed.toByteArray))
  }

  val postRead: Flow[ByteString, IndexData, NotUsed] = Flow[ByteString]
    .map(bs ⇒ EncryptedIndexData.parseFrom(bs.toArray))
    .via(decrypt)
    .via(deserialize)

  private final class IndexEncryptStage(modules: ModuleRegistry, config: CryptoConfig, keys: KeySet)
    extends GraphStage[FlowShape[ByteString, EncryptedIndexData]] {

    val inlet = Inlet[ByteString]("IndexEncrypt.in")
    val outlet = Outlet[EncryptedIndexData]("IndexEncrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val encryption = new IndexEncryption(modules, config.encryption.keys, config.signing.index, serializationModule)

      def onPull(): Unit = {
        tryPull(inlet)
      }

      def onPush(): Unit = {
        val element = grab(inlet)
        push(outlet, encryption.encrypt(element, config.encryption.index, keys))
      }

      setHandlers(inlet, outlet, this)
    }
  }

  private final class IndexDecryptStage(modules: ModuleRegistry, config: CryptoConfig, keyProvider: KeyProvider)
    extends GraphStage[FlowShape[EncryptedIndexData, ByteString]] {

    val inlet = Inlet[EncryptedIndexData]("IndexDecrypt.in")
    val outlet = Outlet[ByteString]("IndexDecrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val encryption = new IndexEncryption(modules, config.encryption.keys,
        config.signing.index, serializationModule)

      def onPull(): Unit = {
        tryPull(inlet)
      }

      def onPush(): Unit = {
        val element = grab(inlet)
        push(outlet, encryption.decrypt(element, keyProvider.forId(element.keysId)))
      }

      setHandlers(inlet, outlet, this)
    }
  }

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
