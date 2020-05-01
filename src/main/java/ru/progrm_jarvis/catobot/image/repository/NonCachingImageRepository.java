package ru.progrm_jarvis.catobot.image.repository;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.progrm_jarvis.catobot.image.CatImage;
import ru.progrm_jarvis.catobot.image.factory.CatImageFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Cat image repository which does not cache images and simply delegated all job to its factory.
 *
 * @param <I> type of used cat image
 * @param <C> type of configuration used by the factory
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class NonCachingImageRepository<I extends CatImage, C> implements CatImageRepository<I, C> {

    /**
     * Factory used for creating cat images
     */
    @NonNull CatImageFactory<I, C> factory;

    @Override
    public CompletableFuture<I> pickRandomCatImage(final C configuration) {
        return factory.createCatImage(configuration);
    }

    @Override
    public void close() {
        factory.close();
    }
}
