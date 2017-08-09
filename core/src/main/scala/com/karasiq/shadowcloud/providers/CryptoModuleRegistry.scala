package com.karasiq.shadowcloud.providers

import java.security.NoSuchAlgorithmException

import com.karasiq.shadowcloud.config.ProvidersConfig
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] trait CryptoModuleRegistry {
  def hashingModule(method: HashingMethod): HashingModule
  def encryptionModule(method: EncryptionMethod): EncryptionModule
  def signModule(method: SignMethod): SignModule

  def hashingAlgorithms: Set[String]
  def encryptionAlgorithms: Set[String]
  def signingAlgorithms: Set[String]
}

private[shadowcloud] object CryptoModuleRegistry {
  // -----------------------------------------------------------------------
  // Constructor
  // -----------------------------------------------------------------------
  def apply(providers: ProvidersConfig[CryptoProvider])(implicit inst: ProviderInstantiator): CryptoModuleRegistry = {
    new CryptoModuleRegistryImpl(providers)
  }

  // -----------------------------------------------------------------------
  // Wrappers
  // -----------------------------------------------------------------------
  def streamHashingModule(registry: CryptoModuleRegistry, method: HashingMethod): StreamHashingModule = {
    registry.hashingModule(method) match {
      case m: StreamHashingModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream hashing module required")
    }
  }

  def streamEncryptionModule(registry: CryptoModuleRegistry, method: EncryptionMethod): StreamEncryptionModule = {
    registry.encryptionModule(method) match {
      case m: StreamEncryptionModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream encryption module required")
    }
  }

  def streamSignModule(registry: CryptoModuleRegistry, method: SignMethod): StreamSignModule = {
    registry.signModule(method) match {
      case m: StreamSignModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream signature module required")
    }
  }
}

private[providers] final class CryptoModuleRegistryImpl(providers: ProvidersConfig[CryptoProvider])
                                                       (implicit inst: ProviderInstantiator) extends CryptoModuleRegistry {

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

  def encryptionModule(method: EncryptionMethod): EncryptionModule = {
    val pf = if (method.provider.isEmpty) {
      context.encryption
    } else {
      providerMap(method.provider).encryption
    }
    moduleFromPF(pf, method)
  }

  def signModule(method: SignMethod): SignModule = {
    val pf = if (method.provider.isEmpty) {
      context.signing
    } else {
      providerMap(method.provider).signing
    }
    moduleFromPF(pf, method)
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
