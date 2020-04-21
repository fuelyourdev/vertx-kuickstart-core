package dev.fuelyour.vertxkuickstartcore.tools

import io.vertx.sqlclient.Tuple
import dev.fuelyour.namedtopositionalsqlparams.converter.convertNamedToPositional
import dev.fuelyour.namedtopositionalsqlparams.converter.prepareNamedAsPositional

data class PositionalSql(val sql: String, val params: Tuple)

data class PreparedPositionalSql(
    val sql: String,
    val paramNames: List<String>,
    val convertParams: (params: Map<String, Any?>) -> Tuple
) {
    fun toPositional(params: Map<String, Any?>): PositionalSql =
        PositionalSql(sql, convertParams(params))
}

fun toPositional(sql: String, params:Map<String, Any?>): PositionalSql =
    convertNamedToPositional(sql, params).let { (sql, params) ->
        PositionalSql(sql, Tuple.tuple(params))
    }

fun preparePositional(sql: String, paramNames: Set<String>? = null): PreparedPositionalSql =
    prepareNamedAsPositional(sql, paramNames).let { prepared ->
        PreparedPositionalSql(prepared.sql, prepared.paramNames) { params ->
            Tuple.tuple(prepared.convertParams(params))
        }
    }