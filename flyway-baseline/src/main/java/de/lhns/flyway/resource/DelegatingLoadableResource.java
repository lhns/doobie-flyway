package de.lhns.flyway.resource;

import org.flywaydb.core.api.resource.LoadableResource;

import java.io.Reader;

/**
 * A {@link LoadableResource} that forwards every method to a wrapped resource.
 * <p>
 * {@code LoadableResource} is an abstract class with five members that almost every
 * decorator wants to pass through unchanged. Extending this class instead lets a decorator
 * override only the one method it actually cares about.
 */
public abstract class DelegatingLoadableResource extends LoadableResource {
    protected final LoadableResource delegate;

    protected DelegatingLoadableResource(final LoadableResource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Reader read() {
        return delegate.read();
    }

    @Override
    public boolean shouldStream() {
        return delegate.shouldStream();
    }

    @Override
    public String getAbsolutePath() {
        return delegate.getAbsolutePath();
    }

    @Override
    public String getAbsolutePathOnDisk() {
        return delegate.getAbsolutePathOnDisk();
    }

    @Override
    public String getFilename() {
        return delegate.getFilename();
    }

    @Override
    public String getRelativePath() {
        return delegate.getRelativePath();
    }
}
