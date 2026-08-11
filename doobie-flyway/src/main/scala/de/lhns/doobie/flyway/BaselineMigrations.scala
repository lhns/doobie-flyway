package de.lhns.doobie.flyway

import de.lhns.flyway.baseline.{BaselineMigrations => JBaselineMigrations}
import org.flywaydb.core.api.MigrationInfoService
import org.flywaydb.core.api.configuration.FluentConfiguration

/** Scala syntax for [[de.lhns.flyway.baseline.BaselineMigrations]], which lives in the
  * doobie-free `flyway-baseline` module.
  */
object BaselineMigrations {
  implicit class BaselineMigrationOps(val configuration: FluentConfiguration) extends AnyVal {
    def withBaselineMigrate(info: MigrationInfoService, baselineMigrationPrefix: String = "B"): FluentConfiguration =
      JBaselineMigrations.withBaselineMigrate(configuration, info, baselineMigrationPrefix)
  }
}
