package ru.progrm_jarvis.catobot.image.factory;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.progrm_jarvis.catobot.image.TheCatApiCatImage;
import ru.progrm_jarvis.catobot.util.Images;
import ru.progrm_jarvis.catobot.util.TheCatApiUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Optional.empty;

/**
 * Cat image factory based on TheCatApi.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class TheCatApiCatImageFactory
        implements CatImageFactory<TheCatApiCatImage, TheCatApiCatImageFactory.Configuration> {

    /**
     * Default configuration to use whenever none is explicitly specified in methods
     */
    @NonNull Configuration defaultConfiguration;

    /**
     * HTTP-client to use for performing HTTP-requests to TheCatApi
     */
    @NonNull HttpClient httpClient;

    /**
     * Executor to use for performing async operations such as managing {@link CompletableFuture}s
     */
    @NonNull ExecutorService executor;

    /**
     * Cache of loaded but not yet used cat images
     */
    @NonNull Queue<TheCatApiCatImage> loadedImages = new ConcurrentLinkedDeque<>();

    @Override
    @NotNull public Future<Optional<TheCatApiCatImage>> createCatImage(@Nullable final Configuration configuration) {
        val config = configuration == null ? defaultConfiguration : configuration;
        return CompletableFuture.supplyAsync(() -> {
            val nextImage = loadedImages.poll();
            if (nextImage == null) {
                // prepare request
                final HttpGet getRequest;
                try {
                    getRequest = new HttpGet(new URIBuilder(TheCatApiUtil.RANDOM_CAT_IMAGE_REQUEST_ENDPOINT)
                            .addParameter("limit", Integer.toString(max(
                                    TheCatApiUtil.MIN_CATS_PER_REQUEST,
                                    min(TheCatApiUtil.MAX_CATS_PER_REQUEST, config.getImagesPerRequest())
                            )))
                            .build()
                    );
                } catch (URISyntaxException e) {
                    return empty();
                }
                val apiKey = config.getApiKey();
                if (apiKey != null) getRequest.setHeader("x-api-key", apiKey);
                getRequest.setHeader("User-Agent", "Cat'o'Bot");

                // perform the request
                TheCatApiCatImage[] images;
                try (val inputStream = httpClient.execute(getRequest).getEntity().getContent()) {
                    images = TheCatApiUtil.parseCatImages(inputStream);
                } catch (final IOException e) {
                    return empty();
                }

                // add factory to the images
                for (val image : images) image.setImageFactory(() -> {
                    val imageRequest = new HttpGet(image.getUrl());
                    if (apiKey != null) imageRequest.setHeader("x-api-key", apiKey);
                    imageRequest.setHeader("User-Agent", "Cat'o'Bot");

                    try (val inputStream = httpClient.execute(imageRequest).getEntity().getContent()) {
                        return IOUtils.toByteArray(inputStream);
                    } catch (final IOException e) {
                        return Images.getStubBytes();
                    }
                });

                val length = images.length;
                switch (length) {
                    case 0: return empty();
                    case 1: return Optional.of(images[0]);
                    default: {
                        loadedImages.addAll(Arrays.asList(Arrays.copyOfRange(images, 1, length - 1)));
                        return Optional.of(images[0]);
                    }
                }
            }

            return Optional.of(nextImage);
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    /**
     * Configuration for this cat image factory.
     */
    @Data
    @Builder
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    public static class Configuration {

        /**
         * API key for TheCatApi service
         */
        @Nullable String apiKey;

        /**
         * Amount of images which should be fetch per request to TheCatApi
         *
         * @apiNote when used with {@link #createCatImage(Configuration)}
         */
        int imagesPerRequest;
    }
}