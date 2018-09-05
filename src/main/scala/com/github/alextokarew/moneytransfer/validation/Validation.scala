package com.github.alextokarew.moneytransfer.validation

object Validation {
  type Check[T] = T => List[ValidationError]
  type Valid[T] = Either[List[ValidationError], T]

  def validate[T](instance: T)(checks: Check[T]*): Valid[T] = {
    val errors = checks.flatMap(check => check(instance))
    if (errors.isEmpty) {
      Right(instance)
    } else {
      Left(errors.toList)
    }
  }

  def check[T](predicate: T => Boolean, errorMsg: String): Check[T] =
    predicate.andThen(checkPassed => if (checkPassed) Nil else List(ValidationError(errorMsg)))

}

case class ValidationError(errorMsg: String)