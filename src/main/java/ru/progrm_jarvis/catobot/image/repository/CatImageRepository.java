package ru.progrm_jarvis.catobot.image.repository;

import ru.progrm_jarvis.catobot.image.CatImage;

import java.util.Optional;
import java.util.concurrent.Future;

/**
 * Repository of cat images responsible for their storage and effective collecting.
 *
 * @param <I> type of cat images stored
 * @param <C> type of configuration required to work with cat images or {@link Void} if none is expected
 */
public interface CatImageRepository<I extends CatImage, C> {

    /**
     * Packs a random cat image from this repository.
     *
     * @param configuration configurations used to get a cat image
     * @return future returning {@link Optional} which should contain a cat image or be empty if something goes wrong
     */
    Future<Optional<I>> pickRandomCatImage(final C configuration);
}
