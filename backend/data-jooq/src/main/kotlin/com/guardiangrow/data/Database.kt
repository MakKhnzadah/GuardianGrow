package com.guardiangrow.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import javax.sql.DataSource

class Database(config: DbConfig) {
  private val ds: HikariDataSource
  val dsl: DSLContext

  init {
    val hikari = HikariConfig().apply {
      jdbcUrl = config.jdbcUrl
      username = config.username
      password = config.password
      maximumPoolSize = 10
      isAutoCommit = true
    }
    ds = HikariDataSource(hikari)

    // Run DB migrations on startup.
    Flyway.configure()
      .dataSource(ds)
      .locations("classpath:db/migration")
      .baselineOnMigrate(true)
      .load()
      .migrate()

    dsl = DSL.using(ds, SQLDialect.DEFAULT)
  }

  fun dataSource(): DataSource = ds

  fun close() {
    ds.close()
  }
}
