package com.karasiq.shadowcloud.actors.context

import akka.actor.ActorRef

import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.storage.replication.{ChunkStatusProvider, StorageStatusProvider}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey

case class RegionContext(id: String, config: RegionConfig, dispatcher: ActorRef,
                         storages: StorageStatusProvider, chunks: ChunkStatusProvider,
                         index: IndexMerger[RegionKey])