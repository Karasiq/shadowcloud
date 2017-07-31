package com.karasiq.shadowcloud.persistence.h2

import com.typesafe.config.{Config, ConfigFactory}
import io.getquill.{H2JdbcContext, SnakeCase}

object H2Context {
  type ContextT = H2JdbcContext[SnakeCase]

  def apply(config: Config, password: String): ContextT = {
    new ContextT(createJdbcConfig(config, password))
  }

  private[this] def createJdbcConfig(config: Config, password: String): Config = {
    import scala.collection.JavaConverters._
    require(!password.contains(" "), "Space character is not allowed")

    val path = config.getString("path")
    val cipher = config.getString("cipher")
    val compress = config.getBoolean("compress")
    val initScript = config.getString("init-script")

    //noinspection SpellCheckingInspection
    ConfigFactory.parseMap(Map(
      "dataSourceClassName" → "org.h2.jdbcx.JdbcDataSource",
      "dataSource.url" → s"jdbc:h2:file:$path;CIPHER=$cipher;COMPRESS=$compress;INIT=RUNSCRIPT FROM '$initScript'",
      "dataSource.user" → "sa",
      "dataSource.password" → s"$password sa"
    ).asJava)
  }
}
