package com.karasiq.shadowcloud.desktop

import javax.imageio.ImageIO
import java.awt._
import java.awt.event.{ActionEvent, ActionListener, ItemEvent}

import scala.concurrent.Future
import scala.language.{implicitConversions, postfixOps}
import scala.util.control.NonFatal

import akka.Done
import akka.actor.ActorSystem

abstract class SCTrayIcon(implicit actorSystem: ActorSystem) {
  import actorSystem.dispatcher

  def onOpen(): Unit
  def onMount(): Future[Done]
  def onExit(): Unit

  def addToTray(): Unit = {
    if (SystemTray.isSupported) {
      val systemTray = SystemTray.getSystemTray
      val popup = new PopupMenu("shadowcloud")

      val openItem = new MenuItem("Open interface")
      openItem.addActionListener((_: ActionEvent) ⇒ onOpen())

      val mountItem = new MenuItem("Mount drive")
      mountItem.addActionListener { _: ActionEvent ⇒
        if (mountItem.isEnabled)
          onMount().foreach(_ ⇒ mountItem.setEnabled(false))
      }
      
      val exitItem = new MenuItem("Exit")
      exitItem.addActionListener((_: ActionEvent) ⇒ onExit())

      // popup.add("shadowcloud")
      // popup.addSeparator()
      popup.add(openItem)
      popup.add(mountItem)
      popup.add(exitItem)

      val trayIcon = new TrayIcon(getTrayIcon(), "shadowcloud", popup)
      try {
        systemTray.add(trayIcon)
      } catch { case NonFatal(error) ⇒
        System.err.println(error)
      }
    }
  }

  private[this] def getTrayIcon() = {
    val inputStream = getClass.getClassLoader.getResourceAsStream("sc-tray-icon.png")
    ImageIO.read(inputStream)
  }

  //noinspection ConvertExpressionToSAM
  private implicit def implicitFunctionToActionListener(f: ActionEvent ⇒ Unit): ActionListener = new ActionListener {
    def actionPerformed(e: ActionEvent): Unit = f(e)
  }
}
