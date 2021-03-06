package me.hongchao.bitcoin4s.external.blockcypher

import akka.actor.Cancellable
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.StrictLogging
import me.hongchao.bitcoin4s.crypto.Hash
import me.hongchao.bitcoin4s.external.blockcypher.Api.{Transaction, TransactionInput, TransactionOutput}
import me.hongchao.bitcoin4s.script.SigVersion.{SIGVERSION_BASE, SIGVERSION_WITNESS_V0}
import me.hongchao.bitcoin4s.script._
import me.hongchao.bitcoin4s.transaction.TxId
import org.spongycastle.util.encoders.Hex

import scala.util.control.Exception.allCatch
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import cats.implicits._
import me.hongchao.bitcoin4s.ApiModels.InterpreterResultOut.NoResult
import me.hongchao.bitcoin4s.ApiModels.{InterpreterOutcome, InterpreterResultOut, InterpreterStateOut, TransactionInputNotFound}

class Service(api: ApiInterface)(
  implicit
  ec: ExecutionContext,
  materializer: Materializer
) extends StrictLogging {

  def getTransaction(txId: TxId): Future[Option[Transaction]] = {
    logger.info(s"Fetching transaction $txId")
    api.getTransaction(txId)
  }

  def getTransactionInput(txId: TxId, inputIndex: Int): Future[Option[TransactionInput]] = {
    getTransaction(txId).map { maybeTransaction =>
      maybeTransaction.flatMap { transaction =>
        allCatch.opt(transaction.inputs(inputIndex))
      }
    }
  }

  def getTransactionOutput(txId: TxId, outputIndex: Int): Future[Option[TransactionOutput]] = {
    getTransaction(txId).map { maybeTransaction =>
      maybeTransaction.flatMap { transaction =>
        allCatch.opt(transaction.outputs(outputIndex))
      }
    }
  }

  def interpret(
    txId: TxId,
    inputIndex: Int,
    maybeSteps: Option[Int] = None,
    flags: Seq[ScriptFlag] = Seq(ScriptFlag.SCRIPT_VERIFY_P2SH, ScriptFlag.SCRIPT_VERIFY_WITNESS)
  ): Future[Option[InterpreterOutcome]] = {
    getTransaction(txId).flatMap { maybeSpendingTx =>

      val maybeTxInput = maybeSpendingTx.flatMap { spendingTx =>
        allCatch.opt(spendingTx.inputs(inputIndex))
      }

      maybeTxInput match {
        case Some(txInput) =>
          val prevId = TxId(txInput.prevTxHash.toString)
          val outputIndex = txInput.output_index

          getTransactionOutput(prevId, outputIndex).map { maybeTxOutput =>
            maybeTxOutput.map { txOutout =>
              val scriptSig = txInput.script.map { s =>
                Parser.parse(Hash.fromHex(s))
              }.getOrElse(Seq.empty)

              val scriptPubKey = Parser.parse(Hash.fromHex(txOutout.script))

              val witnessesStack = txInput.witness.map { rawWitnesses =>
                rawWitnesses.reverse.flatMap { rawWitness =>
                  allCatch.opt(Hex.decode(rawWitness).toSeq).map(ScriptConstant.apply)
                }
              }

              val amount = txOutout.value
              val sigVersion = if (witnessesStack.isEmpty) SIGVERSION_BASE else SIGVERSION_WITNESS_V0

              val initialState = InterpreterState(
                scriptPubKey = scriptPubKey,
                scriptSig = scriptSig,
                scriptWitnessStack = witnessesStack,
                flags = flags,
                transaction = maybeSpendingTx.get.toTx, // Has to exist
                inputIndex = inputIndex,
                amount = amount,
                sigVersion = sigVersion
              )

              val outcome = Interpreter.create(verbose = false, maybeSteps).run(initialState)

              logger.info(s"Interpreter finished with $outcome")

              outcome.map {
                case (finalState@_, interpretedResult) =>
                  InterpreterOutcome(
                    result = InterpreterResultOut.fromInterpreterResult(interpretedResult),
                    state = InterpreterStateOut.fromInterpreterState(finalState)
                  )
              } match {
                case Left(e) =>
                  throw e
                case Right(result) =>
                  result
              }
            }
          }

        case None =>
          Future.successful(None)
      }
    }
  }

  def interpretStream(txId: TxId, inputIndex: Int): Source[InterpreterOutcome, Cancellable] = {
    case object Tick

    Source
      .tick(1.second, 2.seconds, Tick)
      .zipWithIndex
      .mapAsync(1) {
        case (_, step) =>
          interpret(txId, inputIndex, Some(step.toInt))
      }
      .map { maybeInterpreterOutcome =>
        maybeInterpreterOutcome.getOrElse {
          throw TransactionInputNotFound
        }
      }
      .takeWhile(_.result == NoResult, inclusive = true)
  }
}
