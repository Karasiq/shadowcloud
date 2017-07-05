package com.karasiq.shadowcloud.utils

private[shadowcloud] trait ProviderInstantiator {
  def getInstance[T](pClass: Class[T]): T
}
