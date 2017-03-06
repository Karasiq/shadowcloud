package com.karasiq.shadowcloud.serialization.kryo

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.serialization.SerializationModule
import com.twitter.chill.{KryoBase, KryoPool, ScalaKryoInstantiator}

import scala.language.postfixOps
import scala.reflect.ClassTag

/**
  * Kryo serialization module
  * @see [[https://github.com/EsotericSoftware/kryo]]
  * @see [[https://github.com/twitter/chill]]
  */
private[serialization] final class KryoSerializationModule extends SerializationModule {
  private[this] val instantiator = new ScalaKryoInstantiator() {
    override def newKryo(): KryoBase = {
      val kryo = super.newKryo()
      kryo.forSubclass(new ByteStringSerializer)
      kryo.forSubclass(new ConfigSerializer)
      kryo.registerClasses(Iterator(classOf[ByteString], classOf[Checksum], classOf[Chunk], classOf[ChunkIndex],
        classOf[FolderIndex], classOf[ChunkIndexDiff], classOf[Data], classOf[File], classOf[Folder], classOf[FolderDiff],
        classOf[FolderIndexDiff], classOf[IndexDiff], classOf[Path], classOf[HashingMethod], classOf[EncryptionMethod],
        classOf[SymmetricEncryptionParameters], classOf[AsymmetricEncryptionParameters]
      ))
      kryo
    }
  }
  private[this] val kryoPool = KryoPool.withByteArrayOutputStream(sys.runtime.availableProcessors(), instantiator)

  def toBytes[T: ClassTag](value: T): ByteString = {
    ByteString(kryoPool.toBytesWithoutClass(value))
  }

  def fromBytes[T: ClassTag](value: ByteString): T = {
    kryoPool.fromBytes(value.toArray, implicitly[ClassTag[T]].runtimeClass).asInstanceOf[T]
  }
}