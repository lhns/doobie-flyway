package de.lhns.flyway.baseline;

import de.lhns.flyway.resource.LoadableResources;
import de.lhns.flyway.resource.ResourceProviders;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.resource.LoadableResource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Squashed baseline migrations for Flyway.
 * <p>
 * A {@code Bnnn__name.sql} resource is a full schema snapshot equivalent to applying every
 * {@code Vnnn} migration up to and including version {@code nnn}. {@link #withBaselineMigrate}
 * wraps the configured {@link ResourceProvider} so that a fresh database applies the snapshot
 * in one step, while databases that already have a schema history carry on exactly as before.
 * <p>
 * Flyway itself is never told that baselines exist; it only ever sees a rewritten resource list.
 * The full algorithm and the reasoning behind it are recorded in
 * {@code docs/adr/0001-baseline-migrations.md}.
 */
public final class BaselineMigrations {
    /**
     * Matches {@code <prefix><version>__<description>}, e.g. {@code B002__snapshot.sql}.
     * Names that do not match — repeatable {@code R__} migrations and anything else — are
     * passed through untouched.
     */
    private static final Pattern RESOURCE_NAME = Pattern.compile("(\\D+)(\\d+)__.*");

    private static final String DEFAULT_BASELINE_MIGRATION_PREFIX = "B";

    private BaselineMigrations() {
    }

    /**
     * Equivalent to {@link #withBaselineMigrate(FluentConfiguration, MigrationInfoService, String)}
     * with the default baseline prefix {@code "B"}.
     */
    public static FluentConfiguration withBaselineMigrate(final FluentConfiguration configuration,
                                                          final MigrationInfoService info) {
        return withBaselineMigrate(configuration, info, DEFAULT_BASELINE_MIGRATION_PREFIX);
    }

    /**
     * Returns {@code configuration} with a {@link ResourceProvider} that resolves baseline
     * migrations.
     *
     * @param info                    the migration info of the target database, as returned by
     *                                {@code Flyway.info()} on the unmodified configuration.
     *                                Its first applied migration decides which baseline, if any,
     *                                this database is pinned to.
     * @param baselineMigrationPrefix the filename prefix marking a baseline migration
     */
    public static FluentConfiguration withBaselineMigrate(final FluentConfiguration configuration,
                                                          final MigrationInfoService info,
                                                          final String baselineMigrationPrefix) {
        final MigrationInfo[] applied = info.applied();
        final String firstAppliedScript = applied.length == 0 ? null : applied[0].getScript();

        final ResourceProvider resourceProvider = ResourceProviders.orDefault(configuration);
        final String sqlMigrationPrefix = configuration.getSqlMigrationPrefix();

        return configuration.resourceProvider(new ResourceProvider() {
            @Override
            public LoadableResource getResource(final String name) {
                return resourceProvider.getResource(name);
            }

            @Override
            public Collection<LoadableResource> getResources(final String prefix, final String[] suffixes) {
                // Deliberately unfiltered: asking the delegate for `prefix` would drop the
                // baseline resources before we get a chance to rename them.
                final Map<String, List<VersionedResource>> resourcesByType =
                        groupByType(resourceProvider.getResources("", suffixes));

                final VersionedResource baseline = selectBaseline(
                        resourcesByType.getOrDefault(baselineMigrationPrefix, List.of()),
                        firstAppliedScript
                );

                final List<LoadableResource> result = new ArrayList<>();

                if (baseline != null) {
                    result.add(LoadableResources.mapFilename(baseline.resource, filename ->
                            filename.replaceFirst(
                                    "^" + Pattern.quote(baselineMigrationPrefix),
                                    Matcher.quoteReplacement(sqlMigrationPrefix)
                            )
                    ));
                }

                for (final VersionedResource versioned : resourcesByType.getOrDefault(sqlMigrationPrefix, List.of())) {
                    // Everything the baseline already covers is dropped; without a baseline the
                    // full history is kept.
                    if (baseline == null || versioned.version > baseline.version) {
                        result.add(versioned.resource);
                    }
                }

                for (final Map.Entry<String, List<VersionedResource>> entry : resourcesByType.entrySet()) {
                    if (entry.getKey().equals(baselineMigrationPrefix) || entry.getKey().equals(sqlMigrationPrefix)) {
                        continue;
                    }
                    for (final VersionedResource versioned : entry.getValue()) {
                        result.add(versioned.resource);
                    }
                }

                result.removeIf(resource -> !resource.getFilename().startsWith(prefix));
                return result;
            }
        });
    }

    private static Map<String, List<VersionedResource>> groupByType(final Collection<LoadableResource> resources) {
        final Map<String, List<VersionedResource>> resourcesByType = new HashMap<>();
        for (final LoadableResource resource : resources) {
            final Matcher matcher = RESOURCE_NAME.matcher(resource.getFilename());
            String type = "";
            int version = 0;
            if (matcher.matches()) {
                try {
                    version = Integer.parseInt(matcher.group(2));
                    type = matcher.group(1);
                } catch (final NumberFormatException e) {
                    // Not a version we can order; treat the resource as a passthrough.
                    version = 0;
                }
            }
            resourcesByType
                    .computeIfAbsent(type, key -> new ArrayList<>())
                    .add(new VersionedResource(version, resource));
        }
        return resourcesByType;
    }

    /**
     * Picks the baseline this database is pinned to.
     * <p>
     * On an empty schema history the newest baseline wins. Once a history exists the baseline is
     * decided by the migration that created it, so a database bootstrapped from {@code B002}
     * keeps resolving {@code B002} even after a newer {@code B003} is added — its recorded
     * version and checksum stay valid. A history that starts with a plain versioned migration
     * matches nothing, and that database keeps replaying its full history.
     */
    private static VersionedResource selectBaseline(final List<VersionedResource> baselines,
                                                    final String firstAppliedScript) {
        if (firstAppliedScript == null) {
            return baselines.stream().max(Comparator.comparingInt(versioned -> versioned.version)).orElse(null);
        }
        return baselines.stream()
                .filter(versioned -> isScript(versioned.resource, firstAppliedScript))
                .findFirst()
                .orElse(null);
    }

    /**
     * Whether {@code script}, as recorded in Flyway's schema history, refers to {@code resource}.
     * <p>
     * Flyway records the resource's relative path, but historically this comparison was made
     * against the bare filename. Both are accepted so that databases migrated by either version
     * of this library keep resolving to the same baseline.
     */
    private static boolean isScript(final LoadableResource resource, final String script) {
        final String normalized = script.replace('\\', '/');
        final String relativePath = resource.getRelativePath().replace('\\', '/');
        final String filename = resource.getFilename();
        return normalized.equals(relativePath)
                || normalized.endsWith("/" + relativePath)
                || normalized.equals(filename)
                || normalized.endsWith("/" + filename);
    }

    private static final class VersionedResource {
        final int version;
        final LoadableResource resource;

        VersionedResource(final int version, final LoadableResource resource) {
            this.version = version;
            this.resource = resource;
        }
    }
}
