package com.karasiq.shadowcloud.cache

import akka.actor.ActorSystem

trait CacheProvider {
  def createChunkCache(actorSystem: ActorSystem): ChunkCache
}
