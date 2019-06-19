package ru.progrm_jarvis.catobot.util;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import ru.progrm_jarvis.catobot.image.TheCatApiCatImage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

/**
 * Utility for <a href="https://thecatapi.com">TheCatApi</a> stuff.
 *
 * @see <a href="https://docs.thecatapi.com/">docs of TheCatAPI</a>
 */
@UtilityClass
public class TheCatApiUtil {

    /**
     * {@link Gson GSON} instance used for mapping TheCatApi JSON objects
     */
    private final Gson GSON = new Gson();

    /**
     * Minimal amount of cats returned
     */
    public final int MIN_CATS_PER_REQUEST = 1, MAX_CATS_PER_REQUEST = 100;

    public final String ROOT_API_ENDPOINT = "https://api.thecatapi.com/v1/";

    /**
     * Endpoint for HTTP-request for getting random cat images
     */
    public final URI
            RANDOM_CAT_IMAGE_REQUEST_ENDPOINT = URI.create(ROOT_API_ENDPOINT + "images/search"),
            UPLOAD_CAT_IMAGE_ENDPOINT = URI.create(ROOT_API_ENDPOINT + "images/upload");

    /**
     * Parsers the content of the specified data stream as a JSON-array of {@link TheCatApiCatImage}s.
     *
     * @param dataStream data stream of JSON-array of cat images
     * @return parsed cat image objects
     */
    @SneakyThrows(IOException.class)
    public TheCatApiCatImage[] parseCatImages(@NonNull final InputStream dataStream) {
        try (val reader = new BufferedReader(new InputStreamReader(dataStream))){
            return GSON.fromJson(reader, TheCatApiCatImage[].class);
        }
    }
}
