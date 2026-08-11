package de.lhns.flyway.resource;

import org.flywaydb.core.api.resource.LoadableResource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.function.UnaryOperator;

/**
 * Decorators for {@link LoadableResource}.
 */
public final class LoadableResources {
    private LoadableResources() {
    }

    /**
     * Returns a resource that reads through {@code f}, leaving its identity untouched.
     * <p>
     * The content is buffered into memory in order to apply {@code f}, so the result never
     * reports itself as streamable regardless of what the underlying resource says.
     */
    public static LoadableResource mapContent(final LoadableResource resource, final UnaryOperator<String> f) {
        return new DelegatingLoadableResource(resource) {
            @Override
            public Reader read() {
                return new StringReader(f.apply(readString(delegate.read())));
            }

            @Override
            public boolean shouldStream() {
                return false;
            }
        };
    }

    /**
     * Returns a resource whose {@link LoadableResource#getFilename() filename} is rewritten by
     * {@code f}. Everything else, including the relative path, is left as it was.
     */
    public static LoadableResource mapFilename(final LoadableResource resource, final UnaryOperator<String> f) {
        return new DelegatingLoadableResource(resource) {
            @Override
            public String getFilename() {
                return f.apply(delegate.getFilename());
            }
        };
    }

    private static String readString(final Reader reader) {
        final char[] buffer = new char[8 * 1024];
        final StringBuilder builder = new StringBuilder();
        try (reader) {
            int numCharsRead;
            while ((numCharsRead = reader.read(buffer, 0, buffer.length)) != -1) {
                builder.append(buffer, 0, numCharsRead);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return builder.toString();
    }
}
