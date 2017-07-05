package com.karasiq.shadowcloud.providers

import java.security.NoSuchAlgorithmException

import com.karasiq.shadowcloud.config.ProvidersConfig
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] class CryptoModuleRegistry(providers: ProvidersConfig[CryptoProvider])(implicit inst: ProviderInstantiator) {

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] case class Context(hashing: PartialFunction[HashingMethod, HashingModule] = PartialFunction.empty,
                                   encryption: PartialFunction[EncryptionMethod, EncryptionModule] = PartialFunction.empty,
                                   signing: PartialFunction[SignMethod, SignModule] = PartialFunction.empty)

  private[this] val providerInstances = providers.instances
  private[this] val providerMap = providerInstances.toMap
  private[this] val context = providerInstances.foldLeft(Context()) { case (ctx, (_, pr)) ⇒
    ctx.copy(
      pr.hashing.orElse(ctx.hashing),
      pr.encryption.orElse(ctx.encryption),
      pr.signing.orElse(ctx.signing)
    )
  }

  // -----------------------------------------------------------------------
  // Modules
  // -----------------------------------------------------------------------
  def hashingModule(method: HashingMethod): HashingModule = {
    val pf = if (method.provider.isEmpty) {
      context.hashing
    } else {
      providerMap(method.provider).hashing
    }
    moduleFromPF(pf, method)
  }

  def streamHashingModule(method: HashingMethod): StreamHashingModule = {
    hashingModule(method.copy(stream = true)) match {
      case m: StreamHashingModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream hashing module required")
    }
  }

  def encryptionModule(method: EncryptionMethod): EncryptionModule = {
    val pf = if (method.provider.isEmpty) {
      context.encryption
    } else {
      providerMap(method.provider).encryption
    }
    moduleFromPF(pf, method)
  }

  def streamEncryptionModule(method: EncryptionMethod): StreamEncryptionModule = {
    encryptionModule(method.copy(stream = true)) match {
      case m: StreamEncryptionModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream encryption module required")
    }
  }

  def signModule(method: SignMethod): SignModule = {
    val pf = if (method.provider.isEmpty) {
      context.signing
    } else {
      providerMap(method.provider).signing
    }
    moduleFromPF(pf, method)
  }

  def streamSignModule(method: SignMethod): StreamSignModule = {
    signModule(method.copy(stream = true)) match {
      case m: StreamSignModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream signature module required")
    }
  }

  @inline
  private[this] def moduleFromPF[T <: CryptoMethod, R](pf: PartialFunction[T, R], method: T): R = {
    if (pf.isDefinedAt(method)) pf(method) else throw new NoSuchAlgorithmException(method.algorithm)
  }

  // -----------------------------------------------------------------------
  // Algorithms list
  // -----------------------------------------------------------------------
  val hashingAlgorithms: Set[String] = {
    providerMap.values.flatMap(_.hashingAlgorithms).toSet
  }

  val encryptionAlgorithms: Set[String] = {
    providerMap.values.flatMap(_.encryptionAlgorithms).toSet
  }

  val signingAlgorithms: Set[String] = {
    providerMap.values.flatMap(_.signingAlgorithms).toSet
  }
}
