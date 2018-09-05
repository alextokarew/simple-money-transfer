package com.github.alextokarew.moneytransfer.storage

/**
  * Abstract key-value storage
  * @tparam ID primary key type
  * @tparam E entity (value) type
  */
trait Storage[ID, E] {

  /**
    * Puts a new entity to the storage. If an entity under the specified key is present returns an existing entity
    * @param id primary key of the entity to put
    * @param entity an entity to put into the storage
    * @param onInsert optional callback that is called when a new entry is added to the storage
    * @return passed entity if the record wasn't create before, or an existing entity
    */
  def putIfAbsent(id: ID, entity: E, onInsert: Option[E => Unit] = None): E

  /**
    * Checks whether a record by the specified key exists in the storage.
    * @param id an id to check
    * @return true if the record exists, false otherwise
    */
  def exists(id: ID): Boolean

  /**
    * Retrieves an entity by the specified key.
    * @param id an entity key
    * @return an entity or none if the key was not found
    */
  def get(id: ID): Option[E]

  /**
    * Updates a value under specified key. If the value does not exist returns None
    * @param id key to update the value
    * @param doUpdate update operation that accepts previous value and returns an updated value
    * @return an updated entity or none if the key was not found
    */
  def update(id: ID, doUpdate: E => E): Option[E]
}


/**
  * An in-memory implementation of the storage using mutable map
  * @tparam ID primary key type
  * @tparam E entity (value) type
  */
class InMemoryStorage[ID, E] extends Storage[ID, E] {

  private val map = new java.util.concurrent.ConcurrentHashMap[ID, E]()

  override def putIfAbsent(id: ID, entity: E, onInsert: Option[E => Unit] = None): E = {
    val result = map.putIfAbsent(id, entity)
    if (result == null) {
      onInsert.foreach(_(entity))
      entity
    } else {
      result
    }
  }

  override def exists(id: ID): Boolean = map.containsKey(id)

  override def get(id: ID): Option[E] = Option(map.get(id))

  override def update(id: ID, doUpdate: E => E): Option[E] = Option(map.computeIfPresent(id, (_, v) => doUpdate(v)))
}

object InMemoryStorage {
  def apply[ID, E](): InMemoryStorage[ID, E] = new InMemoryStorage()
}
