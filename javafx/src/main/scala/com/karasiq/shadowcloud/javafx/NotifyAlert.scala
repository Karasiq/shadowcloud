package com.karasiq.shadowcloud.javafx

import javafx.stage.Stage
import scalafx.application.JFXApp.PrimaryStage
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, ButtonType}

private[javafx] object NotifyAlert {
  def show(stage: PrimaryStage, msg: String): Unit = JFXUtils.runNow {
    val alert = new Alert(AlertType.Information, msg, ButtonType.OK) {
      initOwner(stage)
      dialogPane().getScene.getWindow.asInstanceOf[Stage].setAlwaysOnTop(true)
    }
    alert.showAndWait()
  }
}
