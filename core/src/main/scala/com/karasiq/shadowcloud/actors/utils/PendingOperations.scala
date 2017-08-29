package com.karasiq.shadowcloud.actors.utils

import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.model.Chunk

private[actors] object PendingOperations {
  def withChunk: PendingOperation[Chunk] = new PendingOperation
  def withRegionChunk: PendingOperation[(ChunkPath, Chunk)] = new PendingOperation
}
