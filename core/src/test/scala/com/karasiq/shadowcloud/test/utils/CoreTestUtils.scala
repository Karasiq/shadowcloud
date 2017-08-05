package com.karasiq.shadowcloud.test.utils

import scala.language.postfixOps
import scala.util.{Random, Try}

import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}

import com.karasiq.shadowcloud.config.{RegionConfig, SCConfig, StorageConfig}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.utils.ProviderInstantiator

object CoreTestUtils extends TestImplicits {
  import TestUtils.{randomBytes, randomString}

  val config = SCConfig(ConfigFactory.load().getConfig("shadowcloud"))
  val modules = SCModules(config)(new TestProviderInstantiator)
  val sha1Hashing = modules.crypto.hashingModule(HashingMethod("SHA1"))
  val aesEncryption = modules.crypto.encryptionModule(EncryptionMethod("AES/GCM", 256))

  def regionConfig(regionId: String): RegionConfig = {
    RegionConfig.forId(regionId, config.rootConfig)
  }

  def storageConfig(storageId: String): StorageConfig = {
    StorageConfig.forId(storageId, config.rootConfig)
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
    val encSize = chunks.map(_.checksum.encSize).sum
    val hash = sha1Hashing.createHash(ByteString.fromChunks(chunks))
    val encHash = sha1Hashing.createHash(ByteString.fromEncryptedChunks(chunks))
    File(parent / s"$randomString.txt", File.newFileId, Timestamp.now, Random.nextInt(10),
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

  private[this] final class TestProviderInstantiator extends ProviderInstantiator {
    def getInstance[T](pClass: Class[T]): T = {
      Try(pClass.getConstructor(classOf[SCConfig]).newInstance(config))
        .orElse(Try(pClass.getConstructor(classOf[Config]).newInstance(config.rootConfig)))
        .orElse(Try(pClass.newInstance()))
        .getOrElse(throw new InstantiationException("No appropriate constructor found for " + pClass))
    }
  }
}
