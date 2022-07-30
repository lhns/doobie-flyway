package de.lhns.doobie.flyway

import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.migration.JavaMigration
import org.flywaydb.core.api.resource.LoadableResource
import org.flywaydb.core.api.{MigrationInfoService, ResourceProvider}
import org.flywaydb.core.internal.scanner.{LocationScannerCache, ResourceNameCache, Scanner}

import java.io.Reader
import java.util
import java.util.regex.Pattern
import scala.jdk.CollectionConverters._

object BaselineMigrations {
  implicit class BaselineMigrationOps(val configuration: FluentConfiguration) extends AnyVal {
    def withBaselineMigrate(info: MigrationInfoService, baselineMigrationPrefix: String = "B"): FluentConfiguration = {
      val firstAppliedScriptOption = info
        .applied()
        .headOption
        .map(_.getScript)

      val resourceProvider = Option(configuration.getResourceProvider).getOrElse {
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

      configuration.resourceProvider(new ResourceProvider {
        override def getResource(name: String): LoadableResource =
          resourceProvider.getResource(name)

        private val ResourceName = "(\\D+)(\\d+)__.*".r

        override def getResources(prefix: String, suffixes: Array[String]): util.Collection[LoadableResource] = {
          val resources = resourceProvider.getResources("", suffixes).asScala.toSeq

          val resourcesByType = resources.map { resource =>
            resource.getFilename match {
              case ResourceName(resourceType, versionString) =>
                val version = versionString.toInt
                (resourceType, version, resource)

              case _ =>
                ("", 0, resource)
            }
          }
            .groupMap(_._1)(e => (e._2, e._3))

          val latestBaselineOption = {
            val baselineResources = resourcesByType.getOrElse(baselineMigrationPrefix, Seq.empty)
            firstAppliedScriptOption.fold {
              baselineResources.maxByOption(_._1)
            } { fileName =>
              baselineResources.find(_._2.getFilename == fileName)
            }
          }

          def baselineToNormalMigration(resource: LoadableResource): LoadableResource = new LoadableResource {
            override def read(): Reader = resource.read()

            override def getAbsolutePath: String = resource.getAbsolutePath

            override def getAbsolutePathOnDisk: String = resource.getAbsolutePathOnDisk

            override def getFilename: String = resource.getFilename.replaceFirst(
              "^" + Pattern.quote(baselineMigrationPrefix),
              configuration.getSqlMigrationPrefix
            )

            override def getRelativePath: String = resource.getRelativePath
          }

          lazy val newResources: Seq[LoadableResource] = {
            latestBaselineOption
              .map(e => baselineToNormalMigration(e._2))
              .toSeq ++
              resourcesByType
                .getOrElse(configuration.getSqlMigrationPrefix, Seq.empty)
                .filter { case (version, _) =>
                  latestBaselineOption.forall { case (baselineVersion, _) =>
                    version > baselineVersion
                  }
                }
                .map(_._2) ++
              resourcesByType
                .removed(baselineMigrationPrefix)
                .removed(configuration.getSqlMigrationPrefix)
                .values
                .flatMap(_.map(_._2))
          }

          newResources.filter(_.getFilename.startsWith(prefix)).asJavaCollection
        }
      })
    }
  }
}
