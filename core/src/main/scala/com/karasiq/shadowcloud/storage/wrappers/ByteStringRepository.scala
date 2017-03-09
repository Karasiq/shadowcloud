package com.karasiq.shadowcloud.storage.wrappers

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.Repository

import scala.language.postfixOps

trait ByteStringRepository extends Repository[ByteString]
