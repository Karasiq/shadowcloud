package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import java.security.MessageDigest

import org.bouncycastle.crypto.Digest

private[hashing] object BCDigestWrapper {
  private[this] final class MDDigest(md: MessageDigest) extends Digest {
    def getDigestSize: Int = {
      md.getDigestLength
    }

    def update(in: Byte): Unit = {
      md.update(in)
    }

    def update(in: Array[Byte], inOff: Int, len: Int): Unit = {
      md.update(in, inOff, len)
    }

    def doFinal(out: Array[Byte], outOff: Int): Int = {
      md.digest(out, outOff, out.length - outOff)
    }

    def getAlgorithmName: String = {
      md.getAlgorithm
    }

    def reset(): Unit = {
      md.reset()
    }

    override def toString: String = {
      s"MDDigest($md)"
    }
  }

  def apply(md: MessageDigest): Digest = {
    new MDDigest(md)
  }
}
