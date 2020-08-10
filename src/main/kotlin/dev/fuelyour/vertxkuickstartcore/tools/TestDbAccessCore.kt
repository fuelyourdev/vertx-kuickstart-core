@file:Suppress("SqlNoDataSourceInspection")

package dev.fuelyour.vertxkuickstartcore.tools

import dev.fuelyour.vertxkuickstartcore.config.config
import dev.fuelyour.vertxkuickstartcore.migrations.migrate
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.executeAwait
import io.vertx.kotlin.sqlclient.getConnectionAwait
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.pgclient.PgException
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlConnection
import io.vertx.sqlclient.Transaction

object TestDbAccessCorePool {
    private var pool: PgPool? = null

    private var connection: SqlConnection? = null
    private var transaction: Transaction? = null

    suspend fun begin() {
        connection = getPool().getConnectionAwait()
        transaction = connection?.begin()
    }

    fun end() {
        try {
            transaction?.rollback()
            connection?.close()
        } catch (ignore: Exception) {
        }
        connection = null
        transaction = null
    }

    fun getConnection(): SqlConnection? {
        return connection
    }

    private suspend fun getPool(): PgPool {
        if (pool == null) {
            init()
        }
        return pool ?: throw Exception("Failed to init connection pool")
    }

    private suspend fun init() {
        val vertx = Vertx.vertx()
        val config = vertx.config().getJsonObject("SERVICE_DB_SYS_ADMIN")
        val placeholders = vertx.config()
            .getJsonObject("SERVICE_DB_PLACEHOLDERS")
        val testDBName = "${config.getString("SERVICE_DB_NAME")}_test"
        val testConfig = config.copy().put("SERVICE_DB_NAME", testDBName)
        initTestPool(testConfig, vertx)
        try {
            val testDbAccessCore = TestDbAccessCore(config)
            begin()
            testDbAccessCore.withConnection(::basicDbContext) { }
            end()
        } catch (e: PgException) {
            println("Creating database $testDBName")
            val da = DbAccessFactory(
                DbAccessCoreImpl(config, vertx)
            ).createWithContext(::basicDbContext)
            da.withConnection {
                connection.preparedQuery("create database $testDBName;")
                    .executeAwait()
            }
        }
        migrate(testConfig, placeholders)
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
        val poolOptions = poolOptionsOf(maxSize = 1)
        pool = PgPool.pool(vertx, connectionOptions, poolOptions)
    }
}

class TestDbAccessCore(config: JsonObject) : DbAccessCore {

    private val user = config.getString("SERVICE_DB_USER")

    override suspend fun <T, A : DbContext> withConnection(
        dbContextInit: (SqlConnection) -> A,
        dbAction: suspend A.() -> T
    ): T {
        val connection = TestDbAccessCorePool.getConnection()
            ?: throw Exception("Must call begin() first")
        connection.preparedQuery("set role $user;")
            .executeAwait()
        val dbContext = dbContextInit(connection)
        val result = dbContext.dbAction()
        connection.preparedQuery("reset role;")
            .executeAwait()
        return result
    }

    override suspend fun <T, A : DbContext> inTransaction(
        dbContextInit: (SqlConnection) -> A,
        dbAction: suspend A.() -> T
    ): T =
        withConnection(dbContextInit, dbAction)
}
