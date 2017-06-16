package com.karasiq.shadowcloud.streams

import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.{Compression, Flow, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import com.karasiq.shadowcloud.config.{CryptoConfig, SCConfig, StorageConfig}
import com.karasiq.shadowcloud.config.keys.{KeyProvider, KeySet}
import com.karasiq.shadowcloud.crypto.index.IndexEncryption
import com.karasiq.shadowcloud.exceptions.IndexException
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.serialization.{SerializationModules, StreamSerialization}
import com.karasiq.shadowcloud.serialization.protobuf.index.{EncryptedIndexData, SerializedIndexData}

object IndexProcessingStreams {
  def apply(modules: SCModules, config: SCConfig, keyProvider: KeyProvider)
           (implicit as: ActorSystem): IndexProcessingStreams = {
    new IndexProcessingStreams(modules, config, keyProvider)
  }
}

final class IndexProcessingStreams(val modules: SCModules, val config: SCConfig,
                                   val keyProvider: KeyProvider)(implicit as: ActorSystem) {

  private[this] val serializer = SerializationModules.forActorSystem(as)

  def serialize(storageConfig: StorageConfig): Flow[IndexData, ByteString, NotUsed] = {
    Flow[IndexData]
      .flatMapConcat { indexData ⇒
        val dataSrc = Source.single(indexData)
          .via(StreamSerialization(serializer).toBytes)
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
        .via(StreamSerialization(serializer).fromBytes[IndexData])
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
    .addAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))

  private final class IndexEncryptStage(modules: SCModules, config: CryptoConfig, keys: KeySet)
    extends GraphStage[FlowShape[ByteString, EncryptedIndexData]] {

    val inlet = Inlet[ByteString]("IndexEncrypt.in")
    val outlet = Outlet[EncryptedIndexData]("IndexEncrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val encryption = new IndexEncryption(modules, config.encryption.keys, config.signing.index, serializer)

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

  private final class IndexDecryptStage(modules: SCModules, config: CryptoConfig, keyProvider: KeyProvider)
    extends GraphStage[FlowShape[EncryptedIndexData, ByteString]] {

    val inlet = Inlet[EncryptedIndexData]("IndexDecrypt.in")
    val outlet = Outlet[ByteString]("IndexDecrypt.out")
    val shape = FlowShape(inlet, outlet)

    def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
      private[this] val log = Logging(as, "IndexDecryptStage")
      private[this] val encryption = new IndexEncryption(modules, config.encryption.keys, config.signing.index, serializer)

      private[this] def decrypt(data: EncryptedIndexData): ByteString = {
        val key = try {
          keyProvider.forDecryption(data.keysId)
        } catch {
          case NonFatal(exc) ⇒
            throw IndexException.KeyMissing(data.keysId, exc)
        }
        encryption.decrypt(data, key)
      }

      def onPull(): Unit = {
        tryPull(inlet)
      }

      def onPush(): Unit = {
        val element = grab(inlet)
        try {
          val result = decrypt(element)
          push(outlet, result)
        } catch {
          case NonFatal(exc) ⇒
            log.error(exc, "Index file decrypt error")
        }
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
