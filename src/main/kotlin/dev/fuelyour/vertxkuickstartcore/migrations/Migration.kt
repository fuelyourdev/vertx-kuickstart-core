package dev.fuelyour.vertxkuickstartcore.migrations

import io.vertx.core.json.JsonObject
import org.flywaydb.core.Flyway

/**
 * Runs flyway migrations for both the public and the test schemas for the database specified by the dbConfig
 *
 * @param dbConfig The database configuration. Required fields: SERVICE_DB_HOST, SERVICE_DB_PORT, SERVICE_DB_NAME,
 *     SERVICE_DB_USER, SERVICE_DB_PASSWORD
 */
fun migrate(dbConfig: JsonObject) {
    val host = dbConfig.getString("SERVICE_DB_HOST")
    val port = dbConfig.getInteger("SERVICE_DB_PORT")
    val name = dbConfig.getString("SERVICE_DB_NAME")
    val url = "jdbc:postgresql://$host:$port/$name"
    val user = dbConfig.getString("SERVICE_DB_USER")
    val password = dbConfig.getString("SERVICE_DB_PASSWORD")
    listOf("public", "test").forEach {
        println("schema: $it")
        val flyway = Flyway
            .configure()
            .placeholders(mutableMapOf("schema" to it))
            .schemas(it)
            .dataSource(url, user, password)
            .load()
        flyway.migrate()
    }
}
