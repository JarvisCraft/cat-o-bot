package ru.progrm_jarvis.catobot.image;

import org.jetbrains.annotations.NotNull;

/**
 * Image of a cat.
 */
public interface CatImage {

    /**
     * Gets the {@link byte} array representing this cat image.
     *
     * @return image object of a cat
     */
    @NotNull byte[] getImage();

    /**
     * Gets the type of this image such as {@code "png"}.
     *
     * @return type of this image
     */
    String getType();
}
