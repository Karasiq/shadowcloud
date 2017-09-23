package com.karasiq.shadowcloud.actors.messages

import com.karasiq.shadowcloud.model.StorageId

@SerialVersionUID(0L)
final case class StorageEnvelope(storageId: StorageId, message: Any)
