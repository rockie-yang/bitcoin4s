package me.hongchao.bitcoin4s

import java.nio.{ByteBuffer, ByteOrder}

import scodec.Attempt
import scodec.bits.BitVector

package object Utils {
  implicit class Rich[T](value: T) {
    def toHex: String = "%02x".format(value)
  }

  implicit class RichSeqByte(bytes: Seq[Byte]) {
    def toHex: String = bytes.map(_.toHex).mkString

    def toBoolean(): Boolean = bytes.reverse match {
      case head +: tail if head == 0x80.toByte =>
        tail.exists(_ != 0)
      case other =>
        other.exists(_ != 0)
    }
  }

  implicit class RichBoolean(b: Boolean) {
    def flatOption[T](f: => Option[T]): Option[T] = {
      if (b) f else None
    }

    def option[T](f: => T): Option[T] = {
      if (b) Some(f) else None
    }
  }

  implicit class RichAttemptByteVector(attemptByteVector: Attempt[BitVector]) {
    def toBytes(): Array[Byte] = {
      attemptByteVector.toEither match {
        case Left(error) =>
          throw new RuntimeException(error.messageWithContext)
        case Right(v) =>
          v.toByteArray
      }
    }
  }

  implicit class RichSeq[T](seq: Seq[T]) {
    def forceTake(n: Int): Seq[T] = {
      val maybeNElements = seq.take(n)
      (maybeNElements.length == n)
        .option(maybeNElements)
        .getOrElse(throw new RuntimeException(s"Not enough elements in $seq to take $n."))
    }


    def takeOpt(n: Int): Option[Seq[T]] = {
      val maybeNElements = seq.take(n)
      (maybeNElements.length == n).option(maybeNElements)
    }

    def dropOpt(n: Int): Option[Seq[T]] = {
      val (maybeNElements, rest) = seq.splitAt(n)
      (maybeNElements.length == n).option(rest)
    }

    def splitAtOpt(n: Int): Option[(Seq[T], Seq[T])] = {
      val (maybeNElements, rest) = seq.splitAt(n)
      (maybeNElements.length == n).option((maybeNElements, rest))
    }

    def splitAtEither[Err](n: Int, error: Err): Either[Err, (Seq[T], Seq[T])] = {
      val (maybeNElements, rest) = seq.splitAt(n)
      if (maybeNElements.length == n) {
        Right((maybeNElements, rest))
      } else {
        Left(error)
      }
    }

    def headEither[Err](error: Err): Either[Err, T] = {
      seq.headOption.map(head => Right(head)).getOrElse(Left(error))
    }
  }

  def bytesToUInt8(bytes: Seq[Byte]): Int = {
    bytes.head.toShort & 0xFF
  }

  def bytesToUInt16(bytes: Seq[Byte]): Int = {
    val byteBuffer = ByteBuffer.wrap(bytes.toArray).order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.getShort & 0xFFFF
  }

  def bytesToUInt32(bytes: Seq[Byte]): Int = {
    val byteBuffer = ByteBuffer.wrap(bytes.toArray).order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.getInt & 0xFFFFFFFF
  }

  def uint32ToBytes(input: Long): Seq[Byte] = uint32ToBytes(input, ByteOrder.LITTLE_ENDIAN)

  def uint32ToBytes(input: Long, order: ByteOrder): Seq[Byte] = {
    val bin = new Array[Byte](4)
    val buffer = ByteBuffer.wrap(bin).order(order)
    buffer.putInt((input & 0xffffffff).toInt)
    bin
  }

  def uInt64ToBytes(input: Long): Seq[Byte] = uInt64ToBytes(input, ByteOrder.LITTLE_ENDIAN)

  def uInt64ToBytes(input: Long, order: ByteOrder): Seq[Byte] = {
    val bin = new Array[Byte](8)
    val buffer = ByteBuffer.wrap(bin).order(order)
    buffer.putLong(input)
    bin
  }

}
