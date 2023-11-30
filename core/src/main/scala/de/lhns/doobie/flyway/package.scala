package de.lhns.doobie

import org.flywaydb.core.api.ResourceProvider
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.migration.JavaMigration
import org.flywaydb.core.api.resource.LoadableResource
import org.flywaydb.core.internal.scanner.{LocationScannerCache, ResourceNameCache, Scanner}

import java.io.{Reader, StringReader}
import java.util
import java.util.stream.Collectors

package object flyway {
  implicit class FluentConfigurationOps(val configuration: FluentConfiguration) extends AnyVal {
    def resourceProviderOption: Option[ResourceProvider] = Option(configuration.getResourceProvider)

    def resourceProviderOrDefault: ResourceProvider = resourceProviderOption.getOrElse {
      new Scanner[JavaMigration](
        classOf[JavaMigration],
        false,
        new ResourceNameCache,
        new LocationScannerCache,
        configuration
      )
    }

    def mapResourceProvider(f: ResourceProvider => ResourceProvider): FluentConfiguration =
      configuration.resourceProvider(f(resourceProviderOrDefault))
  }

  private def readString(reader: Reader): String = {
    val charArray = new Array[Char](8 * 1024)
    val builder = new java.lang.StringBuilder()
    var numCharsRead: Int = 0
    while ( {
      numCharsRead = reader.read(charArray, 0, charArray.length)
      numCharsRead != -1
    }) {
      builder.append(charArray, 0, numCharsRead)
    }
    builder.toString
  }

  implicit class LoadableResourceOps(val resource: LoadableResource) extends AnyVal {
    def mapContent(f: String => String): LoadableResource = new LoadableResource {
      override def read(): Reader = new StringReader(f(readString(resource.read())))

      override def getAbsolutePath: String = resource.getAbsolutePath

      override def getAbsolutePathOnDisk: String = resource.getAbsolutePathOnDisk

      override def getFilename: String = resource.getFilename

      override def getRelativePath: String = resource.getRelativePath
    }
  }

  implicit class ResourceProviderOps(val resourceProvider: ResourceProvider) extends AnyVal {
    def mapResource(f: LoadableResource => LoadableResource): ResourceProvider = new ResourceProvider {
      override def getResource(name: String): LoadableResource =
        Option(resourceProvider.getResource(name)).map(f).orNull

      override def getResources(prefix: String, suffixes: Array[String]): util.Collection[LoadableResource] =
        resourceProvider.getResources(prefix, suffixes).stream().map(f(_)).collect(Collectors.toList())
    }
  }
}
