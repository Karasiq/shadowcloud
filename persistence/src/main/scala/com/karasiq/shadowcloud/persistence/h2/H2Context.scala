package com.karasiq.shadowcloud.persistence.h2

import com.typesafe.config.{Config, ConfigFactory}
import io.getquill.{H2JdbcContext, SnakeCase}

import scala.util.control.NonFatal

object H2Context {
  type ContextT = H2JdbcContext[SnakeCase]

  def apply(config: Config, password: String): ContextT = {
    try {
      new ContextT(createJdbcConfig(config, password))
    } catch { case NonFatal(err) =>
      throw new RuntimeException("Failed to open DB", err)
    }
  }

  private[this] def createJdbcConfig(config: Config, password: String): Config = {
    import scala.collection.JavaConverters._
    import com.karasiq.common.configs.ConfigImplicits._
    require(!password.contains(" "), "Space character is not allowed")

    val path = config.getString("path")
    val compress = config.withDefault(true, _.getBoolean("compress"))
    val cipher = config.optional(_.getString("cipher")).filter(_.nonEmpty)
    val initScript = config.optional(_.getString("init-script")).filter(_.nonEmpty)

    //noinspection SpellCheckingInspection
    ConfigFactory.parseMap(Map(
      "dataSourceClassName" → "org.h2.jdbcx.JdbcDataSource",
      "dataSource.url" → (s"jdbc:h2:file:$path;COMPRESS=$compress;DB_CLOSE_ON_EXIT=FALSE"
        + cipher.fold("")(cipher ⇒ s";CIPHER=$cipher")
        + initScript.fold("")(script ⇒ s";INIT=RUNSCRIPT FROM '$script'")),
      "dataSource.user" → "sa",
      "dataSource.password" → (if (cipher.nonEmpty) s"$password sa" else "sa")
    ).asJava)
  }
}
