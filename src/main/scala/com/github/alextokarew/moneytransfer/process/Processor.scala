package com.github.alextokarew.moneytransfer.process

import com.github.alextokarew.moneytransfer.domain.Transfer

trait Processor {
  /**
    * Enqueues a transfer operation to process.
    * @param transfer a transfer to process
    */
  def enqueue(transfer: Transfer)
}
