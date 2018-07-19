package com.karasiq.shadowcloud.test.utils

import scala.language.postfixOps

import com.karasiq.shadowcloud.{ShadowCloud, ShadowCloudExtension}

abstract class SCExtensionSpec extends ActorSpec with ActorSpecImplicits with ByteStringImplicits {
  implicit val sc: ShadowCloudExtension = ShadowCloud(system)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    sc.init()
  }
}
