package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.config.config
import dev.fuelyour.vertxkuickstartcore.migrations.migrate
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.getConnectionAwait
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.kotlin.sqlclient.preparedQueryAwait
import io.vertx.pgclient.PgException
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlClient

object TestDbAccessCore : DbAccessCore {
    private var pool: PgPool? = null

    private suspend fun init() {
        val vertx = Vertx.vertx()
        val config = vertx.config()
        val testDBName = "${config.getString("SERVICE_DB_NAME")}_test"
        val testConfig = config.copy().put("SERVICE_DB_NAME", testDBName)
        initTestPool(testConfig, vertx)
        try {
            withConnection({ connection -> BasicDbContext(connection) }) { }
        } catch (e: PgException) {
            println("Creating database $testDBName")
            val da = DbAccessFactory(
                DbAccessCoreImpl(config, vertx)
            ).createWithContext { connection ->
                BasicDbContext(connection)
            }
            da.withConnection {
                connection.preparedQueryAwait("CREATE DATABASE $testDBName;")
            }
        }
        migrate(testConfig)
    }

    private fun initTestPool(config: JsonObject, vertx: Vertx) {
        val connectionOptions = pgConnectOptionsOf(
            port = config.getInteger("SERVICE_DB_PORT"),
            host = config.getString("SERVICE_DB_HOST"),
            database = config.getString("SERVICE_DB_NAME"),
            user = config.getString("SERVICE_DB_USER"),
            password = config.getString("SERVICE_DB_PASSWORD"),
            properties = mapOf(
                "search_path" to config.getString(
                    "schema",
                    "public"
                )
            )
        )
        val poolOptions = poolOptionsOf(maxSize = 10)
        pool = PgPool.pool(vertx, connectionOptions, poolOptions)
    }

    override suspend fun <T, A : DbContext> withConnection(
        dbContextInit: (SqlClient) -> A,
        dbAction: suspend A.() -> T
    ): T {
        if (pool == null) {
            init()
        }
        val pool = pool ?: throw Exception("Failed to init connection pool")
        val connection = pool.getConnectionAwait()
        val transaction = connection.begin()
        val dbContext = dbContextInit(connection)
        val result: T
        try {
            result = dbContext.dbAction()
        } catch (ex: Exception) {
            throw ex
        } finally {
            try {
                transaction.rollback()
                connection.close()
            } catch (ignore: Exception) {
            }
        }
        return result
    }

    override suspend fun <T, A : DbContext> inTransaction(
        dbContextInit: (SqlClient) -> A,
        dbAction: suspend A.() -> T
    ): T {
        return withConnection(dbContextInit, dbAction)
    }
}
