package com.karasiq.shadowcloud.webapp.components.themes

import scala.language.postfixOps

import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.webapp.utils.LSBind

object ThemeSelector {
  val Themes = Vector(
    "Default", "Cerulean", "Cosmo", "Cyborg", "Darkly", "Flatly", "Journal", "Lumen", "Paper", "Readable",
    "Sandstone", "Simplex", "Slate", "Spacelab", "Superhero", "United", "Yeti"
  )

  lazy val CurrentTheme = LSBind("bootstrap-theme", Themes.head)

  def apply(): ThemeSelector = {
    new ThemeSelector(Themes, CurrentTheme)
  }
}

class ThemeSelector(themes: IndexedSeq[String], currentTheme: Var[String])
  extends BootstrapHtmlComponent {

  def setTheme(theme: String): Unit = {
    currentTheme() = theme
  }

  def nextTheme(): Unit = {
    val currentIndex = themes.indexOf(currentTheme.now)
    val nextIndex = (currentIndex + 1) % themes.length
    setTheme(themes(nextIndex))
  }

  lazy val linkModifier = Rx(href := s"themes/${currentTheme().toLowerCase}.css").auto

  def renderTag(md: ModifierT*): TagT = {
    link(rel := "stylesheet", linkModifier, md)
  }
}
