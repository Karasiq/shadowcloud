package com.karasiq.shadowcloud.serialization.internal

import scala.collection.concurrent.TrieMap

private[serialization] trait CompanionReflectSerializer {
  protected type Companion
  private[this] val runtime      = scala.reflect.runtime.universe.runtimeMirror(getClass.getClassLoader)
  private[this] val companionMap = TrieMap.empty[Class[_], Any]

  private[this] def getCompanionWithReflection[C](`type`: Class[_]): C = {
    runtime.reflectModule(runtime.classSymbol(`type`).companion.asModule).instance.asInstanceOf[C]
  }

  protected def getCompanion[C](`type`: Class[_]): C = {
    companionMap.getOrElseUpdate(`type`, getCompanionWithReflection[C](`type`)).asInstanceOf[C]
  }
}
