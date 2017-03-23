package com.karasiq.shadowcloud.serialization.kryo

import akka.util.ByteString
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.crypto.{AsymmetricEncryptionParameters, EncryptionMethod, HashingMethod, SymmetricEncryptionParameters}
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}
import com.twitter.chill.{KryoBase, ScalaKryoInstantiator}

private[kryo] final class SCKryoInstantiator extends ScalaKryoInstantiator {
  override def newKryo(): KryoBase = {
    val kryo = super.newKryo()
    kryo.forSubclass(new ByteStringSerializer)
    kryo.forSubclass(new ConfigSerializer)
    kryo.registerClasses(Iterator(classOf[ByteString], classOf[Checksum], classOf[Chunk], classOf[ChunkIndex],
      classOf[FolderIndex], classOf[ChunkIndexDiff], classOf[Data], classOf[File], classOf[Folder], classOf[FolderDiff],
      classOf[FolderIndexDiff], classOf[IndexDiff], classOf[Path], classOf[HashingMethod], classOf[EncryptionMethod],
      classOf[SymmetricEncryptionParameters], classOf[AsymmetricEncryptionParameters], classOf[SerializedProps]
    ))
    kryo
  }
}
