package me.hongchao.bitcoin4s

import java.nio.{ByteBuffer, ByteOrder}

import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.Size
import eu.timepit.refined.generic.Equal
import shapeless.nat._

package object Utils {
  implicit class Rich[T](value: T) {
    def toHex: String = "%02x".format(value)
  }

  implicit class RichSeqByte(bytes: Seq[Byte]) {
    def toHex: String = bytes.map(_.toHex).mkString
  }

  implicit class RichBoolean(b: Boolean) {
    def option[T](f: => T): Option[T] = {
      if (b) Some(f) else None
    }
  }

  implicit class RichSeq[T](seq: Seq[T]) {
    def takeOpt(n: Int): Option[Seq[T]] = {
      val maybeNElements = seq.take(n)
      if (maybeNElements.length == n) {
        Some(maybeNElements)
      } else {
        None
      }
    }

    def dropOpt(n: Int): Option[Seq[T]] = {
      val (maybeNElements, rest) = seq.splitAt(n)
      if (maybeNElements.length == n) {
        Some(rest)
      } else {
        None
      }
    }

    def splitAtOpt(n: Int): Option[(Seq[T], Seq[T])] = {
      val (maybeNElements, rest) = seq.splitAt(n)
      if (maybeNElements.length == n) {
        Some((maybeNElements, rest))
      } else {
        None
      }
    }
  }

  def toUInt8(bytes: Seq[Byte] Refined Size[Equal[_1]]): Int = {
    bytes.value.head.toShort
  }

  def toUInt16(bytes: Seq[Byte] Refined Size[Equal[_2]]): Int = {
    val byteBuffer = ByteBuffer.wrap(bytes.value.toArray).order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.getShort & 0xFFFF
  }

  def toUInt32(bytes: Seq[Byte] Refined Size[Equal[_4]]): Long = {
    val byteBuffer = ByteBuffer.wrap(bytes.value.toArray).order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.getInt & 0xFFFFFFFFL
  }
}
