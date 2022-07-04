package de.lhns.doobie.flyway

import cats.effect.{Resource, Sync}
import cats.syntax.flatMap._
import doobie.Transactor
import org.flywaydb.core.api.MigrationInfoService
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output._

import javax.sql.DataSource
import scala.util.chaining._

object Flyway {
  def apply[F[_], A <: DataSource, B](transactor: Transactor.Aux[F, A])
                                     (f: FluentConfiguration => F[B]): Resource[F, B] =
    Resource.eval(transactor.configure { dataSource =>
      org.flywaydb.core.Flyway.configure
        .dataSource(dataSource)
        .pipe(f)
    })

  def info[F[_] : Sync, A <: DataSource](transactor: Transactor.Aux[F, A])
                                        (configure: FluentConfiguration => F[FluentConfiguration]): Resource[F, MigrationInfoService] =
    Flyway(transactor) {
      configure(_)
        .flatMap(configuration => Sync[F].blocking {
          configuration
            .load()
            .info()
        })
    }

  def migrate[F[_] : Sync, A <: DataSource](transactor: Transactor.Aux[F, A])
                                           (configure: FluentConfiguration => F[FluentConfiguration]): Resource[F, MigrateResult] =
    Flyway(transactor) {
      configure(_)
        .flatMap(configuration => Sync[F].blocking {
          configuration
            .load()
            .migrate()
        })
    }

  def validate[F[_] : Sync, A <: DataSource](transactor: Transactor.Aux[F, A])
                                            (configure: FluentConfiguration => F[FluentConfiguration]): Resource[F, Either[ValidateResult, ValidateResult]] =
    Flyway(transactor) {
      configure(_)
        .flatMap(configuration => Sync[F].blocking {
          val result = configuration
            .load()
            .validateWithResult()

          Either.cond(
            result.validationSuccessful || configuration.isCleanOnValidationError,
            result,
            result
          )
        })
    }

  def baseline[F[_] : Sync, A <: DataSource](transactor: Transactor.Aux[F, A])
                                            (configure: FluentConfiguration => F[FluentConfiguration]): Resource[F, BaselineResult] =
    Flyway(transactor) {
      configure(_)
        .flatMap(configuration => Sync[F].blocking {
          configuration
            .load()
            .baseline()
        })
    }

  def repair[F[_] : Sync, A <: DataSource](transactor: Transactor.Aux[F, A])
                                          (configure: FluentConfiguration => F[FluentConfiguration]): Resource[F, RepairResult] =
    Flyway(transactor) {
      configure(_)
        .flatMap(configuration => Sync[F].blocking {
          configuration
            .load()
            .repair()
        })
    }

  def clean[F[_] : Sync, A <: DataSource](transactor: Transactor.Aux[F, A], confirm: Boolean = false)
                                         (configure: FluentConfiguration => F[FluentConfiguration]): Resource[F, CleanResult] =
    Flyway(transactor) {
      configure(_)
        .flatMap(configuration => Sync[F].blocking {
          require(confirm, "please confirm that you want to clean the database")
          configuration
            .load()
            .clean()
        })
    }
}
