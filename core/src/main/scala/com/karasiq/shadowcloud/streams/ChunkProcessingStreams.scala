package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, ZipWith}
import akka.util.ByteString

import com.karasiq.shadowcloud.config.{CryptoConfig, ParallelismConfig, SCConfig}
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.index.{Chunk, Data}
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.utils.{MemorySize, ProviderInstantiator}

object ChunkProcessingStreams {
  def apply(modules: SCModules, crypto: CryptoConfig,
            parallelism: ParallelismConfig)(implicit ec: ExecutionContext): ChunkProcessingStreams = {
    new ChunkProcessingStreams(modules, crypto, parallelism)
  }

  def apply(config: SCConfig)(implicit ec: ExecutionContext, inst: ProviderInstantiator): ChunkProcessingStreams = {
    apply(SCModules(config), config.crypto, config.parallelism)
  }
}

final class ChunkProcessingStreams(val modules: SCModules, val crypto: CryptoConfig,
                                   val parallelism: ParallelismConfig)(implicit ec: ExecutionContext) {
  type ChunkFlow = Flow[Chunk, Chunk, NotUsed]

  def split(chunkSize: Int = MemorySize.MB): Flow[ByteString, Chunk, NotUsed] = {
    ChunkSplitter(chunkSize)
  }

  def generateKey(method: EncryptionMethod = crypto.encryption.chunks): ChunkFlow = {
    Flow.fromGraph(GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val keys = builder.add(ChunkKeyStream(modules, method))
      val chunkWithKey = builder.add(ZipWith((key: EncryptionParameters, chunk: Chunk) ⇒ chunk.copy(encryption = key)))
      keys ~> chunkWithKey.in0
      FlowShape(chunkWithKey.in1, chunkWithKey.out)
    })
  }

  def encrypt: ChunkFlow = parallelFlow(parallelism.encryption) { chunk ⇒
    chunk.data match {
      case Data(plain, ByteString.empty) if plain.nonEmpty ⇒
        val module = modules.crypto.encryptionModule(chunk.encryption.method)
        chunk.copy(data = chunk.data.copy(encrypted = module.encrypt(plain, chunk.encryption)))

      case Data(_, encrypted) if encrypted.nonEmpty ⇒
        chunk

      case _ ⇒
        throw new IllegalArgumentException("Chunk data is empty")
    }
  }

  def createHashes(plainMethod: HashingMethod = crypto.hashing.chunks,
                   encMethod: HashingMethod = crypto.hashing.chunksEncrypted): ChunkFlow = parallelFlow(parallelism.hashing) { chunk ⇒
    val hasher = modules.crypto.hashingModule(plainMethod)
    val encHasher = modules.crypto.hashingModule(encMethod)
    val size = chunk.data.plain.length
    val hash = if (chunk.data.plain.nonEmpty) hasher.createHash(chunk.data.plain) else ByteString.empty
    val encSize = chunk.data.encrypted.length
    val encHash = if (chunk.data.encrypted.nonEmpty) encHasher.createHash(chunk.data.encrypted) else ByteString.empty
    chunk.copy(checksum = chunk.checksum.copy(plainMethod, encMethod, size, hash, encSize, encHash))
  }

  def decrypt: ChunkFlow = parallelFlow(parallelism.encryption) { chunk ⇒
    chunk.data match {
      case Data(ByteString.empty, encrypted) if encrypted.nonEmpty ⇒
        val decryptor = modules.crypto.encryptionModule(chunk.encryption.method)
        val decryptedData = decryptor.decrypt(encrypted, chunk.encryption)
        chunk.copy(data = chunk.data.copy(plain = decryptedData))

      case Data(plain, _) if plain.nonEmpty ⇒
        chunk

      case _ ⇒
        throw new IllegalArgumentException("Chunk data is empty")
    }
  }

  def verify: ChunkFlow = parallelFlow(parallelism.hashing) { chunk ⇒
    val hasher = modules.crypto.hashingModule(chunk.checksum.method)
    if (chunk.checksum.hash.nonEmpty && hasher.createHash(chunk.data.plain) != chunk.checksum.hash) {
      throw new IllegalArgumentException(s"Chunk plaintext checksum not match: $chunk")
    } else if (chunk.checksum.encHash.nonEmpty && hasher.createHash(chunk.data.encrypted) != chunk.checksum.encHash) {
      throw new IllegalArgumentException(s"Chunk ciphertext checksum not match: $chunk")
    } else if ((chunk.data.plain.nonEmpty && chunk.checksum.size != chunk.data.plain.length) ||
      (chunk.data.encrypted.nonEmpty && chunk.checksum.encSize != chunk.data.encrypted.length)) {
      throw new IllegalArgumentException(s"Chunk sizes not match: $chunk")
    } else {
      chunk
    }
  }

  def beforeWrite(encryption: EncryptionMethod = crypto.encryption.chunks,
                  hashing: HashingMethod = crypto.hashing.chunks,
                  encHashing: HashingMethod = crypto.hashing.chunksEncrypted): ChunkFlow = {
    generateKey(encryption).via(encrypt).via(createHashes(hashing, encHashing))
  }

  def afterRead: ChunkFlow = {
    decrypt.via(verify)
  }

  def index(plainHashing: HashingMethod = crypto.hashing.files,
            encHashing: HashingMethod = crypto.hashing.filesEncrypted): Sink[Chunk, Future[FileIndexer.Result]] = {
    FileIndexer(modules.crypto, plainHashing, encHashing).async
  }

  protected def parallelFlow(parallelism: Int)(func: Chunk ⇒ Chunk): ChunkFlow = {
    require(parallelism > 0)
    Flow[Chunk].mapAsync(parallelism)(chunk ⇒ Future(func(chunk)))
  }
}
