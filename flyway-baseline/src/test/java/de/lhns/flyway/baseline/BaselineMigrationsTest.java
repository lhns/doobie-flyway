package de.lhns.flyway.baseline;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BaselineMigrationsTest {

    /** Returns a unique H2 in-memory JDBC URL so each test starts with an empty database. */
    private static String uniqueDbUrl() {
        return "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static FluentConfiguration configure(String dbUrl, String location) {
        return Flyway.configure()
                .dataSource(dbUrl, "", "")
                .locations("classpath:" + location);
    }

    // -------------------------------------------------------------------------
    // Helper to run the standard withBaselineMigrate pipeline:
    //   1. call info() on the config (fresh or existing DB)
    //   2. apply withBaselineMigrate + validateMigrationNaming (after rename)
    //   3. run migrate()
    // -------------------------------------------------------------------------
    private static MigrateResult runWithBaseline(FluentConfiguration config) {
        MigrationInfoService info = config.load().info();
        return BaselineMigrations.withBaselineMigrate(config, info)
                .validateMigrationNaming(true)
                .load()
                .migrate();
    }

    private static MigrateResult runWithBaseline(FluentConfiguration config, String prefix) {
        MigrationInfoService info = config.load().info();
        return BaselineMigrations.withBaselineMigrate(config, info, prefix)
                .validateMigrationNaming(true)
                .load()
                .migrate();
    }

    // =========================================================================
    // Test 1: no baseline migrations present — ordinary V-prefixed migrations
    //         should run as normal.
    // =========================================================================
    @Test
    void noBaseline_allVMigrationsRun() {
        FluentConfiguration config = configure(uniqueDbUrl(), "db/migration-no-baseline");

        MigrateResult result = runWithBaseline(config);

        assertTrue(result.success, "Migration should succeed");
        assertEquals(1, result.migrationsExecuted,
                "V001 should be executed");

        var versions = result.migrations.stream()
                .map(m -> Integer.parseInt(m.version))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(1), versions,
                "Only version 1 should be executed");
    }

    // =========================================================================
    // Test 2: single baseline (B001) present on a fresh database — it should be
    //         renamed to V001 and executed, followed by V002.
    // =========================================================================
    @Test
    void singleBaseline_freshDb_baselineRenamedAndSubsequentMigrationRun() {
        FluentConfiguration config = configure(uniqueDbUrl(), "db/migration-single-baseline");

        MigrateResult result = runWithBaseline(config);

        assertTrue(result.success, "Migration should succeed");
        assertEquals(2, result.migrationsExecuted,
                "B001→V001 and V002 should both be executed");

        var versions = result.migrations.stream()
                .map(m -> Integer.parseInt(m.version))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(1, 2), versions,
                "Versions 1 (renamed from B001) and 2 should run in order");
    }

    // =========================================================================
    // Test 3: multiple baselines (B001, B002) plus a V003 migration on a fresh
    //         database — the LATEST baseline (B002) must be selected, renamed to
    //         V002, and V003 must run; B001 must be skipped entirely.
    // =========================================================================
    @Test
    void multipleBaselines_freshDb_latestBaselineUsed() {
        FluentConfiguration config = configure(uniqueDbUrl(), "db/migration-multi-baseline");

        MigrateResult result = runWithBaseline(config);

        assertTrue(result.success, "Migration should succeed");
        assertEquals(2, result.migrationsExecuted,
                "Only B002→V002 and V003 should run (B001 skipped)");

        var versions = result.migrations.stream()
                .map(m -> Integer.parseInt(m.version))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(2, 3), versions,
                "Versions 2 (renamed from B002) and 3 should run; version 1 (B001) must be skipped");
    }

    // =========================================================================
    // Test 4: V migrations with a version number LOWER than the selected baseline
    //         version must be excluded from the migration set.
    //
    //         Resources: B002, V001 (before baseline), V003 (after baseline)
    //         Expected:  B002→V002 runs, V003 runs, V001 is skipped.
    // =========================================================================
    @Test
    void vMigrationsBeforeBaselineVersion_areExcluded() {
        FluentConfiguration config = configure(uniqueDbUrl(), "db/migration-exclude-old-v");

        MigrateResult result = runWithBaseline(config);

        assertTrue(result.success, "Migration should succeed");
        assertEquals(2, result.migrationsExecuted,
                "B002→V002 and V003 should run; V001 must be excluded");

        var versions = result.migrations.stream()
                .map(m -> Integer.parseInt(m.version))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(2, 3), versions,
                "Version 1 (V001 before baseline) must not appear; versions 2 and 3 must run");
    }

    // =========================================================================
    // Test 5: running withBaselineMigrate a SECOND TIME on a database that was
    //         already migrated by a previous withBaselineMigrate call must be
    //         idempotent (0 migrations executed).
    // =========================================================================
    @Test
    void secondRun_afterBaselineMigration_isIdempotent() {
        String url = uniqueDbUrl();
        FluentConfiguration config = configure(url, "db/migration-single-baseline");

        // First run: executes B001→V001 + V002
        MigrateResult firstResult = runWithBaseline(config);
        assertTrue(firstResult.success);
        assertEquals(2, firstResult.migrationsExecuted, "First run should execute 2 migrations");

        // Second run: DB already has V001 and V002 in schema history
        MigrateResult secondResult = runWithBaseline(config);
        assertTrue(secondResult.success, "Second run should succeed");
        assertEquals(0, secondResult.migrationsExecuted,
                "Second run must execute 0 migrations (idempotent)");
    }

    // =========================================================================
    // Test 6: custom baseline prefix ("X" instead of the default "B") must be
    //         honoured — X001 is renamed to V001, V002 runs afterwards.
    // =========================================================================
    @Test
    void customBaselinePrefix_isRespected() {
        FluentConfiguration config = configure(uniqueDbUrl(), "db/migration-custom-prefix");

        MigrateResult result = runWithBaseline(config, "X");

        assertTrue(result.success, "Migration should succeed");
        assertEquals(2, result.migrationsExecuted,
                "X001→V001 and V002 should both execute");

        var versions = result.migrations.stream()
                .map(m -> Integer.parseInt(m.version))
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(1, 2), versions,
                "Versions 1 (renamed from X001) and 2 should run in order");
    }
}
