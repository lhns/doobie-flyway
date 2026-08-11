# doobie-flyway

[![build](https://github.com/lhns/doobie-flyway/actions/workflows/build.yml/badge.svg)](https://github.com/lhns/doobie-flyway/actions/workflows/build.yml)
[![Release Notes](https://img.shields.io/github/release/lhns/doobie-flyway.svg?maxAge=3600)](https://github.com/lhns/doobie-flyway/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/de.lhns/doobie-flyway_2.13)](https://search.maven.org/artifact/de.lhns/doobie-flyway_2.13)
[![Apache License 2.0](https://img.shields.io/github/license/lhns/doobie-flyway.svg?maxAge=3600)](https://www.apache.org/licenses/LICENSE-2.0)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

[Flyway](https://flywaydb.org/) migrations for [doobie](https://github.com/tpolecat/doobie).

This repository publishes two artifacts:

| Artifact                | Description                                                                        |
|-------------------------|------------------------------------------------------------------------------------|
| `de.lhns::doobie-flyway` | Runs Flyway from a doobie `Transactor` in `cats-effect`.                            |
| `de.lhns:flyway-baseline` | Squashed baseline migrations for Flyway. Plain Java, no Scala or doobie dependency. |

`doobie-flyway` depends on `flyway-baseline` and exposes it through Scala syntax, so using
the former gives you both.

### build.sbt
```sbt
libraryDependencies += "de.lhns" %% "doobie-flyway" % "0.9.0"
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
          }.toResource
  } yield xa
```

## Baseline migrations

A `Bnnn__description.sql` file is a squashed snapshot of the schema as of version `nnn`. A
fresh database applies the snapshot and then continues with the migrations after it, instead
of replaying the whole history; databases already in the field are unaffected and stay on
the history they were created with. See
[ADR 1](docs/adr/0001-baseline-migrations.md) for the algorithm and its guarantees.

`withBaselineMigrate` needs the migration info of the target database, so it is called
between an `info()` and a `migrate()`, as in the example above. Note that
`validateMigrationNaming` has to be enabled *after* it — beforehand, the resource list still
contains `B` files.

This is available without doobie, cats-effect or Scala:

```xml
<dependency>
    <groupId>de.lhns</groupId>
    <artifactId>flyway-baseline</artifactId>
    <version>0.9.0</version>
</dependency>
```

```java
FluentConfiguration configuration = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration");

MigrationInfoService info = configuration.load().info();

BaselineMigrations.withBaselineMigrate(configuration, info)
        .validateMigrationNaming(true)
        .load()
        .migrate();
```

## License
This project uses the Apache 2.0 License. See the file called LICENSE.
