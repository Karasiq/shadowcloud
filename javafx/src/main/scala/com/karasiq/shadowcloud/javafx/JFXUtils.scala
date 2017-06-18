package com.karasiq.shadowcloud.javafx

private[javafx] object JFXUtils {
  def getResourcePath(fileName: String): String = {
    getClass.getClassLoader.getResource(fileName).toString
  }
}
