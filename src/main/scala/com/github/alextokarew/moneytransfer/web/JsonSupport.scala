package com.github.alextokarew.moneytransfer.web

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.alextokarew.moneytransfer.domain._
import com.github.alextokarew.moneytransfer.validation.ValidationError
import com.github.alextokarew.moneytransfer.web.request.CreateAccountRequest
import spray.json.{DefaultJsonProtocol, JsString, JsValue, JsonFormat, RootJsonFormat, deserializationError}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val validationErrorFormat: RootJsonFormat[ValidationError] = jsonFormat1(ValidationError)

  implicit object AccountIdFormat extends JsonFormat[AccountId] {
    def write(x: AccountId): JsString = {
      require(x ne null)
      JsString(x.value)
    }
    def read(value: JsValue): AccountId = value match {
      case JsString(x) => AccountId(x)
      case x => deserializationError("Expected String as JsString, but got " + x)
    }
  }

  implicit val transferFailedFormat: RootJsonFormat[Failed] = jsonFormat1(Failed)

  implicit object TransferStatusFormat extends JsonFormat[TransferStatus] {
    def write(status: TransferStatus): JsValue = {
      require(status ne null)
      status match {
        case obj @ (Processing | Succeded) => JsString(obj.toString)
        case failed: Failed => transferFailedFormat.write(failed)
      }
    }

    def read(json: JsValue): TransferStatus =
      deserializationError("deserialization of transfer status is not supported (yet)")
  }

  implicit val accountFormat: RootJsonFormat[Account] = jsonFormat3(Account)
  implicit val balanceFormat: RootJsonFormat[Balance] = jsonFormat1(Balance)
  implicit val transferRequestFormat: RootJsonFormat[TransferRequest] = jsonFormat5(TransferRequest)
  implicit val transferFormat: RootJsonFormat[Transfer] = jsonFormat5(Transfer)

  implicit val createAccountRequestFormat: RootJsonFormat[CreateAccountRequest] = jsonFormat4(CreateAccountRequest)
}
