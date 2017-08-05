package com.karasiq.shadowcloud.storage.utils

import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.storage.StorageIOResult

private[shadowcloud] case class IndexIOResult[Key](key: Key, diff: IndexData, ioResult: StorageIOResult)
