package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import scala.language.postfixOps
import scala.util.control.NonFatal

import org.bouncycastle.crypto.params.{ECDomainParameters, ECNamedDomainParameters}
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.{CryptoMethod, EncryptionMethod, SignMethod}

private[bouncycastle] object ECUtils {
  def getCurveDomainParameters(method: CryptoMethod): ECDomainParameters = {
    val spec = getCurveSpec(method)
    ECUtil.getNamedCurveOid(spec.getName) match {
      case null ⇒
        new ECDomainParameters(spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)

      case cid ⇒
        new ECNamedDomainParameters(cid, spec.getCurve, spec.getG, spec.getN, spec.getH, spec.getSeed)
    }
  }

  def getCurveSpec(method: CryptoMethod): ECNamedCurveParameterSpec = {
    ECNamedCurveTable.getParameterSpec(getCurveName(method))
  }

  def getCurveName(method: CryptoMethod): String = {
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
