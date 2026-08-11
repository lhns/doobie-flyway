package de.lhns.flyway.baseline

import munit.FunSuite
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.h2.jdbcx.JdbcDataSource

import javax.sql.DataSource

/** Tests the resource rewriting in [[BaselineMigrations]] against a real database.
  *
  * Every assertion goes through the schema history table rather than the `MigrateResult`, so
  * what is checked is the state a later run would actually see.
  */
class BaselineMigrationsSuite extends FunSuite {
  private def dataSource(name: String): DataSource = {
    val ds = new JdbcDataSource
    // DB_CLOSE_DELAY=-1 keeps the in-memory database alive between the connections of a
    // multi-step test; the unique name per test keeps tests isolated.
    ds.setURL(s"jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1")
    ds.setUser("sa")
    ds.setPassword("")
    ds
  }

  private def migrate(
      ds: DataSource,
      location: String,
      baselineMigrationPrefix: String = "B",
      configure: FluentConfiguration => FluentConfiguration = identity
  ): Unit = {
    def configuration: FluentConfiguration =
      configure(
        Flyway
          .configure()
          .dataSource(ds)
          .locations(s"classpath:$location")
      )

    // Naming is validated only after the rewrite, as in the README: beforehand the resource
    // list still contains baseline files, whose prefix Flyway does not necessarily recognise.
    val info = configuration.load().info()
    BaselineMigrations
      .withBaselineMigrate(configuration, info, baselineMigrationPrefix)
      .validateMigrationNaming(true)
      .load()
      .migrate()
    ()
  }

  private def query[A](ds: DataSource, sql: String)(f: java.sql.ResultSet => A): List[A] = {
    val connection = ds.getConnection
    try {
      val statement = connection.createStatement()
      try {
        val rs = statement.executeQuery(sql)
        Iterator.continually(rs).takeWhile(_.next()).map(f).toList
      } finally statement.close()
    } finally connection.close()
  }

  /** The applied migrations as `version -> script`, in the order they were installed.
    *
    * Flyway's own bookkeeping rows (schema and history-table creation) sit at a negative
    * installed rank and are excluded, matching what `MigrationInfoService.applied()` reports —
    * which is what the algorithm itself reads.
    */
  private def history(ds: DataSource): List[(String, String)] =
    // Flyway creates the history table as a quoted, lower-case identifier.
    query(
      ds,
      """select "version", "script" from "flyway_schema_history"
        |where "installed_rank" >= 0 order by "installed_rank"""".stripMargin
    ) { rs =>
      (rs.getString(1), rs.getString(2))
    }

  private def versions(ds: DataSource): List[String] =
    history(ds).map(_._1)

  private def tables(ds: DataSource): Set[String] =
    query(
      ds,
      "select table_name from information_schema.tables where table_schema = 'PUBLIC' and table_type = 'BASE TABLE'"
    )(_.getString(1)).map(_.toLowerCase).toSet - "flyway_schema_history"

  test("no baseline: the full versioned history is applied") {
    val ds = dataSource("no_baseline")
    migrate(ds, "db/no-baseline")

    assertEquals(versions(ds), List("001", "002"))
    assertEquals(tables(ds), Set("a", "b"))
  }

  test("single baseline: the baseline replaces the versions it covers") {
    val ds = dataSource("single_baseline")
    migrate(ds, "db/single-baseline")

    // V001 and V002 are covered by B002 and never run; the baseline is recorded as version 2
    // but keeps its own script name, which is what pins this database to it later.
    assertEquals(versions(ds), List("002", "003"))
    assertEquals(history(ds).head._2, "B002__snapshot.sql")
    assertEquals(tables(ds), Set("a", "b", "c"))
  }

  test("multiple baselines: a fresh database takes the newest one") {
    val ds = dataSource("multi_baseline")
    migrate(ds, "db/multi-baseline")

    assertEquals(versions(ds), List("002", "003"))
    assertEquals(history(ds).head._2, "B002__snapshot.sql")
    assertEquals(tables(ds), Set("a", "b", "c"))
  }

  test("a database bootstrapped from a baseline stays pinned to that baseline") {
    val ds = dataSource("rerun")
    migrate(ds, "db/rerun/before")
    assertEquals(versions(ds), List("002", "003"))

    // A newer B004 has since been added. The database was created from B002, so it must keep
    // resolving B002 — picking B004 here would invalidate the recorded version and checksum.
    migrate(ds, "db/rerun/after")

    assertEquals(versions(ds), List("002", "003", "004"))
    assertEquals(history(ds).head._2, "B002__snapshot.sql")
    assertEquals(tables(ds), Set("a", "b", "c", "d"))
  }

  test("a database predating any baseline keeps its full versioned history") {
    val ds = dataSource("legacy")
    migrate(ds, "db/legacy/before")
    assertEquals(versions(ds), List("001", "002"))

    // A baseline covering V001 and V002 is introduced afterwards. This database started at
    // V001, matches no baseline, and must carry on as if baselines did not exist.
    migrate(ds, "db/legacy/after")

    assertEquals(versions(ds), List("001", "002", "003"))
    assertEquals(tables(ds), Set("a", "b", "c"))
  }

  test("baselines in a subdirectory of the location stay pinned too") {
    val ds = dataSource("subdir")
    migrate(ds, "db/subdir/before")
    assertEquals(versions(ds), List("002", "003"))

    // Flyway records the script relative to the location, so for a nested layout the recorded
    // script is not the bare filename. Matching on the filename alone silently fails to find
    // the baseline here and degrades the database to the full-history path.
    migrate(ds, "db/subdir/after")

    assertEquals(versions(ds), List("002", "003", "004"))
    assertEquals(tables(ds), Set("a", "b", "c", "d"))
  }

  test("custom sql and baseline migration prefixes") {
    val ds = dataSource("custom_prefix")
    migrate(
      ds,
      "db/custom-prefix",
      baselineMigrationPrefix = "X",
      configure = _.sqlMigrationPrefix("M")
    )

    assertEquals(versions(ds), List("002", "003"))
    assertEquals(history(ds).head._2, "X002__snapshot.sql")
    assertEquals(tables(ds), Set("a", "b", "c"))
  }

  test("repeatable migrations pass through untouched") {
    val ds = dataSource("repeatable")
    migrate(ds, "db/repeatable")

    assertEquals(versions(ds), List("002", "003", null))
    assertEquals(history(ds).last._2, "R__v.sql")
    assertEquals(tables(ds), Set("a", "b", "c"))
    assertEquals(query(ds, "select count(*) from v")(_.getInt(1)), List(0))
  }
}
