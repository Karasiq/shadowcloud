package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object TableView {
  def apply(table: Metadata.Table)(implicit context: AppContext): TableView = {
    new TableView(table)
  }
}

class TableView(table: Metadata.Table)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[Modifier](
      context.locale.name,
      context.locale.value
    )

    val rows = table.values.toVector.sortBy(_._1).map { case (name, Metadata.Table.Values(values)) ⇒
      TableRow(Seq(name, values.mkString("\n")))
    }

    PagedTable.static(heading, rows, 10).renderTag(md:_*)
  }
}

