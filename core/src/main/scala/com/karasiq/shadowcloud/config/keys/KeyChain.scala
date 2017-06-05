package com.karasiq.shadowcloud.config.keys

import java.util.UUID

case class KeyChain(encKeys: Map[UUID, KeySet], decKeys: Map[UUID, KeySet])
