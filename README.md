# doobie-flyway
[![Test Workflow](https://github.com/lhns/doobie-flyway/workflows/test/badge.svg)](https://github.com/lhns/doobie-flyway/actions?query=workflow%3Atest)
[![Release Notes](https://img.shields.io/github/release/lhns/doobie-flyway.svg?maxAge=3600)](https://github.com/lhns/doobie-flyway/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/de.lhns/doobie-flyway_2.13)](https://search.maven.org/artifact/de.lhns/doobie-flyway_2.13)
[![Apache License 2.0](https://img.shields.io/github/license/lhns/doobie-flyway.svg?maxAge=3600)](https://www.apache.org/licenses/LICENSE-2.0)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

[Flyway](https://flywaydb.org/) migrations for [doobie](https://github.com/tpolecat/doobie).

### build.sbt
```sbt
libraryDependencies += "de.lhns" %% "doobie-flyway" % "0.2.9"
```

## Usage
```scala
def transactor(config: DbConfig): Resource[IO, Transactor[IO]] =
  for {
    ce <- ExecutionContexts.fixedThreadPool[IO](config.poolSizeOrDefault)
    xa <- HikariTransactor
            .newHikariTransactor[IO](
              config.driverOrDefault,
              config.url,
              config.user,
              config.password,
              ce
            )
    _  <- Flyway(xa) { flyway =>
            for {
              info <- flyway.info()
              _    <- flyway
                        .configure(_
                          .withBaselineMigrate(info)
                          .validateMigrationNaming(true)
                        )
                        .migrate()
            } yield ()
          }
  } yield xa
```

## License
This project uses the Apache 2.0 License. See the file called LICENSE.
