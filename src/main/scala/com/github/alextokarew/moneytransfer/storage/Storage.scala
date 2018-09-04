package com.github.alextokarew.moneytransfer.storage

/**
  * Abstract key-value storage
  * @tparam ID primary key type
  * @tparam E entity (value) type
  */
trait Storage[ID, E] {
  /**
    * Puts an entity to the storage.
    * @param id primary key of the entity to put
    * @param entity an entity to put into the storage
    * @return the entity
    */
  def put(id: ID, entity: E): E

  /**
    * Puts an entity to the storage only if the record with specified id does not exist. Otherwise returns an existing entity
    * @param id primary key of the entity to put
    * @param entity an entity to put into the storage
    * @return passed entity if the record wasn't create before, or an existing entity
    */
  def putIfAbsent(id: ID, entity: E): E

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
}


/**
  * An in-memory implementation of the storage using mutable map
  * @tparam ID primary key type
  * @tparam E entity (value) type
  */
class InMemoryStorage[ID, E] extends Storage[ID, E] {

  private val map = new java.util.concurrent.ConcurrentHashMap[ID, E]()

  override def put(id: ID, entity: E): E = {
    map.put(id, entity)
    entity
  }

  override def putIfAbsent(id: ID, entity: E): E = map.putIfAbsent(id, entity)

  override def exists(id: ID): Boolean = map.contains(id)

  override def get(id: ID): Option[E] = Option(map.get(id))
}

object InMemoryStorage {
  def apply[ID, E]: InMemoryStorage[ID, E] = new InMemoryStorage()
}
