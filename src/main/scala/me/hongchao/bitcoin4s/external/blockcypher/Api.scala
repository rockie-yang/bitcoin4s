package me.hongchao.bitcoin4s.external.blockcypher

import java.time.ZonedDateTime

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import me.hongchao.bitcoin4s.crypto.Hash
import me.hongchao.bitcoin4s.external.HttpSender
import me.hongchao.bitcoin4s.external.blockcypher.Api._
import me.hongchao.bitcoin4s.script.Parser
import me.hongchao.bitcoin4s.transaction.structure.{OutPoint, Hash => ScodecHash}
import me.hongchao.bitcoin4s.transaction.{Tx, TxId, TxIn, TxOut}
import play.api.libs.json.{JsError, JsSuccess, Json}
import scodec.bits.ByteVector
import tech.minna.playjson.macros.json

import scala.concurrent.{ExecutionContext, Future}

// https://www.blockcypher.com/dev/bitcoin/#transaction-api
trait ApiInterface {
  def getTransaction(txId: TxId): Future[Option[Transaction]]
}

class Api(httpSender: HttpSender)(
  implicit
  ec: ExecutionContext,
  materializer: Materializer
) extends ApiInterface {
  override def getTransaction(txId: TxId): Future[Option[Transaction]] = {
    httpSender(HttpRequest(uri = rawTxUrl(txId))).flatMap { response =>
      if (response.status.isSuccess()) {
        transactionUnmarshaller(response.entity).map(Option.apply _)
      } else {
        Future.successful(None)
      }
    }
  }
}

class CachedApi(api: Api)(
  implicit
  system: ActorSystem,
  ec: ExecutionContext,
) extends ApiInterface {
  implicit val transactionCacheActor = system.actorOf(TransactionCacheActor.props())

  override def getTransaction(txId: TxId): Future[Option[Transaction]] = {
    TransactionCacheActor.getTransaction(txId).flatMap { cachedTx =>
      cachedTx match {
        case None =>
          api.getTransaction(txId).map { maybeFreshTx =>
            maybeFreshTx match {
              case Some(freshTx) =>
                TransactionCacheActor.setTransaction(txId, freshTx)
                Some(freshTx)
              case None =>
                None
            }
          }
        case Some(tx) =>
          Future.successful(Some(tx))
      }
    }
  }
}


object Api {

  @json case class TransactionInput(
    prev_hash: String,
    output_index: Int,
    script: Option[String],
    output_value: Long,
    sequence: Long,
    script_type: String,
    age: Long,
    witness: Option[List[String]] = None
  ) {
    val prevTxHash = ScodecHash(ByteVector(Hash.fromHex(prev_hash)))
    def toTxIn = TxIn(
      previous_output = OutPoint(prevTxHash, output_index),
      sig_script = script.map { s =>
        ByteVector(Hash.fromHex(s))
      }.getOrElse(ByteVector.empty),
      sequence = sequence
    )
  }

  @json case class TransactionOutput(
    value: Long,
    script: String,
    spent_by: Option[String],
    script_type: String
  ) {
    def toTxOut = TxOut(
      value = value,
      pk_script = ByteVector(Parser.parse(Hash.fromHex(script)).flatMap(_.bytes))
    )
  }

  @json case class Transaction(
    block_hash: String,
    block_height: Long,
    block_index: Int,
    hash: String,
    addresses: Seq[String],
    total: Long,
    fees: Long,
    size: Long,
    confirmed: ZonedDateTime,
    received: ZonedDateTime,
    ver: Int,
    lock_time: Long = 0,
    double_spend: Boolean,
    vin_sz: Int,
    vout_sz: Int,
    confirmations: Long,
    confidence: Int,
    inputs: Seq[TransactionInput],
    outputs: Seq[TransactionOutput]
  ) {
    def toTx = Tx(
      version = ver,
      tx_in = inputs.map(_.toTxIn).toList,
      tx_out = outputs.map(_.toTxOut).toList,
      lock_time = lock_time
    )
  }

  protected def rawTxUrl(txId: TxId) = Uri(s"https://api.blockcypher.com/v1/btc/main/txs/${txId.value}")

  def parseTransaction(raw: String): Transaction = {
    Json.fromJson[Transaction](Json.parse(raw)) match {
      case JsSuccess(value, _) =>
        value
      case JsError(e) =>
        throw new RuntimeException(s"Failed to parse transaction $e")
    }
  }

  protected implicit val transactionUnmarshaller = Unmarshaller.stringUnmarshaller.map(parseTransaction)
}