package dev.fuelyour.vertxkuickstartcore.tools

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.pgclient.pgConnectOptionsOf
import io.vertx.kotlin.sqlclient.getConnectionAwait
import io.vertx.kotlin.sqlclient.poolOptionsOf
import io.vertx.pgclient.PgPool
import io.vertx.sqlclient.SqlConnection

class DbAccessFactory(private val core: DbAccessCore) {
    fun <A : DbContext> createWithContext(
        dbContextInit: (SqlConnection) -> A
    ): DbAccess<A> =
        DbAccess(core, dbContextInit)
}

class DbAccess<A : DbContext>(
    private val core: DbAccessCore,
    private val dbContextInit: (SqlConnection) -> A
) {

    /**
     * Get a connection to the database.
     *
     * @param dbAction code block in which a connection to the database is
     * available
     */
    suspend fun <T> withConnection(dbAction: suspend A.() -> T): T =
        core.withConnection(dbContextInit, dbAction)

    /**
     * Get a transactional connection to the database. On success the
     * transaction is committed. On error the transaction is rolled back.
     *
     * @param dbAction code block in which a connection to the database is
     * available
     */
    suspend fun <T> inTransaction(dbAction: suspend A.() -> T): T =
        core.inTransaction(dbContextInit, dbAction)
}

interface DbContext

class BasicDbContext(val connection: SqlConnection) : DbContext

fun basicDbContext(connection: SqlConnection): BasicDbContext =
    BasicDbContext(connection)

interface DbAccessCore {
    suspend fun <T, A : DbContext> withConnection(
        dbContextInit: (SqlConnection) -> A,
        dbAction: suspend A.() -> T
    ): T

    suspend fun <T, A : DbContext> inTransaction(
        dbContextInit: (SqlConnection) -> A,
        dbAction: suspend A.() -> T
    ): T
}

/**
 * Establishes a connection to the database specified in the config,
 * and maintains a connection pool to that database.
 */
class DbAccessCoreImpl(config: JsonObject, vertx: Vertx) : DbAccessCore {
    private val pool: PgPool

    init {
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
        dbContextInit: (SqlConnection) -> A,
        dbAction: suspend A.() -> T
    ): T {
        val connection = pool.getConnectionAwait()
        val dbContext = dbContextInit(connection)
        val result: T
        try {
            result = dbContext.dbAction()
        } catch (ex: Exception) {
            throw ex
        } finally {
            try {
                connection.close()
            } catch (ignore: Exception) {
            }
        }
        return result
    }

    override suspend fun <T, A : DbContext> inTransaction(
        dbContextInit: (SqlConnection) -> A,
        dbAction: suspend A.() -> T
    ): T {
        val connection = pool.getConnectionAwait()
        val transaction = connection.begin()
        val dbContext = dbContextInit(connection)
        val result: T
        try {
            result = dbContext.dbAction()
            transaction.commit()
        } catch (ex: Exception) {
            transaction.rollback()
            throw ex
        } finally {
            try {
                connection.close()
            } catch (ignore: Exception) {
            }
        }
        return result
    }
}
