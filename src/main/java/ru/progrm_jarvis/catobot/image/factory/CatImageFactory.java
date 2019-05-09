package ru.progrm_jarvis.catobot.image.factory;

import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import ru.progrm_jarvis.catobot.image.CatImage;

import java.util.Optional;
import java.util.concurrent.Future;

/**
 * Factory producing new cat images.
 *
 * @param <I> type of cat images produces
 * @param <C> configuration type of this cat image factory, should be {@link Void} if none is needed
 */
@FunctionalInterface
public interface CatImageFactory<I extends CatImage, C> extends AutoCloseable {

    /**
     * Creates new cat image using the given configuration.
     *
     * @param configuration configuration used for creating new cat image
     * @return future returning optional containing created a created cat image
     * or an empty one is it was impossible to create one
     *
     * @apiNote {@code configuration} may be {@link null} if type of configuration is {@link Void}
     */
    @NotNull Future<Optional<I>> createCatImage(@NonNull final C configuration);

    @Override
    default void close() {}
}
