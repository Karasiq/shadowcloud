package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.language.postfixOps

trait ChunkRepository {
  type ChunkKey = ByteString
  def chunks: Source[ChunkKey, _]
  def read(chunk: ChunkKey): Source[ByteString, _]
  def write(chunk: ChunkKey): Sink[ByteString, _]
}
