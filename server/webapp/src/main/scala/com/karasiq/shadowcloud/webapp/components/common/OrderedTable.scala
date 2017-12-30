package com.karasiq.shadowcloud.webapp.components.common
import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.webapp.components.common.OrderedTable.Column

object OrderedTable {
  final case class Column[T, V](name: Modifier, extract: T ⇒ V, render: T ⇒ Modifier)(implicit val ord: Ordering[V])

  final case class Builder[T](items: Rx[Seq[T]], columns: Seq[Column[T, Any]] = Nil,
                              rowModifiers: T ⇒ Modifier = (_: T) ⇒ Bootstrap.noModifier,
                              filterItem: (T, String) ⇒ Boolean = (i: T, f: String) ⇒ i.toString.contains(f)) {

    def withItems(items: Rx[Seq[T]]) = copy(items)
    def withItems(items: T*) = copy(Var(items))
    def withColumns(columns: Column[T, _]*) = copy(columns = columns.asInstanceOf[Seq[Column[T, Any]]])
    def withRowModifiers(rowModifiers: T ⇒ Modifier) = copy(rowModifiers = rowModifiers)
    def withFilter(filterItem: (T, String) ⇒ Boolean) = copy(filterItem = filterItem)
  }

  def apply[T](items: Rx[Seq[T]]) = Builder(items)
  def apply[T](items: T*) = Builder(Var(items))

  def apply[T](_items: Rx[Seq[T]])(_columns: Column[T, _]*)
              (_rowModifiers: T ⇒ Modifier = (_: T) ⇒ Bootstrap.noModifier,
               _filterItem: (T, String) ⇒ Boolean = (i: T, f: String) ⇒ i.toString.contains(f)): OrderedTable[T] = {

    new OrderedTable[T] {
      val items = _items

      val columns = Var(_columns.asInstanceOf[Seq[Column[T, Any]]])
      val sortByColumn = Var(_columns.head.asInstanceOf[Column[T, Any]])
      val reverseOrdering = Var(false)

      val filter = Var("")
      def filterItem(item: T, filter: String): Boolean = _filterItem(item, filter)
      def rowModifiers(item: T): Modifier = _rowModifiers(item)
    }
  }
}

abstract class OrderedTable[T] extends BootstrapHtmlComponent {
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
      GridSystem.mkRow(Form(FormInput.text("", filter.reactiveInput)), Rx(items().length <= 1).reactiveHide),
      GridSystem.mkRow(PagedTable(heading, content).renderTag(md:_*))
    )
  }
}
