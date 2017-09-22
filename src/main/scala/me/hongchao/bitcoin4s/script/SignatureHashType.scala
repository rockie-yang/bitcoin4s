package me.hongchao.bitcoin4s.script

import me.hongchao.bitcoin4s.Utils._

case class SignatureHashType(value: Int) {

  def SIGHASH_ANYONECANPAY(): Boolean = {
    (value & 0x80) == 0x80
  }

  def SIGHASH_ALL(): Boolean = {
    SIGHASH_ANYONECANPAY()
      .option((value & 0x7f) == 0x01)
      .getOrElse(value == 0x01)
  }

  def SIGHASH_NONE(): Boolean = {
    SIGHASH_ANYONECANPAY()
      .option((value & 0x7f) == 0x02)
      .getOrElse(value == 0x02)
  }

  def SIGHASH_SINGLE(): Boolean = {
    SIGHASH_ANYONECANPAY()
      .option((value & 0x7f) == 0x03)
      .getOrElse(value == 0x03)
  }
}