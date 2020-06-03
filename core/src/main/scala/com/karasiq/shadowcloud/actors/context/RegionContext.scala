package com.karasiq.shadowcloud.actors.context

import akka.actor.ActorRef
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.storage.replication.{ChunkStatusProvider, RegionStorageProvider}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey

case class RegionContext(
    id: RegionId,
    config: RegionConfig,
    dispatcher: ActorRef,
    storages: RegionStorageProvider,
    chunks: ChunkStatusProvider,
    index: IndexMerger[RegionKey]
)
