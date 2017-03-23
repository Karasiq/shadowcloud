package com.karasiq.shadowcloud.serialization.kryo

import akka.actor.ExtendedActorSystem
import com.twitter.chill.KryoInstantiator
import com.twitter.chill.akka.AkkaSerializer

private[kryo] final class SCKryoAkkaSerializer(system: ExtendedActorSystem) extends AkkaSerializer(system) {
  override def kryoInstantiator: KryoInstantiator = {
    super.kryoInstantiator.withRegistrar(new SCKryoRegistrar)
  }
}
