package com.karasiq.shadowcloud.persistence.h2

import com.typesafe.config.{Config, ConfigFactory}
import io.getquill.{H2JdbcContext, SnakeCase}

private object H2Context {
  def createJdbcConfig(config: Config, password: String): Config = {
    import scala.collection.JavaConverters._
    val path = config.getString("path")
    val cipher = config.getString("cipher")

    ConfigFactory.parseMap(Map(
      "dataSourceClassName" → "org.h2.jdbcx.JdbcDataSource",
      "dataSource.url" → s"jdbc:h2:file:$path;CIPHER=$cipher",
      "dataSource.user" → "sa",
      "dataSource.password" → s"$password sa"
    ).asJava)
  }
}

final class H2Context(config: Config, password: String) {
  lazy val db = new H2JdbcContext[SnakeCase](H2Context.createJdbcConfig(config, password))
}
