package com.karasiq.shadowcloud.test.utils

import scala.language.postfixOps
import scala.util.Random

import akka.util.ByteString

import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.index.File.Revision
import com.karasiq.shadowcloud.providers.ModuleRegistry

object TestUtils extends TestImplicits {
  val config = AppConfig.load()
  val modules = ModuleRegistry(config)
  val sha1Hashing = modules.hashingModule(HashingMethod("SHA1"))
  val aesEncryption = modules.encryptionModule(EncryptionMethod("AES/GCM", 256))

  def randomBytes(length: Int): ByteString = {
    val array = new Array[Byte](length)
    Random.nextBytes(array)
    ByteString(array)
  }

  def randomString: String = {
    randomBytes(4).toHexString
  }

  def testTimestamp: Long = {
    1486917426797L
  }

  def indexedBytes: (ByteString, File) = {
    val text = ByteString("""You may have noticed various code patterns that emerge when testing stream pipelines. Akka Stream has a separate akka-stream-testkit module that provides tools specifically for writing stream tests. This module comes with two main components that are TestSource and TestSink which provide sources and sinks that materialize to probes that allow fluent API.""")
    val hashingMethod = HashingMethod("SHA1")
    val textHash = ByteString.fromHexString("2f5a0c419cfeb92f05888ae3468e54fee3ee1726")
    val preCalcHashes = Vector("f660847d03634f41c45f7be337b02973a083721a", "dfa6cbe4eb725d390e3339075fe420791a5a394f", "e63bf72054623e911ce6a995dc520527d7fe2e2d", "802f6e7f54ca13c650741e65f188b0bdb023cb15").map(ByteString.fromHexString)

    val chunks = Seq(
      Chunk(Checksum(hashingMethod, hashingMethod, 100, preCalcHashes(0), 100, preCalcHashes(0)), EncryptionParameters.empty, Data(text.slice(0, 100), text.slice(0, 100))),
      Chunk(Checksum(hashingMethod, hashingMethod, 100, preCalcHashes(1), 100, preCalcHashes(1)), EncryptionParameters.empty, Data(text.slice(100, 200), text.slice(100, 200))),
      Chunk(Checksum(hashingMethod, hashingMethod, 100, preCalcHashes(2), 100, preCalcHashes(2)), EncryptionParameters.empty, Data(text.slice(200, 300), text.slice(200, 300))),
      Chunk(Checksum(hashingMethod, hashingMethod, 56, preCalcHashes(3), 56, preCalcHashes(3)), EncryptionParameters.empty, Data(text.slice(300, 356), text.slice(300, 356)))
    )
    (text, File(Path.root / "test.txt", Timestamp(testTimestamp, testTimestamp), Revision.first, Checksum(hashingMethod, hashingMethod, 356, textHash, 356, textHash), chunks))
  }

  def testChunk: Chunk = {
    indexedBytes._2.chunks.head
  }

  def testDiff: IndexDiff = {
    val (_, file) = indexedBytes
    IndexDiff(testTimestamp, FolderIndexDiff.seq(FolderDiff(Path.root, testTimestamp, newFiles = Set(file))), ChunkIndexDiff(file.chunks.toSet))
  }

  def randomChunk: Chunk = {
    val data = randomBytes(100)
    val encParameters = aesEncryption.createParameters()
    val encData = aesEncryption.encrypt(data, encParameters)
    Chunk(
      Checksum(sha1Hashing.method, sha1Hashing.method, data.length, sha1Hashing.createHash(data), encData.length, sha1Hashing.createHash(encData)),
      encParameters,
      Data(data, encData)
    )
  }

  def randomFile(parent: Path = Path.root): File = {
    val chunks = Seq.fill(1)(randomChunk)
    val size = chunks.map(_.checksum.size).sum
    val encSize = chunks.map(_.checksum.encryptedSize).sum
    val hash = sha1Hashing.createHash(ByteString.fromChunks(chunks))
    val encHash = sha1Hashing.createHash(ByteString.fromEncryptedChunks(chunks))
    File(parent / s"$randomString.txt", Timestamp.now, File.Revision(Random.nextInt(10)),
      Checksum(sha1Hashing.method, sha1Hashing.method, size, hash, encSize, encHash), chunks)
  }

  def randomFolder(path: Path = Path.root / randomString): Folder = {
    val folders = Seq.fill(1)(randomString)
    val files = Seq.fill(1)(randomFile(path))
    Folder(path, Timestamp.now, folders.toSet, files.toSet)
  }

  def randomDiff: IndexDiff = {
    val folder = randomFolder()
    val chunks = folder.files.flatMap(_.chunks)
    IndexDiff(folder.timestamp.lastModified, FolderIndexDiff.create(folder), ChunkIndexDiff(chunks))
  }
}
