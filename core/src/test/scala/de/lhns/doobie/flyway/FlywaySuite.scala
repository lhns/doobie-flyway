package de.lhns.doobie.flyway

import cats.effect.IO
import de.lhns.doobie.flyway.BaselineMigrations._
import doobie.ExecutionContexts
import doobie.h2.H2Transactor
import munit.CatsEffectSuite

class FlywaySuite extends CatsEffectSuite {
  test("migrate") {
    (for {
      ce <- ExecutionContexts.fixedThreadPool[IO](4)
      xa <- H2Transactor.newH2Transactor[IO](
        url = "jdbc:h2:mem:test",
        user = "",
        pass = "",
        connectEC = ce
      )
      _ <- Flyway(xa) { flyway =>
        for {
          info <- flyway.info()
          _ <- flyway
            .configure(_
              .withBaselineMigrate(info)
              .validateMigrationNaming(true)
            )
            .migrate()
        } yield ()
      }.toResource
    } yield ()).use_
  }
}
