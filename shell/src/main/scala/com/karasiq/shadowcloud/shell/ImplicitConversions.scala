package com.karasiq.shadowcloud.shell

import java.nio.file.{Paths, Path ⇒ FSPath}

import scala.language.{implicitConversions, postfixOps}

trait ImplicitConversions {
  implicit def toFSPath(path: String): FSPath = {
    Paths.get(path)
  }
}
