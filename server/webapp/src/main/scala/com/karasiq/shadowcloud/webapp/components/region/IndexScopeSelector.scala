package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons, DateInput}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

object IndexScopeSelector {
  def apply()(implicit context: AppContext): IndexScopeSelector = {
    new IndexScopeSelector
  }

  def forContext(folderContext: FolderContext)(implicit context: AppContext): IndexScopeSelector = {
    val scopeSelector = apply()
    scopeSelector.selectedScope.foreach(folderContext.scope() = _)
    scopeSelector
  }
}

class IndexScopeSelector(implicit context: AppContext) extends BootstrapHtmlComponent {
  val opened    = Var(false)
  val dateInput = DateInput(context.locale.indexSnapshotDate)

  val selectedScope = Rx[IndexScope] {
    if (opened()) {
      dateInput.selectedDate() match {
        case Some(date) ⇒
          IndexScope.UntilTime(DateInput.toTimestamp(date))

        case None ⇒
          IndexScope.default
      }
    } else {
      IndexScope.default
    }
  }

  def renderTag(md: ModifierT*): TagT = {
    val link = Rx {
      val isOpened = opened()
      val icon     = if (isOpened) AppIcons.historyScope else AppIcons.currentScope
      val style    = if (isOpened) Bootstrap.textStyle.warning else Bootstrap.textStyle.success
      val title    = if (isOpened) context.locale.historyScope else context.locale.currentScope
      AppComponents.iconLink(title, icon, style, onclick := Callback.onClick(_ ⇒ opened() = !opened.now))
    }

    val content = Rx[Frag](if (opened()) Form(dateInput) else Bootstrap.noContent)

    div(
      link,
      content,
      md
    )
  }
}
