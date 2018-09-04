package com.github.alextokarew.moneytransfer.validation

object Validation {
  type Check[T] = T => List[Error]
  type Valid[T] = Either[List[Error], T]

  def validate[T](instance: T)(checks: Check[T]*): Valid[T] = {
    val errors = checks.flatMap(check => check(instance))
    if (errors.isEmpty) {
      Right(instance)
    } else {
      Left(errors.toList)
    }
  }

  def check[T](predicate: T => Boolean, errorMsg: String): Check[T] =
    predicate.andThen(checkPassed => if (checkPassed) Nil else List(Error(errorMsg)))

}

case class Error(error: String)