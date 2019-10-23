package dev.fuelyour.tools

import dev.fuelyour.exceptions.ModelNotFoundException
import io.vertx.core.json.JsonObject
import io.vertx.core.Vertx
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.getConnectionAwait
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlClient

class DatabaseAccess(config: JsonObject, vertx: Vertx) {
  private val pool: PgPool

  init {
    val connectionOptions = pgConnectOptionsOf(
      port = config.getInteger("SERVICE_DB_PORT"),
      host = config.getString("SERVICE_DB_HOST"),
      database = config.getString("SERVICE_DB_NAME"),
      user = config.getString("SERVICE_DB_USER"),
      password = config.getString("SERVICE_DB_PASSWORD"),
      properties = mapOf("search_path" to config.getString("schema", "public"))
    )
    val poolOptions = poolOptionsOf(maxSize = 10)
    pool = PgPool.pool(vertx, connectionOptions, poolOptions)
  }

  suspend fun <T : Any> getConnection(
    dbAction: suspend (SqlClient) -> T?
  ): T {
    val result: T?
    val connection = pool.getConnectionAwait()
    try {
      result = dbAction.invoke(connection)
    } catch (ex: Exception) {
      throw ex
    } finally {
      try {
        connection.close()
      } catch (ignore: Exception) {}
    }
    if (result == null)
      throw ModelNotFoundException("Record not found")
    return result
  }

  suspend fun <T : Any> getTransaction(
    dbAction: suspend (SqlClient) -> T?
  ): T {
    val result: T?
    val connection = pool.getConnectionAwait()
    val transaction = connection.begin()
    try {
      result = dbAction.invoke(connection)
      transaction.commit()
    } catch (ex: Exception) {
      transaction.rollback()
      throw ex
    } finally {
      try {
        connection.close()
      } catch (ignore: Exception) {}
    }
    if (result == null)
      throw ModelNotFoundException("Record not found")
    return result
  }
}