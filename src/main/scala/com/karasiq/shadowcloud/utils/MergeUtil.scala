package com.karasiq.shadowcloud.utils

import scala.collection.mutable
import scala.language.postfixOps

object MergeUtil {
  //------------------------------------------
  // TYPES
  //------------------------------------------
  sealed trait State[+T]
  object State {
    sealed trait SingleSide[+T] extends State[T] {
      def value: T
    }
    case class Left[+T](value: T) extends State[T] with SingleSide[T]
    case class Right[+T](value: T) extends State[T] with SingleSide[T]
    case class Equal[+T](value: T) extends State[T]
    case class Conflict[+T](left: T, right: T) extends State[T]
  }
  import State._
  
  type Decider[V] = PartialFunction[State[V], Option[V]]
  type SplitDecider[V] = Equal[V] ⇒ Option[State[V]]

  //------------------------------------------
  // COMPARE
  //------------------------------------------
  def compareMaps[K <: AnyRef, V](left: Map[K, V], right: Map[K, V]): Map[K, State[V]] = {
    val newMap = mutable.AnyRefMap[K, State[V]]()
    newMap ++= left.mapValues(Left(_))
    right.foreach { case (key, rightV) ⇒
      val mergedValue = newMap.get(key) match {
        case Some(Left(leftV)) ⇒
          if (leftV == rightV) Equal(leftV) else Conflict(leftV, rightV)

        case _ ⇒
          Right(rightV)
      }
      newMap += key → mergedValue
    }
    newMap.toMap
  }

  def compareSets[V](left: Set[V], right: Set[V]): Set[State[V]] = {
    val newSet = mutable.Set[State[V]]()
    newSet ++= left.map(Left(_))
    right.foreach { value ⇒
      val mergedValue = if (newSet.contains(Left(value))) Equal(value) else Right(value)
      newSet -= Left(value)
      newSet += mergedValue
    }
    newSet.toSet
  }

  //------------------------------------------
  // MERGE
  //------------------------------------------
  def mergeMaps[K <: AnyRef, V](left: Map[K, V], right: Map[K, V], decider: Decider[V]): Map[K, V] = {
    compareMaps(left, right).flatMap { case (key, value) ⇒ decider.orElse(Decider.default).apply(value).map((key, _)) }
  }

  def mergeByKey[K <: AnyRef, V](left: TraversableOnce[V], right: TraversableOnce[V], extractKey: V ⇒ K, decider: Decider[V]): Vector[V] = {
    mergeMaps(left.map(v ⇒ (extractKey(v), v)).toMap, right.map(v ⇒ (extractKey(v), v)).toMap, decider).values.toVector
  }

  def mergeSets[V](left: Set[V], right: Set[V], func: Decider[V]): Set[V] = {
    compareSets(left, right).flatMap(func.orElse(Decider.default).apply(_))
  }


  //------------------------------------------
  // SPLIT
  //------------------------------------------
  def splitSets[V](left: Set[V], right: Set[V], decider: SplitDecider[V]): (Set[V], Set[V]) = {
    val newLeft, newRight = Set.newBuilder[V]
    def pushElement: PartialFunction[State[V], Unit] = {
      case Left(value) ⇒
        newLeft += value

      case Right(value) ⇒
        newRight += value

      case Equal(value) ⇒
        newLeft += value
        newRight += value

      case Conflict(left, right) ⇒ // Not implemented on Set
        newLeft += left
        newRight += right
    }
    compareSets(left, right).foreach {
      case eq: Equal[_] ⇒
        decider(eq).foreach(pushElement)

      case state ⇒
        pushElement(state)
    }
    (newLeft.result(), newRight.result())
  }

  //------------------------------------------
  // DECIDERS
  //------------------------------------------
  object Decider {
    def default[V]: Decider[V] = {
      case Left(left) ⇒
        Some(left)

      case Right(right) ⇒
        Some(right)

      case Equal(value) ⇒
        Some(value)

      case Conflict(_, right) ⇒
        Some(right)
    }

    def diff[V]: Decider[V] = { case Equal(_) ⇒ None }
    def dropLeft[V]: Decider[V] = { case Left(_) ⇒ None }
    def dropRight[V]: Decider[V] = { case Right(_) ⇒ None }
    def dropUnique[V]: Decider[V] = { case Right(_) | Left(_) ⇒ None }

    def intersect[V]: Decider[V] = {
      case Equal(value) ⇒
        Some(value)

      case _ ⇒
        None
    }

    def keepLeft[V]: Decider[V] = {
      case Left(value) ⇒
        Some(value)

      case _ ⇒
        None
    }

    def keepRight[V]: Decider[V] = {
      case Right(value) ⇒
        Some(value)

      case _ ⇒
        None
    }
  }

  //------------------------------------------
  // SPLIT DECIDERS
  //------------------------------------------
  object SplitDecider {
    def dropDuplicates[V]: SplitDecider[V] = _ ⇒ None
    def keepBoth[V]: SplitDecider[V] = Some.apply
    def keepLeft[V]: SplitDecider[V] = state ⇒ Some(Left(state.value))
    def keepRight[V]: SplitDecider[V] = state ⇒ Some(Right(state.value))
  }
}
