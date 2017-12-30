package com.karasiq.shadowcloud.webapp.components.common
import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.webapp.components.common.FilteredOrderedTable.Column

object FilteredOrderedTable {
  final case class Column[T, V](name: Modifier, extract: T ⇒ V, render: T ⇒ Modifier)(implicit val ord: Ordering[V])
  type GenColumn[T] = Column[T, Any]

  final case class Builder[T](items: Rx[Seq[T]], columns: Seq[GenColumn[T]] = Nil,
                              rowModifiers: T ⇒ Modifier = (_: T) ⇒ Bootstrap.noModifier,
                              filterItem: (T, String) ⇒ Boolean = (i: T, f: String) ⇒ i.toString.contains(f)) extends BootstrapHtmlComponent {

    def withItems(items: Rx[Seq[T]]) = copy(items)
    def withItems(items: T*) = copy(Var(items))
    def withColumns(columns: Column[T, _]*) = copy(columns = columns.asInstanceOf[Seq[GenColumn[T]]])
    def withRowModifiers(rowModifiers: T ⇒ Modifier) = copy(rowModifiers = rowModifiers)
    def withFilter(filterItem: (T, String) ⇒ Boolean) = copy(filterItem = filterItem)

    def createTable(): FilteredOrderedTable[T] = {
      require(columns.nonEmpty, "Columns is empty")

      new FilteredOrderedTable[T] {
        val items = Builder.this.items

        val columns = Var(Builder.this.columns)
        val sortByColumn = Var(Builder.this.columns.head)
        val reverseOrdering = Var(false)

        val filter = Var("")
        def filterItem(item: T, filter: String): Boolean = Builder.this.filterItem(item, filter)
        def rowModifiers(item: T): Modifier = Builder.this.rowModifiers(item)
      }
    }

    def renderTag(md: ModifierT*): TagT = {
      val table = createTable()
      table.renderTag(md:_*)
    }
  }

  def apply[T](items: Rx[Seq[T]]): Builder[T] = new Builder(items)
  def apply[T](items: T*): Builder[T] = new Builder(Var(items))
}

abstract class FilteredOrderedTable[T] extends BootstrapHtmlComponent {
  def items: Rx[Seq[T]]

  def columns: Rx[Seq[Column[T, Any]]]
  def sortByColumn: Var[Column[T, Any]]
  def reverseOrdering: Var[Boolean]

  def filter: Var[String]
  def filterItem(item: T, filter: String): Boolean

  def rowModifiers(item: T): Modifier

  def setOrdering(column: Column[T, Any]): Unit = {
    if (sortByColumn.now == column) reverseOrdering() = !reverseOrdering.now
    else sortByColumn() = column
  }

  def renderTag(md: ModifierT*): TagT = {
    val heading = Rx {
      val columns = this.columns()
      columns.map { column ⇒
        val icon = Rx[Frag] {
          if (sortByColumn() == column) Icon.faFw(if (reverseOrdering()) "caret-down" else "caret-up")
          else Bootstrap.noContent
        }

        span(icon, column.name, cursor.pointer, onclick := Callback.onClick(_ ⇒ setOrdering(column)))
      }
    }

    val content = Rx {
      val columns = this.columns()
      val items = this.items()

      val filter = this.filter()
      val selectedCol = this.sortByColumn()

      val ordering = if (reverseOrdering()) selectedCol.ord.reverse else selectedCol.ord
      val sortedItems = items
        .filter(item ⇒ filterItem(item, filter))
        .sortBy(item ⇒ selectedCol.extract(item))(ordering)

      sortedItems.map(item ⇒ TableRow(columns.map(col ⇒ col.render(item)), rowModifiers(item)))
    }

    div(
      GridSystem.mkRow(Form(FormInput.text("", filter.reactiveInput)), Rx(items().lengthCompare(1) <= 0).reactiveHide),
      GridSystem.mkRow(PagedTable(heading, content).renderTag(md:_*))
    )
  }
}
