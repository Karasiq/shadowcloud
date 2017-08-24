package com.karasiq.shadowcloud.actors.messages

import com.karasiq.shadowcloud.model.StorageId

case class StorageEnvelope(storageId: StorageId, message: Any)
