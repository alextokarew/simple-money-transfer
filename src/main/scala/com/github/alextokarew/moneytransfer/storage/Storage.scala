package com.github.alextokarew.moneytransfer.storage

import com.github.alextokarew.moneytransfer.domain.{Account, AccountId}

/**
  * Abstract key-value storage
  * @tparam ID primary key type
  * @tparam E entity (value) type
  */
trait Storage[ID, E] {
  /**
    * Puts a new entity to the storage.
    * @param id primary key of the entity to put
    * @param entity an entity to put into the storage
    * @return
    */
  def put(id: ID, entity: E): this.type

  /**
    * Retrieves an entity by the specified key.
    * @param id an entity key
    * @return an entity or none if the key was not found
    */
  def get(id: ID): Option[E]
}


/**
  * An in-memory implementation of the storage
  * @tparam ID primary key type
  * @tparam E entity (value) type
  */
class InMemoryStorage[ID, E](map: Map[ID, E]) extends Storage[ID, E] {

  override def put(id: ID, entity: E): InMemoryStorage.this.type = {
    InMemoryStorage(map + (id -> entity))
  }

  override def get(id: ID): Option[E] = map.get(id)
}

object InMemoryStorage {
  def apply[ID, E](map: Map[ID, E]): InMemoryStorage[ID, E] = new InMemoryStorage(map)
  val empty: InMemoryStorage[Nothing, Nothing] = new InMemoryStorage[Nothing, Nothing](Map.empty)
}
