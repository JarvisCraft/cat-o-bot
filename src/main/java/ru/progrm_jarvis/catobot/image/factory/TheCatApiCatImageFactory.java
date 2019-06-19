package ru.progrm_jarvis.catobot.image.factory;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.progrm_jarvis.catobot.image.TheCatApiCatImage;
import ru.progrm_jarvis.catobot.util.Images;
import ru.progrm_jarvis.catobot.util.TheCatApiUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Cat image factory based on TheCatApi.
 */
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class TheCatApiCatImageFactory
        implements CatImageFactory<TheCatApiCatImage, TheCatApiCatImageFactory.Configuration>,
        CatImageSharer<TheCatApiCatImageFactory.Configuration> {

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
    @NotNull public CompletableFuture<TheCatApiCatImage> createCatImage(@Nullable final Configuration configuration) {
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
                } catch (final URISyntaxException e) {
                    throw new RuntimeException("An exception occurred while creating a URI for loading cat images", e);
                }
                val apiKey = config.getApiKey();
                if (apiKey != null) getRequest.setHeader("x-api-key", apiKey);
                getRequest.setHeader("User-Agent", "Cat'o'Bot");

                // perform the request
                TheCatApiCatImage[] images;
                try (val inputStream = httpClient.execute(getRequest).getEntity().getContent()) {
                    images = TheCatApiUtil.parseCatImages(inputStream);
                } catch (final IOException e) {
                    throw new RuntimeException("An exception occurred while loading cat images", e);
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
                log.debug("Loaded {} cat images: {}", length, images);
                switch (length) {
                    case 0: throw new RuntimeException(
                            "Unable to load cat image, an empty array was returned by TheCatApi"
                    );
                    case 1: return images[0];
                    default: {
                        loadedImages.addAll(Arrays.asList(Arrays.copyOfRange(images, 1, length - 1)));
                        return images[0];
                    }
                }
            }

            return nextImage;
        }, executor);
    }

    @Override
    @NotNull public CompletableFuture<Void> shareCatImage(@NonNull final Configuration configuration,
                                                          @NonNull final InputStream imageInputStream) {
        val config = configuration == null ? defaultConfiguration : configuration;
        return CompletableFuture.supplyAsync(() -> {
            // prepare request
            final HttpPost postRequest;
            postRequest = new HttpPost(TheCatApiUtil.UPLOAD_CAT_IMAGE_ENDPOINT);
            postRequest.setEntity(new InputStreamEntity(imageInputStream));
            val apiKey = config.getApiKey();
            if (apiKey != null) postRequest.setHeader("x-api-key", apiKey);
            postRequest.setHeader("User-Agent", "Cat'o'Bot");

            try (val response = httpClient.execute(postRequest).getEntity().getContent()) {
                log.debug(
                        "Uploaded image, response:\n{}",
                        String.join("\n", IOUtils.readLines(response, StandardCharsets.UTF_8))
                );
            } catch (final IOException e) {
                throw new RuntimeException("An exception occurred while trying to upload a cat image", e);
            }

            return null; // because Void
        }, executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
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
