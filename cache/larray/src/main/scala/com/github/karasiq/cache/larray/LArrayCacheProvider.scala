package com.github.karasiq.cache.larray

import akka.actor.ActorSystem

import com.karasiq.shadowcloud.cache.{CacheProvider, ChunkCache}

class LArrayCacheProvider extends CacheProvider {
  def createChunkCache(actorSystem: ActorSystem): ChunkCache = {
    val config = actorSystem.settings.config.getConfig("shadowcloud.cache.larray")
    new LArrayChunkCache(config.getBytes("size"))
  }
}
