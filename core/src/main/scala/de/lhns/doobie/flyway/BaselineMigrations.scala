package de.lhns.doobie.flyway

import de.lhns.flyway.baseline.{BaselineMigrations => JBaselineMigrations}
import org.flywaydb.core.api.MigrationInfoService
import org.flywaydb.core.api.configuration.FluentConfiguration

object BaselineMigrations {
  implicit class BaselineMigrationOps(val configuration: FluentConfiguration) extends AnyVal {
    def withBaselineMigrate(info: MigrationInfoService, baselineMigrationPrefix: String = "B"): FluentConfiguration =
      JBaselineMigrations.withBaselineMigrate(configuration, info, baselineMigrationPrefix)
  }
}
