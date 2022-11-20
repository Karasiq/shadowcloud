package com.karasiq.shadowcloud.api

object SCApiUtils {
  val RequestedWith: String = "SCAjaxApiClient"
  val PostHeaders           = Map("X-Requested-With" → RequestedWith)
}
