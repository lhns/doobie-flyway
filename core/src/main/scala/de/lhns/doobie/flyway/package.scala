package de.lhns.doobie

import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.migration.JavaMigration
import org.flywaydb.core.api.resource.LoadableResource
import org.flywaydb.core.api.{MigrationInfoService, ResourceProvider}
import org.flywaydb.core.internal.scanner.{LocationScannerCache, ResourceNameCache, Scanner}

import java.util

package object flyway {
  implicit class FluentConfigurationOps(val configuration: FluentConfiguration) extends AnyVal {
    def resourceProviderOption: Option[ResourceProvider] = Option(configuration.getResourceProvider)

    def resourceProviderOrDefault: ResourceProvider = resourceProviderOption.getOrElse {
      new Scanner[JavaMigration](
        classOf[JavaMigration],
        util.Arrays.asList(configuration.getLocations: _*),
        configuration.getClassLoader,
        configuration.getEncoding,
        configuration.isDetectEncoding,
        false,
        new ResourceNameCache,
        new LocationScannerCache,
        configuration.isFailOnMissingLocations
      )
    }
  }
}
