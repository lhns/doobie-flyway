package de.lhns.flyway.baseline;

import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;

import java.io.Reader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BaselineMigrations {

    private static final Pattern RESOURCE_NAME_PATTERN = Pattern.compile("(\\D+)(\\d+)__.*");
    private static final String DEFAULT_BASELINE_MIGRATION_PREFIX = "B";

    private BaselineMigrations() {}

    public static FluentConfiguration withBaselineMigrate(
            FluentConfiguration configuration,
            MigrationInfoService info) {
        return withBaselineMigrate(configuration, info, DEFAULT_BASELINE_MIGRATION_PREFIX);
    }

    public static FluentConfiguration withBaselineMigrate(
            FluentConfiguration configuration,
            MigrationInfoService info,
            String baselineMigrationPrefix) {

        final String firstAppliedScript = Arrays.stream(info.applied())
                .findFirst()
                .map(MigrationInfo::getScript)
                .orElse(null);

        final ResourceProvider resourceProvider = getResourceProviderOrDefault(configuration);
        final String sqlMigrationPrefix = configuration.getSqlMigrationPrefix();

        return configuration.resourceProvider(new ResourceProvider() {
            @Override
            public LoadableResource getResource(String name) {
                return resourceProvider.getResource(name);
            }

            @Override
            public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
                Collection<LoadableResource> allResources = resourceProvider.getResources("", suffixes);

                Map<String, List<Map.Entry<Integer, LoadableResource>>> resourcesByType = new HashMap<>();
                for (LoadableResource resource : allResources) {
                    Matcher matcher = RESOURCE_NAME_PATTERN.matcher(resource.getFilename());
                    String resourceType;
                    int version;
                    if (matcher.matches()) {
                        resourceType = matcher.group(1);
                        version = Integer.parseInt(matcher.group(2));
                    } else {
                        resourceType = "";
                        version = 0;
                    }
                    resourcesByType
                            .computeIfAbsent(resourceType, k -> new ArrayList<>())
                            .add(new AbstractMap.SimpleEntry<>(version, resource));
                }

                List<Map.Entry<Integer, LoadableResource>> baselineResources =
                        resourcesByType.getOrDefault(baselineMigrationPrefix, Collections.emptyList());

                Map.Entry<Integer, LoadableResource> latestBaseline;
                if (firstAppliedScript == null) {
                    latestBaseline = baselineResources.stream()
                            .max(Comparator.comparingInt(Map.Entry::getKey))
                            .orElse(null);
                } else {
                    latestBaseline = baselineResources.stream()
                            .filter(e -> e.getValue().getFilename().equals(firstAppliedScript))
                            .findFirst()
                            .orElse(null);
                }

                List<LoadableResource> newResources = new ArrayList<>();

                if (latestBaseline != null) {
                    newResources.add(baselineToNormalMigration(
                            latestBaseline.getValue(), sqlMigrationPrefix, baselineMigrationPrefix));
                }

                for (Map.Entry<Integer, LoadableResource> entry :
                        resourcesByType.getOrDefault(sqlMigrationPrefix, Collections.emptyList())) {
                    if (latestBaseline == null || entry.getKey() > latestBaseline.getKey()) {
                        newResources.add(entry.getValue());
                    }
                }

                for (Map.Entry<String, List<Map.Entry<Integer, LoadableResource>>> typeEntry :
                        resourcesByType.entrySet()) {
                    String type = typeEntry.getKey();
                    if (!type.equals(baselineMigrationPrefix) && !type.equals(sqlMigrationPrefix)) {
                        for (Map.Entry<Integer, LoadableResource> resource : typeEntry.getValue()) {
                            newResources.add(resource.getValue());
                        }
                    }
                }

                return newResources.stream()
                        .filter(r -> r.getFilename().startsWith(prefix))
                        .collect(Collectors.toList());
            }
        });
    }

    private static LoadableResource baselineToNormalMigration(
            LoadableResource resource,
            String sqlMigrationPrefix,
            String baselineMigrationPrefix) {
        return new LoadableResource() {
            @Override
            public Reader read() {
                return resource.read();
            }

            @Override
            public String getAbsolutePath() {
                return resource.getAbsolutePath();
            }

            @Override
            public String getAbsolutePathOnDisk() {
                return resource.getAbsolutePathOnDisk();
            }

            @Override
            public String getFilename() {
                return resource.getFilename().replaceFirst(
                        "^" + Pattern.quote(baselineMigrationPrefix),
                        Matcher.quoteReplacement(sqlMigrationPrefix)
                );
            }

            @Override
            public String getRelativePath() {
                return resource.getRelativePath();
            }
        };
    }

    private static ResourceProvider getResourceProviderOrDefault(FluentConfiguration configuration) {
        ResourceProvider provider = configuration.getResourceProvider();
        if (provider != null) {
            return provider;
        }
        // Fall back to Flyway's built-in Scanner as the default resource provider.
        // JavaMigration.class is the standard type parameter used when scanning for
        // SQL migration resources (as opposed to Java-based migration classes).
        return new Scanner<>(
                JavaMigration.class,
                configuration,
                configuration.getLocations()
        );
    }
}
