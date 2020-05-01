package ru.progrm_jarvis.catobot.image.factory;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for sharing cat images.
 *
 * @param <C> configuration type of this cat image factory, should be {@link Void} if none is needed
 */
@FunctionalInterface
public interface CatImageSharer<C> extends AutoCloseable {

    @NotNull CompletableFuture<Void> shareCatImage(@NonNull final C configuration,
                                                   @NonNull final InputStream imageInputStream);

    @Override
    default void close() {}
}
