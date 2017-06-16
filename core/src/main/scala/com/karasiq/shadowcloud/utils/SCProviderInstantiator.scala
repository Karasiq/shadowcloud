package com.karasiq.shadowcloud.utils

import scala.util.Try

import akka.actor.ActorSystem

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.config.SCConfig

private[shadowcloud] final class SCProviderInstantiator(sc: ShadowCloudExtension) {
  def getInstance[T](pClass: Class[T]): T = {
    Try(pClass.getConstructor(classOf[ShadowCloudExtension]).newInstance(sc))
      .orElse(Try(pClass.getConstructor(classOf[SCConfig]).newInstance(sc.config)))
      .orElse(Try(pClass.getConstructor(classOf[ActorSystem]).newInstance(sc.implicits.actorSystem)))
      .orElse(Try(pClass.newInstance()))
      .getOrElse(throw new InstantiationException("No appropriate constructor found for " + pClass))
  }
}
