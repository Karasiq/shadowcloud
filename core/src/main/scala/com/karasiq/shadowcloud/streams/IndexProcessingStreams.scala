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
import com.karasiq.shadowcloud.config.keys.{KeyChain, KeyProvider}
import com.karasiq.shadowcloud.crypto.index.IndexEncryption
import com.karasiq.shadowcloud.exceptions.CryptoException
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.serialization.{SerializationModules, StreamSerialization}
import com.karasiq.shadowcloud.serialization.protobuf.index.{EncryptedIndexData, SerializedIndexData}
import com.karasiq.shadowcloud.streams.utils.ByteStringConcat

object IndexProcessingStreams {
  def apply(modules: SCModules, config: SCConfig, keyProvider: KeyProvider)
           (implicit as: ActorSystem): IndexProcessingStreams = {
    new IndexProcessingStreams(modules, config, keyProvider)
  }
}

final class IndexProcessingStreams(val modules: SCModules, val config: SCConfig,
                                   val keyProvider: KeyProvider)(implicit as: ActorSystem) {

  private[this] val serializer = SerializationModules.forActorSystem(as)

  // -----------------------------------------------------------------------
  // Flows
  // -----------------------------------------------------------------------
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
    .prefixAndTail(0)
    .map(_._2)
    .zip(Source.fromFuture(keyProvider.getKeyChain()))
    .flatMapConcat { case (source, keyChain) ⇒
      source.via(new IndexEncryptStage(modules, config.crypto, keyChain))
    }

  val decrypt = Flow[EncryptedIndexData]
    .prefixAndTail(0)
    .map(_._2)
    .zip(Source.fromFuture(keyProvider.getKeyChain()))
    .flatMapConcat { case (source, keyChain) ⇒
      source.via(new IndexDecryptStage(modules, config.crypto, keyChain))
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
      private[this] val encryption = IndexEncryption(modules, config.encryption.keys, config.signing.index, serializer)

      private[this] def encryptData(data: ByteString): Unit = {
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
      private[this] val log = Logging(as, "IndexDecryptStage")
      private[this] val encryption = IndexEncryption(modules, config.encryption.keys, config.signing.index, serializer)

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
