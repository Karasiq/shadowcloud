package com.karasiq.shadowcloud.webapp.components.themes

import scala.language.postfixOps

import org.scalajs.dom
import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

object ThemeSelector {
  val Themes = Vector(
    "Default", "Cerulean", "Cosmo", "Cyborg", "Darkly", "Flatly", "Journal", "Lumen", "Paper", "Readable",
    "Sandstone", "Simplex", "Slate", "Spacelab", "Superhero", "United", "Yeti"
  )

  def apply(): ThemeSelector = {
    new ThemeSelector()
  }

  private def getSelectedTheme: String = {
    Option(dom.window.localStorage.getItem("bootstrap-theme"))
      .filter(_.nonEmpty)
      .getOrElse(Themes.head)
  }

  private def setSelectedTheme(theme: String): Unit = {
    dom.window.localStorage.setItem("bootstrap-theme", theme)
  }
}

class ThemeSelector extends BootstrapHtmlComponent {
  val currentTheme = Var(ThemeSelector.getSelectedTheme)

  def setTheme(theme: String): Unit = {
    currentTheme() = theme
    ThemeSelector.setSelectedTheme(theme)
  }

  def nextTheme(): Unit = {
    val currentIndex = ThemeSelector.Themes.indexOf(currentTheme.now)
    val nextIndex = (currentIndex + 1) % ThemeSelector.Themes.length
    setTheme(ThemeSelector.Themes(nextIndex))
  }

  lazy val linkModifier = Rx(href := s"themes/${currentTheme().toLowerCase}.css").auto

  def renderTag(md: ModifierT*): TagT = {
    link(rel := "stylesheet", linkModifier, md)
  }
}
