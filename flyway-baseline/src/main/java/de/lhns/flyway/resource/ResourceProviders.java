package de.lhns.flyway.resource;

import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;

import java.util.Collection;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Helpers for obtaining and decorating a Flyway {@link ResourceProvider}.
 */
public final class ResourceProviders {
    private ResourceProviders() {
    }

    /**
     * Returns the configured {@link ResourceProvider}, or the one Flyway would build for itself
     * if none was set explicitly.
     * <p>
     * This is the only place in this library that reaches into Flyway's internals: there is no
     * public API for constructing the default provider. If a future Flyway release moves or
     * changes {@code org.flywaydb.core.internal.scanner.Scanner}, this method is the single
     * point to adapt.
     */
    public static ResourceProvider orDefault(final Configuration configuration) {
        final ResourceProvider resourceProvider = configuration.getResourceProvider();
        if (resourceProvider != null) {
            return resourceProvider;
        }
        return new Scanner<>(
                JavaMigration.class,
                configuration,
                configuration.getLocations()
        );
    }

    /**
     * Returns a provider that passes every resource it yields through {@code f}.
     */
    public static ResourceProvider map(final ResourceProvider resourceProvider, final UnaryOperator<LoadableResource> f) {
        return new ResourceProvider() {
            @Override
            public LoadableResource getResource(final String name) {
                final LoadableResource resource = resourceProvider.getResource(name);
                return resource == null ? null : f.apply(resource);
            }

            @Override
            public Collection<LoadableResource> getResources(final String prefix, final String[] suffixes) {
                return resourceProvider.getResources(prefix, suffixes).stream()
                        .map(f)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            }
        };
    }
}
