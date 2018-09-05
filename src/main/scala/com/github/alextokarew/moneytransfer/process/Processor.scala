package com.github.alextokarew.moneytransfer.process

import com.github.alextokarew.moneytransfer.domain.Transfer

trait Processor {
  def enqueue(transfer: Transfer)
}
