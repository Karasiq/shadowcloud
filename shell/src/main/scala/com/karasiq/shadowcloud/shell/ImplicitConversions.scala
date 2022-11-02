package com.karasiq.shadowcloud.shell

import java.nio.file.{Paths, Path ⇒ FSPath}

trait ImplicitConversions {
  implicit def toFSPath(path: String): FSPath = {
    Paths.get(path)
  }
}
