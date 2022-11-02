package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.model.crypto.{CryptoMethod, EncryptionMethod, SignMethod}
import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{ECDomainParameters, ECKeyGenerationParameters, ECNamedDomainParameters}
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec

import scala.util.control.NonFatal

private[bouncycastle] object ECUtils {
  // Curve list: org.bouncycastle.crypto.ec.CustomNamedCurves.getByName

  def getCurveForSize(keySize: Int): String = {
    "P-" + keySize
  }

  def getCurveName(method: CryptoMethod): String = {
    def getDefaultCurve(method: CryptoMethod): String = {
      val keySize = method match {
        case em: EncryptionMethod ⇒
          em.keySize

        case sm: SignMethod ⇒
          sm.keySize

        case _ ⇒
          256
      }
      getCurveForSize(keySize)
    }

    val curveName =
      try {
        val config = ConfigProps.toConfig(method.config)
        Some(config.getString("curve"))
      } catch {
        case NonFatal(_) ⇒
          None
      }

    curveName.getOrElse(getDefaultCurve(method))
  }

  def getCurveSpec(method: CryptoMethod): ECNamedCurveParameterSpec = {
    val curveName = getCurveName(method)
    ECNamedCurveTable.getParameterSpec(curveName)
  }

  def getCurveDomainParameters(method: CryptoMethod): ECDomainParameters = {
    val spec = getCurveSpec(method)
    ECUtil.getNamedCurveOid(spec.getName) match {
      case null ⇒
        new ECDomainParameters(spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)

      case curveId ⇒
        new ECNamedDomainParameters(curveId, spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)
    }
  }

  def createKeyGenerator(method: CryptoMethod): AsymmetricCipherKeyPairGenerator = {
    val generator        = new ECKeyPairGenerator
    val domainParameters = ECUtils.getCurveDomainParameters(method)
    val secureRandom     = BCUtils.createSecureRandom()
    generator.init(new ECKeyGenerationParameters(domainParameters, secureRandom))
    generator
  }
}
