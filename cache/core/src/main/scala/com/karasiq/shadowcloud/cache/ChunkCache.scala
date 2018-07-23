package com.karasiq.shadowcloud.cache

import scala.concurrent.Future

import com.karasiq.shadowcloud.model.Chunk

trait ChunkCache {
  def readCached(chunk: Chunk, getChunk: () ⇒ Future[Chunk]): Future[Chunk]
}
