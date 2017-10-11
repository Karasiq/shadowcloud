package com.karasiq.shadowcloud.javafx

import scalafx.geometry.Insets
import scalafx.scene.control.{ButtonType, Dialog, PasswordField}
import scalafx.scene.control.ButtonBar.ButtonData
import scalafx.scene.image.ImageView
import scalafx.scene.layout.{GridPane, Priority}
import scalafx.Includes._

private[javafx] object PasswordDialog {
  val OKButton = new ButtonType("OK", ButtonData.OKDone)
}

private[javafx] class PasswordDialog(passwordId: String) extends Dialog[String] {
  def showAndWait(): Option[String] = {
    passwordField.requestFocus()
    showAndWait((s: String) ⇒ s).asInstanceOf[Option[String]]
  }

  val passwordField = new PasswordField {
    hgrow = Priority.Always
    promptText = "Password"
  }

  val gridPane = new GridPane {
    hgap = 10
    vgap = 10
    padding = Insets(20, 10, 10, 10)
    add(passwordField, 0, 0)
  }

  title = "Password dialog"
  headerText = s"Enter $passwordId password"
  graphic = new ImageView(JFXUtils.getResourcePath("key-icon_32.png"))

  dialogPane().buttonTypes += PasswordDialog.OKButton
  val okButton = dialogPane().lookupButton(PasswordDialog.OKButton)
  okButton.disable = true
  okButton.disable <== passwordField.text.isEmpty

  resultConverter = {
    case PasswordDialog.OKButton ⇒
      passwordField.text()

    case _ ⇒
      ""
  }

  dialogPane().content = gridPane
}