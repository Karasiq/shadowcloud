package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import java.security.SecureRandom

import scala.util.control.NonFatal

import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.{ECDomainParameters, ECKeyGenerationParameters, ECNamedDomainParameters}
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.{CryptoMethod, EncryptionMethod, SignMethod}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCAsymmetricKeys

trait BCECKeys extends BCAsymmetricKeys {
  protected def method: CryptoMethod

  protected val keyPairGenerator = {
    val generator = new ECKeyPairGenerator
    generator.init(new ECKeyGenerationParameters(getCurveDomainParameters(method), new SecureRandom()))
    generator
  }

  protected def getCurveDomainParameters(method: CryptoMethod): ECDomainParameters = {
    val spec = getCurveSpec(method)
    ECUtil.getNamedCurveOid(spec.getName) match {
      case null ⇒
        new ECDomainParameters(spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)

      case cid ⇒
        new ECNamedDomainParameters(cid, spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)
    }
  }

  protected def getCurveSpec(method: CryptoMethod): ECNamedCurveParameterSpec = {
    ECNamedCurveTable.getParameterSpec(getCurveName(method))
  }

  protected def getCurveName(method: CryptoMethod): String = {
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
