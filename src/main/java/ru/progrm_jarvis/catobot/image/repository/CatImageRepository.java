package ru.progrm_jarvis.catobot.image.repository;

import lombok.val;
import lombok.var;
import ru.progrm_jarvis.catobot.image.CatImage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository of cat images responsible for their storage and effective collecting.
 *
 * @param <I> type of cat images stored
 * @param <C> type of configuration required to work with cat images or {@link Void} if none is expected
 */
public interface CatImageRepository<I extends CatImage, C> extends AutoCloseable {

    /**
     * Packs a random cat image from this repository.
     *
     * @param configuration configurations used to get a cat image
     * @return future returning {@link Optional} which should contain a cat image or be empty if something goes wrong
     */
    CompletableFuture<I> pickRandomCatImage(C configuration);

    /**
     * Packs random cat images from this repository.
     *
     * @param count amount of cat images to get
     * @param configuration configurations used to get a cat image
     * @return list of futures containing optional {@link CatImage}s
     *
     * @throws IllegalArgumentException if count is negative
     */
    default List<CompletableFuture<I>> pickRandomCatImages(final int count, final C configuration) {
        if (count < 0) throw new IllegalArgumentException("count should be non-negative");

        val catImages = new ArrayList<CompletableFuture<I>>(count);
        for (var i = 0; i < count; i++) catImages.add(pickRandomCatImage(configuration));

        return catImages;
    }

    @Override
    default void close() {}
}
