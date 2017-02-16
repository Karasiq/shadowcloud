package com.karasiq.shadowcloud.storage.wrappers

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.ChunkRepository

import scala.language.postfixOps

trait ByteStringChunkRepository extends ChunkRepository[ByteString]
