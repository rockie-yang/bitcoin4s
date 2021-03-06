package me.hongchao.bitcoin4s.script

import cats.data.StateT
import com.typesafe.scalalogging.StrictLogging
import me.hongchao.bitcoin4s.script.Interpreter.{InterpreterContext, InterpreterErrorHandler}
import scala.language.implicitConversions
import simulacrum._
import cats.implicits._

@typeclass trait InterpretableOp[A <: ScriptOpCode] extends StrictLogging {
  def interpret(opCode: A): InterpreterContext[Option[Boolean]]

  def interpret(opCode: A, verbose: Boolean): InterpreterContext[Option[Boolean]] = {
    if (verbose) {
      for {
        oldState <- StateT.get[InterpreterErrorHandler, InterpreterState]
        newContext <- interpret(opCode)
      } yield {
        logger.info(
          s"""
             |
             |Script: ${opCode +: oldState.currentScript}
             |stack: ${oldState.stack}
             |altstack: ${oldState.altStack}
             |executingStage: ${oldState.scriptExecutionStage}
             |
           """.stripMargin)
        newContext
      }
    } else {
      interpret(opCode)
    }
  }
}
