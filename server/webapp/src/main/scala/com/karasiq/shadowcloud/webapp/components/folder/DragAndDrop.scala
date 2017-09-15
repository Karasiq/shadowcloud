package com.karasiq.shadowcloud.webapp.components.folder

import akka.util.ByteString
import org.scalajs.dom.DataTransfer

import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.model.{File, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

private[folder] object DragAndDrop {
  implicit class DataTransferOps(dataTransfer: DataTransfer) extends collection.mutable.AbstractMap[String, String] {
    def +=(kv: (String, String)): this.type = {
      dataTransfer.setData(kv._1, kv._2)
      this
    }

    def -=(key: String): DataTransferOps.this.type = {
      dataTransfer.clearData(key)
      this
    }

    def get(name: String): Option[String] = {
      Option(dataTransfer.getData(name)).filter(_.nonEmpty)
    }

    def iterator: Iterator[(String, String)] = {
      dataTransfer.types.toIterator
        .map(key ⇒ (key, dataTransfer.getData(key)))
    }

    def setBytes(name: String, value: ByteString): Unit = {
      this += name → SCApiEncoding.toUrlSafe(value)
    }

    def getEncoded[T](name: String)(decode: ByteString ⇒ T): Option[T] = {
      get(name).map(str ⇒ decode(SCApiEncoding.toBinary(str)))
    }
  }

  private[this] object Attributes {
    val path = "path"
    val file = "file"
    val folder = "folder"
    val entityType = "entityType"
    val regionId = "regionId"
    val scope = "scope"
  }

  def addRegionId(dataTransfer: DataTransfer, regionId: RegionId): Unit = {
    dataTransfer += Attributes.regionId → regionId
  }

  def addScope(dataTransfer: DataTransfer, scope: IndexScope)(implicit context: AppContext): Unit = {
    dataTransfer.setBytes(Attributes.scope, context.api.encoding.encodeScope(scope))
  }

  def addFolderContext(dataTransfer: DataTransfer)(implicit context: AppContext, folderContext: FolderContext): Unit = {
    addRegionId(dataTransfer, folderContext.regionId)
    addScope(dataTransfer, folderContext.scope.now)
  }

  def addFolderPath(dataTransfer: DataTransfer, path: Path)(implicit context: AppContext): Unit = {
    dataTransfer += Attributes.entityType → Attributes.folder
    dataTransfer.setBytes(Attributes.path, context.api.encoding.encodePath(path))
  }

  def addFilePath(dataTransfer: DataTransfer, path: Path)(implicit context: AppContext): Unit = {
    dataTransfer += Attributes.entityType → Attributes.file
    dataTransfer.setBytes(Attributes.path, context.api.encoding.encodePath(path))
  }

  def addFileHandle(dataTransfer: DataTransfer, file: File)(implicit context: AppContext): Unit = {
    dataTransfer += Attributes.entityType → Attributes.file
    dataTransfer.setBytes(Attributes.file, context.api.encoding.encodeFile(file))
  }

  def isCopyable(dataTransfer: DataTransfer): Boolean = {
    getEntityType(dataTransfer).exists(et ⇒ et == Attributes.file || et == Attributes.folder)
  }

  def isFile(dataTransfer: DataTransfer): Boolean = {
    getEntityType(dataTransfer).contains(Attributes.file)
  }

  def getRegionId(dataTransfer: DataTransfer): Option[RegionId] = {
    dataTransfer.get(Attributes.regionId)
  }

  def getScope(dataTransfer: DataTransfer)(implicit context: AppContext): Option[IndexScope] = {
    dataTransfer.getEncoded(Attributes.scope)(context.api.encoding.decodeScope)
  }

  def getPath(dataTransfer: DataTransfer)(implicit context: AppContext): Option[Path] = {
    dataTransfer.getEncoded(Attributes.path)(context.api.encoding.decodePath)
  }

  def getFile(dataTransfer: DataTransfer)(implicit context: AppContext): Option[File] = {
    dataTransfer.getEncoded(Attributes.file)(context.api.encoding.decodeFile)
  }

  private[this] def getEntityType(dataTransfer: DataTransfer) = {
    dataTransfer.get(Attributes.entityType)
  }
}
