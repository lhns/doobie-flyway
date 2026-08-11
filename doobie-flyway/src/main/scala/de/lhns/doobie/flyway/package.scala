package de.lhns.doobie

import de.lhns.flyway.resource.{LoadableResources, ResourceProviders}
import org.flywaydb.core.api.ResourceProvider
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.resource.LoadableResource

/** Scala syntax for the Flyway resource helpers in the doobie-free `flyway-baseline` module.
  */
package object flyway {
  implicit class FluentConfigurationOps(val configuration: FluentConfiguration) extends AnyVal {
    def resourceProviderOption: Option[ResourceProvider] = Option(configuration.getResourceProvider)

    def resourceProviderOrDefault: ResourceProvider = ResourceProviders.orDefault(configuration)

    def mapResourceProvider(f: ResourceProvider => ResourceProvider): FluentConfiguration =
      configuration.resourceProvider(f(resourceProviderOrDefault))
  }

  implicit class LoadableResourceOps(val resource: LoadableResource) extends AnyVal {
    def mapContent(f: String => String): LoadableResource =
      LoadableResources.mapContent(resource, f(_))

    def mapFilename(f: String => String): LoadableResource =
      LoadableResources.mapFilename(resource, f(_))
  }

  implicit class ResourceProviderOps(val resourceProvider: ResourceProvider) extends AnyVal {
    def mapResource(f: LoadableResource => LoadableResource): ResourceProvider =
      ResourceProviders.map(resourceProvider, f(_))
  }
}
