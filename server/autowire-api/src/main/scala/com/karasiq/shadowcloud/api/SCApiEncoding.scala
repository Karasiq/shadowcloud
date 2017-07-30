package com.karasiq.shadowcloud.api

import com.karasiq.shadowcloud.index.Path

trait SCApiEncoding {
  def encodePath(path: Path): String
  def decodePath(pathString: String): Path 
}

object SCApiEncoding {
  val default = SCJsonEncoding
}
