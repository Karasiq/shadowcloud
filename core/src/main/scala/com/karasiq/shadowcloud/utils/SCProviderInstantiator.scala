package com.karasiq.shadowcloud.utils

import akka.actor.ActorSystem
import com.typesafe.config.Config

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.SCConfig

private[shadowcloud] final class SCProviderInstantiator(sc: ShadowCloudExtension) extends ProviderInstantiator {
  private[this] lazy val constructors = Array[(Array[Class[_]], Array[AnyRef])](
    (Array(classOf[ShadowCloudExtension]), Array(sc)),
    (Array(classOf[SCConfig]), Array(sc.config)),
    (Array(classOf[Config]), Array(sc.rootConfig)),
    (Array(classOf[ActorSystem]), Array(sc.implicits.actorSystem)),
    (Array.empty, Array.empty)
  )

  def getInstance[T](pClass: Class[T]): T = {
    def tryCreate(argsClasses: Seq[Class[_]], args: Seq[AnyRef]): Option[T] = {
      try {
        Some(pClass.getConstructor(argsClasses:_*).newInstance(args:_*))
      } catch { case _: NoSuchMethodException ⇒
        None
      }
    }

    constructors
      .foldLeft(None: Option[T]) { case (opt, (cArgs, args)) ⇒ opt.orElse(tryCreate(cArgs, args)) }
      .getOrElse(throw new InstantiationException("No appropriate constructor found for " + pClass))
  }
}
