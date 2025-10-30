package de.lhns.doobie.flyway

import cats.Functor
import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import doobie.Transactor
import org.flywaydb.core.api.MigrationInfoService
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.flywaydb.core.api.output._

import javax.sql.DataSource

final class Flyway[F[_]] private(configuration: FluentConfiguration) {
  def configure(f: FluentConfiguration => FluentConfiguration): Flyway[F] =
    new Flyway(f(new FluentConfiguration().configuration(configuration)))

  def configureF(f: FluentConfiguration => F[FluentConfiguration])(implicit F: Functor[F]): F[Flyway[F]] =
    f(new FluentConfiguration().configuration(configuration)).map(new Flyway(_))

  def apply[B](f: FluentConfiguration => F[B]): F[B] =
    f(configuration)

  def info()(implicit F: Sync[F]): F[MigrationInfoService] = apply { configuration =>
    F.blocking {
      configuration
        .load()
        .info()
    }
  }

  def migrate()(implicit F: Sync[F]): F[MigrateResult] = apply { configuration =>
    Sync[F].blocking {
      configuration
        .load()
        .migrate()
    }
  }

  def validate()(implicit F: Sync[F]): F[Either[ValidateResult, ValidateResult]] = apply { configuration =>
    Sync[F].blocking {
      val result = configuration
        .load()
        .validateWithResult()

      Either.cond(
        result.validationSuccessful || configuration.isCleanOnValidationError,
        result,
        result
      )
    }
  }

  def baseline()(implicit F: Sync[F]): F[BaselineResult] = apply { configuration =>
    Sync[F].blocking {
      configuration
        .load()
        .baseline()
    }
  }

  def repair()(implicit F: Sync[F]): F[RepairResult] = apply { configuration =>
    Sync[F].blocking {
      configuration
        .load()
        .repair()
    }
  }

  def clean()(implicit F: Sync[F]): F[CleanResult] = apply { configuration =>
    Sync[F].blocking {
      configuration
        .load()
        .clean()
    }
  }
}

object Flyway {
  def apply[F[_], A <: DataSource, B](transactor: Transactor.Aux[F, A])
                                     (f: Flyway[F] => F[B]): F[B] = {
    transactor.configure { dataSource =>
      val flyway = new Flyway[F](
        org.flywaydb.core.Flyway.configure
          .dataSource(dataSource)
      )

      f(flyway)
    }
  }
}
