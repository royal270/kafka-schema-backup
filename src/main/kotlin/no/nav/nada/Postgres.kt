package no.nav.nada

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.config.ApplicationConfig
import no.nav.vault.jdbc.hikaricp.HikariCPVaultUtil
import org.flywaydb.core.Flyway
import javax.sql.DataSource

fun dataSourceFrom(config: DatabaseConfig, role: String = "user"): DataSource {
    return if (config.local) {
        HikariDataSource(hikariConfigFrom(config))
    } else {
        hikariDataSourceWithVaultIntegration(config, role)
    }
}
fun databaseConfigFrom(appConfig: ApplicationConfig) = DatabaseConfig(
    host = appConfig.propertyOrNull("database.host_${appConfig.envKind}")?.getString() ?: "localhost",
    port = appConfig.propertyOrNull("database.port")?.getString()?.toInt() ?: 5432,
    name = appConfig.propertyOrNull("database.name")?.getString() ?: "nada-schema-backup",
    username = appConfig.propertyOrNull("database.user")?.getString() ?: "nada",
    password = appConfig.propertyOrNull("database.password")?.getString() ?: "nadapassword",
    vaultMountPath = appConfig.propertyOrNull("database.vault_mount_path_${appConfig.envKind}")?.getString() ?: "",
    local = appConfig.propertyOrNull("ktor.environment")?.getString() == "local"
)

private fun hikariDataSourceWithVaultIntegration(config: DatabaseConfig, role: String = "user") =
    HikariCPVaultUtil.createHikariDataSourceWithVaultIntegration(
        hikariConfigFrom(config),
        config.vaultMountPath,
        "${config.name}-$role"
    )

private fun hikariConfigFrom(config: DatabaseConfig) =
    HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.name}"
        maximumPoolSize = 3
        minimumIdle = 1
        idleTimeout = 10001
        connectionTimeout = 1000
        maxLifetime = 30001
        username = config.username
        password = config.password
    }

data class DatabaseConfig(
    val host: String,
    val port: Int,
    val name: String,
    val username: String,
    val password: String,
    val vaultMountPath: String,
    val local: Boolean = false
)
internal fun migrate(dataSource: HikariDataSource, initSql: String = ""): Int =
    Flyway.configure().dataSource(dataSource).initSql(initSql).load().migrate()

internal fun clean(dataSource: HikariDataSource) = Flyway.configure().dataSource(dataSource).load().clean()

val ApplicationConfig.envKind get() = property("ktor.environment").getString()
