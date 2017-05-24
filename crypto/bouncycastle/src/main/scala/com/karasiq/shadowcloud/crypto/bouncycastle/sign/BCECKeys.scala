package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import java.security.SecureRandom

import scala.util.control.NonFatal

import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{ECDomainParameters, ECKeyGenerationParameters}
import org.bouncycastle.jce.ECNamedCurveTable

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.{CryptoMethod, EncryptionMethod, SignMethod}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCAsymmetricKeys

trait BCECKeys extends BCAsymmetricKeys {
  protected def method: CryptoMethod

  protected val keyPairGenerator = {
    val generator = new ECKeyPairGenerator
    val spec = ECNamedCurveTable.getParameterSpec(getCurveName(method))
    val domainParameters = new ECDomainParameters(spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)
    generator.init(new ECKeyGenerationParameters(domainParameters, new SecureRandom()))
    generator
  }

  private[this] def getCurveName(method: CryptoMethod): String = {
    val curveName = try {
      val config = ConfigProps.toConfig(method.config)
      Some(config.getString("curve"))
    } catch { case NonFatal(_) ⇒ None }

    def keySize: Int = method match {
      case em: EncryptionMethod ⇒
        em.keySize

      case sm: SignMethod ⇒
        sm.keySize

      case _ ⇒
        256
    }

    curveName.getOrElse("P-" + keySize)
  }
}
