package de.lhns.doobie.flyway

import cats.effect.{IO, Resource}
import de.lhns.doobie.flyway.BaselineMigrations._
import doobie.{ExecutionContexts, Transactor}
import doobie.h2.H2Transactor
import munit.CatsEffectSuite

class FlywaySuite extends CatsEffectSuite {

  /** Creates an isolated H2 in-memory transactor for a named database. */
  private def transactor(dbName: String): Resource[IO, Transactor[IO]] = for {
    ce <- ExecutionContexts.fixedThreadPool[IO](4)
    xa <- H2Transactor.newH2Transactor[IO](
      url = s"jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1",
      user = "",
      pass = "",
      connectEC = ce
    )
  } yield xa

  /** Runs the standard withBaselineMigrate pipeline within a given transactor and location. */
  private def migrateWithBaseline(xa: Transactor[IO], location: String): IO[Int] =
    Flyway(xa) { flyway =>
      val configured = flyway.configure(_.locations(s"classpath:$location"))
      for {
        info   <- configured.info()
        result <- configured
          .configure(_
            .withBaselineMigrate(info)
            .validateMigrationNaming(true)
          )
          .migrate()
      } yield result.migrationsExecuted
    }

  // -------------------------------------------------------------------------
  // Test 1 (existing): plain V-only migrations on a fresh database work as-is.
  // -------------------------------------------------------------------------
  test("migrate: V-only migrations on fresh database") {
    transactor("test_v_only").use { xa =>
      migrateWithBaseline(xa, "db/migration").map { count =>
        assertEquals(count, 1)
      }
    }
  }

  // -------------------------------------------------------------------------
  // Test 2: single baseline (B001) + subsequent V002 on a fresh database.
  //         Expected: B001 is renamed to V001 and executed together with V002.
  // -------------------------------------------------------------------------
  test("migrate: single baseline + subsequent V migration on fresh database") {
    transactor("test_single_baseline").use { xa =>
      migrateWithBaseline(xa, "db/migration-baseline").map { count =>
        assertEquals(count, 2, "B001→V001 and V002 should both run")
      }
    }
  }

  // -------------------------------------------------------------------------
  // Test 3: multiple baselines (B001, B002) + V003 on a fresh database.
  //         Expected: only the LATEST baseline (B002) is used; B001 is skipped.
  // -------------------------------------------------------------------------
  test("migrate: multiple baselines on fresh database uses latest baseline") {
    transactor("test_multi_baseline").use { xa =>
      migrateWithBaseline(xa, "db/migration-multi-baseline").map { count =>
        assertEquals(count, 2, "Only B002→V002 and V003 should run; B001 must be skipped")
      }
    }
  }
}
