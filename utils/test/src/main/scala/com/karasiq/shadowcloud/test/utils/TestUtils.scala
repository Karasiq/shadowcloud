package com.karasiq.shadowcloud.test.utils

import java.util.UUID

import scala.util.Random

import akka.util.ByteString

import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters, HashingMethod, SymmetricEncryptionParameters}

object TestUtils {
  def randomBytes(length: Int): ByteString = {
    val array = new Array[Byte](length)
    Random.nextBytes(array)
    ByteString.fromArrayUnsafe(array)
  }

  def randomString: String = {
    HexString.encode(randomBytes(4))
  }

  def testTimestamp: Long = {
    1486917426797L
  }

  def indexedBytes: (ByteString, File) = {
    val text = ByteString(
      """You may have noticed various code patterns that emerge when testing stream pipelines. Akka Stream has a separate akka-stream-testkit module that provides tools specifically for writing stream tests. This module comes with two main components that are TestSource and TestSink which provide sources and sinks that materialize to probes that allow fluent API."""
    )
    val hashingMethod = HashingMethod("SHA1")
    val textHash      = HexString.decode("2f5a0c419cfeb92f05888ae3468e54fee3ee1726")
    val preCalcHashes = Vector(
      "f660847d03634f41c45f7be337b02973a083721a",
      "dfa6cbe4eb725d390e3339075fe420791a5a394f",
      "e63bf72054623e911ce6a995dc520527d7fe2e2d",
      "802f6e7f54ca13c650741e65f188b0bdb023cb15"
    ).map(HexString.decode)

    val chunks = Seq(
      Chunk(
        Checksum(hashingMethod, hashingMethod, 100, preCalcHashes(0), 100, preCalcHashes(0)),
        EncryptionParameters.empty,
        Data(text.slice(0, 100), text.slice(0, 100))
      ),
      Chunk(
        Checksum(hashingMethod, hashingMethod, 100, preCalcHashes(1), 100, preCalcHashes(1)),
        EncryptionParameters.empty,
        Data(text.slice(100, 200), text.slice(100, 200))
      ),
      Chunk(
        Checksum(hashingMethod, hashingMethod, 100, preCalcHashes(2), 100, preCalcHashes(2)),
        EncryptionParameters.empty,
        Data(text.slice(200, 300), text.slice(200, 300))
      ),
      Chunk(
        Checksum(hashingMethod, hashingMethod, 56, preCalcHashes(3), 56, preCalcHashes(3)),
        EncryptionParameters.empty,
        Data(text.slice(300, 356), text.slice(300, 356))
      )
    )
    (
      text,
      File(
        Path.root / "test.txt",
        UUID.fromString("c3cd9085-b2d5-43fb-b85c-a8448698f7a6"),
        0,
        Timestamp(testTimestamp, testTimestamp),
        SerializedProps.empty,
        Checksum(hashingMethod, hashingMethod, 356, textHash, 356, textHash),
        chunks
      )
    )
  }

  def testChunk: Chunk = {
    indexedBytes._2.chunks.head
  }

  def testDiff: IndexDiff = {
    val (_, file) = indexedBytes
    IndexDiff(testTimestamp, FolderIndexDiff.fromDiffs(FolderDiff(Path.root, testTimestamp, newFiles = Set(file))), ChunkIndexDiff(file.chunks.toSet))
  }

  def testSymmetricParameters: EncryptionParameters = {
    SymmetricEncryptionParameters(
      EncryptionMethod("ChaCha20"),
      HexString.decode("911acc04d881733ca3eb74ca2dd11655ebae2cd94bf1596364c552705a8d5c62"),
      HexString.decode("911acc04d881733c")
    )
  }
}
