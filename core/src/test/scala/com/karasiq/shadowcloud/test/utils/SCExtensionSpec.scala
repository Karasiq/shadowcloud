package com.karasiq.shadowcloud.test.utils

import scala.language.postfixOps

import com.karasiq.shadowcloud.{ShadowCloud, ShadowCloudExtension}

abstract class SCExtensionSpec extends ActorSpec {
  implicit val sc: ShadowCloudExtension = ShadowCloud(system)
}
