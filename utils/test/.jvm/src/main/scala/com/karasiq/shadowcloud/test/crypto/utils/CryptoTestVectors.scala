package com.karasiq.shadowcloud.test.crypto.utils

import java.io.{ObjectInputStream, ObjectOutputStream}
import java.net.{URLDecoder, URLEncoder}
import java.nio.file.{Files, Paths, Path ⇒ FSPath}
import java.util.stream.Collectors

import akka.util.ByteString

import com.karasiq.shadowcloud.model.crypto.EncryptionParameters
import com.karasiq.shadowcloud.test.utils.ResourceUtils
import com.karasiq.shadowcloud.utils.{ByteStringInputStream, ByteStringOutputStream}

class CryptoTestVectors(testVectorsFolder: FSPath) {
  def save(name: String, parameters: EncryptionParameters, plain: ByteString, encrypted: ByteString): Unit = {
    val filePath = getVectorFilePath(name)
    if (!Files.exists(filePath)) {
      Files.createDirectories(filePath.getParent)
      Files.write(filePath, writeTestVector(parameters, plain, encrypted).toArray)
    }
  }

  def load(name: String): (EncryptionParameters, ByteString, ByteString) = {
    val bytes = ResourceUtils.toBytes(getVectorResourceName(name))
    readTestVector(bytes)
  }

  def list(): Seq[String] = {
    import scala.collection.JavaConverters._
    Files
      .list(testVectorsFolder)
      .collect(Collectors.toList[FSPath])
      .asScala
      .map(path ⇒ URLDecoder.decode(path.getFileName.toString, "UTF-8"))
      .sorted
  }

  private[this] def getVectorFilePath(name: String): FSPath = {
    testVectorsFolder.resolve(URLEncoder.encode(name, "UTF-8"))
  }

  private[this] def getVectorResourceName(name: String): String = {
    s"test-vectors/${URLEncoder.encode(name, "UTF-8")}"
  }

  private[this] def writeTestVector(parameters: EncryptionParameters, plain: ByteString, encrypted: ByteString): ByteString = {
    val bsOutput = ByteStringOutputStream()
    val objOutput = new ObjectOutputStream(bsOutput)
    objOutput.writeObject(parameters)
    objOutput.writeObject(plain)
    objOutput.writeObject(encrypted)
    objOutput.flush()
    objOutput.close()
    bsOutput.toByteString
  }

  private[this] def readTestVector(str: ByteString): (EncryptionParameters, ByteString, ByteString) = {
    val bsInput = ByteStringInputStream(str)
    val objInput = new ObjectInputStream(bsInput)
    val parameters = objInput.readObject().asInstanceOf[EncryptionParameters]
    val plain = objInput.readObject().asInstanceOf[ByteString]
    val encrypted = objInput.readObject().asInstanceOf[ByteString]
    objInput.close()
    (parameters, plain, encrypted)
  }
}

object CryptoTestVectors {
  def apply(name: String): CryptoTestVectors = {
    val path = Paths.get(s"./crypto/$name/src/test/resources/test-vectors")
    new CryptoTestVectors(path)
  }
}