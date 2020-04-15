package dev.fuelyour.repositories

import dev.fuelyour.exceptions.ModelNotFoundException
import dev.fuelyour.tools.*
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.sqlclient.preparedQueryAwait
import io.vertx.sqlclient.SqlClient
import io.vertx.sqlclient.Tuple

/**
 * Simple mixin query for selecting all from a given table. The default implementation can be used with a call to the
 * `impl` function, or a different implementation may be provided.
 */
interface AllQuery<T: Any> {
  companion object {
    /**
     * Provides the default implementation for AllQuery. Can be used as follows. Note that constant values should be
     * passed for schema and table, as doing otherwise would risk sql injection.
     *
     * Example:
     *
     * ```
     * class ExampleRepo: AllQuery<Example> by AllQuery.impl(schema="public", table="example")
     * ```
     */
    inline fun <reified T: Any> impl(
      schema: String, table: String
    ): AllQuery<T> =
      AllQueryImpl(schema, table, type(), DeserializerImpl())
  }
  suspend fun all(connection: SqlClient): List<T>
}

/**
 * The default implementation for AllQuery. It returns all rows in the table.
 */
class AllQueryImpl<T: Any>(
  schema: String,
  table: String,
  private val type: FullType<T>,
  deserializer: Deserializer
): AllQuery<T>, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun all(connection: SqlClient): List<T> {
    return connection
      .preparedQueryAwait("select * from $tableName")
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        type.instantiate(json)
      }.requireNoNulls()
  }
}

/**
 * Simple mixin query for selecting a single row from a given table by id. The default implementation can be used with
 * a call to the `impl` function, or a different implementation may be provided.
 */
interface FindQuery<T: Any> {
  companion object {
    /**
     * Provides the default implementation for FindQuery. Can be used as follows. Note that constant values should be
     * passed for schema and table, as doing otherwise would risk sql injection.
     *
     * Example:
     *
     * ```
     * class ExampleRepo: FindQuery<Example> by FindQuery.impl(schema="public", table="example")
     * ```
     */
    inline fun <reified T: Any> impl(
      schema: String, table: String
    ): FindQuery<T> =
      FindQueryImpl(schema, table, type(), DeserializerImpl())
  }
  suspend fun find(id: String, connection: SqlClient): T
}

/**
 * The default implementation for FindQuery. It returns a single row in the table by id.
 */
class FindQueryImpl<T: Any>(
  schema: String,
  table: String,
  private val type: FullType<T>,
  deserializer: Deserializer
): FindQuery<T>, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun find(id: String, connection: SqlClient): T {
    return connection
      .preparedQueryAwait(
        "select * from $tableName where id = $1",
        Tuple.of(id)
      )
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        type.instantiate(json)
      }.firstOrNull()
      ?: throw ModelNotFoundException("No object found with ID", JsonArray(id))
  }
}

/**
 * Simple mixin query for inserting a single row into a given table. The default implementation can be used with a call
 * to the `impl` function, or a different implementation may be provided.
 */
interface InsertQuery<T: Any, R: Any> {
  companion object {
    /**
     * Provides the default implementation for InsertQuery. Can be used as follows. Note that constant values should be
     * passed for schema and table, as doing otherwise would risk sql injection.
     *
     * Example:
     *
     * ```
     * class ExampleRepo: InsertQuery<Example> by InsertQuery.impl(schema="public", table="example")
     * ```
     */
    inline fun <T: Any, reified R: Any> impl(
      schema: String, table: String
    ): InsertQuery<T, R> =
      InsertQueryImpl(
        schema,
        table,
        type(),
        SerializerImpl(),
        DeserializerImpl()
      )
  }
  suspend fun insert(toInsert: T, connection: SqlClient): R
}

/**
 * The default implementation for InsertQuery. It inserts a single row into the table.
 */
class InsertQueryImpl<T: Any, R: Any>(
  schema: String,
  table: String,
  private val type: FullType<R>,
  serializer: Serializer,
  deserializer: Deserializer
): InsertQuery<T, R>, Serializer by serializer, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun insert(toInsert: T, connection: SqlClient): R {
    val query = "insert into $tableName (data) values ($1::jsonb) returning *"
    val data = toInsert.serialize().also { it.remove("id") }
    return connection.preparedQueryAwait(query, Tuple.of(data))
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        type.instantiate(json)
      }.requireNoNulls().first()
  }
}

/**
 * Simple mixin query for updating a single from a given table based on id. The default implementation can be used with
 * a call to the `impl` function, or a different implementation may be provided.
 */
interface UpdateQuery<T: Any, R: Any> {
  companion object {
    /**
     * Provides the default implementation for UpdateQuery. Can be used as follows. Note that constant values should be
     * passed for schema and table, as doing otherwise would risk sql injection.
     *
     * Example:
     *
     * ```
     * class ExampleRepo: UpdateQuery<Example> by UpdateQuery.impl(schema="public", table="example")
     * ```
     */
    inline fun <T: Any, reified R: Any> impl(
      schema: String, table: String
    ): UpdateQuery<T, R> =
      UpdateQueryImpl(
        schema,
        table,
        type(),
        SerializerImpl(),
        DeserializerImpl()
      )
  }
  suspend fun update(id: String, toUpdate: T, connection: SqlClient): R
}

/**
 * The default implementation for UpdateQuery. It updates a single row in the table by id.
 */
class UpdateQueryImpl<T: Any, R: Any>(
  schema: String,
  table: String,
  private val type: FullType<R>,
  serializer: Serializer,
  deserializer: Deserializer
): UpdateQuery<T, R>, Serializer by serializer, Deserializer by deserializer {

  private val tableName = "$schema.$table"

  override suspend fun update(
    id: String,
    toUpdate: T,
    connection: SqlClient
  ): R {
    val query = "update $tableName set data = $1 where id = $2 returning *"
    val data = toUpdate.serialize().also { it.remove("id") }
    return connection.preparedQueryAwait(query, Tuple.of(data, id))
      .map { row ->
        val json = row.getValue("data")
          .let { it as JsonObject }
          .put("id", row.getString("id"))
        type.instantiate(json)
      }.requireNoNulls().first()
  }
}

/**
 * Simple mixin query for deleting a single ron from a given table based on id. The default implementation can be used
 * with a call to the `impl` function, or a different implementation may be provided.
 */
interface DeleteQuery {
  companion object {
    /**
     * Provides the default implementation for DeleteQuery. Can be used as follows. Note that constant values should be
     * passed for schema and table, as doing otherwise would risk sql injection.
     *
     * Example:
     *
     * ```
     * class ExampleRepo: DeleteQuery<Example> by DeleteQuery.impl(schema="public", table="example")
     * ```
     */
    fun impl(schema: String, table: String): DeleteQuery =
      DeleteQueryImpl(schema, table)
  }
  suspend fun delete(id: String, connection: SqlClient): String
}

/**
 * The default implementation for DeleteQuery. It deletes a single row in the table by id.
 */
class DeleteQueryImpl(schema: String, table: String): DeleteQuery {

  private val tableName = "$schema.$table"

  override suspend fun delete(id: String, connection: SqlClient): String {
    val query = "delete from $tableName where id = $1 returning id"
    return connection.preparedQueryAwait(query, Tuple.of(id))
      .map { row -> row.getString("id") }
      .firstOrNull()
      ?: throw ModelNotFoundException(
        "Tried to delete an item that does not exist",
        JsonArray(id)
      )
  }
}

