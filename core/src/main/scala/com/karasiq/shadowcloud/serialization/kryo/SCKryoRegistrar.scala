package com.karasiq.shadowcloud.serialization.kryo

import scala.reflect.ClassTag

import com.esotericsoftware.kryo.Kryo
import com.twitter.chill
import com.twitter.chill.{IKryoRegistrar, _}

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.config.keys.KeySet
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}

private[kryo] final class SCKryoRegistrar extends IKryoRegistrar {
  def apply(kryo: Kryo): Unit = {
    register(kryo, new ByteStringSerializer)
    register(kryo, new ConfigSerializer)
    kryo.registerClasses(Iterator(classOf[Checksum], classOf[Chunk], classOf[ChunkIndex],
      classOf[FolderIndex], classOf[ChunkIndexDiff], classOf[Data], classOf[File], classOf[Folder], classOf[FolderDiff],
      classOf[FolderIndexDiff], classOf[IndexDiff], classOf[Path], classOf[SerializedProps], classOf[HashingMethod], classOf[EncryptionMethod],
      classOf[SignMethod], classOf[SymmetricEncryptionParameters], classOf[AsymmetricEncryptionParameters], classOf[SignParameters], classOf[KeySet]
    ))
  }
  
  @inline
  private[this] def register[T: ClassTag](kryo: Kryo, serializer: chill.KSerializer[T]): Unit = {
    if (!kryo.alreadyRegistered[T]) {
      kryo.forClass(serializer)
      kryo.forSubclass(serializer)
    }
  }
}
