package com.karasiq.shadowcloud.streams.chunk

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Sink, ZipWith}
import akka.util.ByteString
import com.karasiq.shadowcloud.config.{ChunksConfig, CryptoConfig, ParallelismConfig}
import com.karasiq.shadowcloud.exceptions.{CryptoException, SCExceptions}
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters, HashingMethod}
import com.karasiq.shadowcloud.model.{Chunk, Data}
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.streams.file.FileIndexer

import scala.concurrent.{ExecutionContext, Future}

object ChunkProcessingStreams {
  def apply(cryptoModules: CryptoModuleRegistry, chunks: ChunksConfig, crypto: CryptoConfig,
            parallelism: ParallelismConfig)(implicit ec: ExecutionContext): ChunkProcessingStreams = {
    new ChunkProcessingStreams(cryptoModules, chunks, crypto, parallelism)
  }

  /* def apply(config: SCConfig)(implicit ec: ExecutionContext, inst: ProviderInstantiator): ChunkProcessingStreams = {
    apply(SCModules(config), config.chunks, config.crypto, config.parallelism)
  } */ 
}

final class ChunkProcessingStreams(cryptoModules: CryptoModuleRegistry, chunks: ChunksConfig,
                                   crypto: CryptoConfig, parallelism: ParallelismConfig)(implicit ec: ExecutionContext) {
  type ChunkFlow = Flow[Chunk, Chunk, NotUsed]

  def split(chunkSize: Int = chunks.chunkSize): Flow[ByteString, Chunk, NotUsed] = {
    ChunkSplitter(chunkSize)
  }

  def generateKeys(method: EncryptionMethod = crypto.encryption.chunks,
                   maxKeyReuse: Int = crypto.encryption.maxKeyReuse): ChunkFlow = {
    val graph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val keys = builder.add(ChunkKeyStream(cryptoModules, method, maxKeyReuse))
      val chunkWithKey = builder.add(ZipWith((key: EncryptionParameters, chunk: Chunk) ⇒ chunk.copy(encryption = key)))
      keys ~> chunkWithKey.in0
      FlowShape(chunkWithKey.in1, chunkWithKey.out)
    }
    Flow.fromGraph(graph).named("generateKeys")
  }

  def encrypt: ChunkFlow = parallelFlow(parallelism.encryption) { chunk ⇒
    chunk.data match {
      case Data(plain, ByteString.empty) if plain.nonEmpty ⇒
        val module = cryptoModules.encryptionModule(chunk.encryption.method)
        chunk.copy(data = chunk.data.copy(encrypted = module.encrypt(plain, chunk.encryption)))

      case Data(_, encrypted) if encrypted.nonEmpty || chunk.checksum.encSize == 0 ⇒
        chunk

      case _ ⇒
        throw SCExceptions.ChunkDataIsEmpty(chunk)
    }
  } named "encrypt"

  def createHashes(plainMethod: HashingMethod = crypto.hashing.chunks,
                   encMethod: HashingMethod = crypto.hashing.chunksEncrypted): ChunkFlow = {
    parallelFlow(parallelism.hashing) { chunk ⇒
      val hasher = cryptoModules.hashingModule(plainMethod)
      val encHasher = cryptoModules.hashingModule(encMethod)

      val size = chunk.data.plain.length
      val hash = if (chunk.data.plain.nonEmpty) hasher.createHash(chunk.data.plain) else ByteString.empty

      val encSize = chunk.data.encrypted.length
      val encHash = if (chunk.data.encrypted.nonEmpty) encHasher.createHash(chunk.data.encrypted) else ByteString.empty

      chunk.copy(checksum = chunk.checksum.copy(plainMethod, encMethod, size, hash, encSize, encHash))
    } named "createHashes"
  }

  def decrypt: ChunkFlow = parallelFlow(parallelism.encryption) { chunk ⇒
    chunk.data match {
      case Data(ByteString.empty, encrypted) if encrypted.nonEmpty ⇒
        val decryptor = cryptoModules.encryptionModule(chunk.encryption.method)
        val decryptedData = decryptor.decrypt(encrypted, chunk.encryption)
        chunk.copy(data = chunk.data.copy(plain = decryptedData))

      case Data(plain, _) if plain.nonEmpty || chunk.checksum.size == 0 ⇒
        chunk

      case _ ⇒
        throw SCExceptions.ChunkDataIsEmpty(chunk)
    }
  } named "decrypt"

  def verify: ChunkFlow = parallelFlow(parallelism.hashing) { chunk ⇒
    def verifyHash(expected: ByteString, create: ⇒ ByteString): Unit = {
      if (expected.nonEmpty) {
        val actual = create
        if (expected != actual)
          throw SCExceptions.ChunkVerifyError(chunk, CryptoException.ChecksumError(expected, actual))
      }
    }

    val hasher = cryptoModules.hashingModule(chunk.checksum.method)

    if ((chunk.data.plain.nonEmpty && chunk.checksum.size != chunk.data.plain.length) ||
      (chunk.data.encrypted.nonEmpty && chunk.checksum.encSize != chunk.data.encrypted.length)) {
      throw SCExceptions.ChunkVerifyError(chunk, new IllegalArgumentException("Chunk sizes not match"))
    }

    verifyHash(chunk.checksum.hash, hasher.createHash(chunk.data.plain))
    verifyHash(chunk.checksum.encHash, hasher.createHash(chunk.data.encrypted))

    chunk
  } named "verify"

  def beforeWrite(encryption: EncryptionMethod = crypto.encryption.chunks,
                  hashing: HashingMethod = crypto.hashing.chunks,
                  encHashing: HashingMethod = crypto.hashing.chunksEncrypted): ChunkFlow = {
    generateKeys(encryption)
      // .buffer(10, OverflowStrategy.backpressure)
      .via(encrypt)
      .via(createHashes(hashing, encHashing))
  }

  def afterRead: ChunkFlow = {
    decrypt.via(verify)
  }

  def index(plainHashing: HashingMethod = crypto.hashing.files,
            encHashing: HashingMethod = crypto.hashing.filesEncrypted): Sink[Chunk, Future[FileIndexer.Result]] = {
    FileIndexer(cryptoModules, plainHashing, encHashing).async
  }

  protected def parallelFlow(parallelism: Int)(func: Chunk ⇒ Chunk): ChunkFlow = {
    require(parallelism > 0)
    Flow[Chunk].mapAsync(parallelism)(chunk ⇒ Future(func(chunk)))
  }
}
