package ru.progrm_jarvis.catobot.ai;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.InputStreamEntity;
import ru.progrm_jarvis.catobot.util.WitAiUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

/**
 * Recognizer based on <a href="https://wit.ai">WitAI</a> API.
 */
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class WitAiRecognizer implements Recognizer<WitAiRecognizer.Configuration> {

    /**
     * HTTP-client to use for requeSts to API
     */
    @NonNull final HttpClient httpClient;
    /**
     * Executor used for async operations
     */
    @NonNull final ExecutorService executor;
    /**
     * Default configuration to use whenever none is provided when needed
     */
    @NonNull final Configuration defaultConfiguration;

    @Override
    public CompletableFuture<Optional<RecognitionResult>> recognizeMessage(@NonNull final String message,
                                                                           final Configuration configuration) {
        val config = configuration == null ? defaultConfiguration : configuration;
        return CompletableFuture.supplyAsync(() -> {
            final HttpGet request;
            try {
                request = new HttpGet(
                        new URIBuilder(WitAiUtil.GET_MESSAGE_MEANING_ENDPOINT)
                                .setParameter("v", WitAiUtil.getCurrentApiVersion())
                                .setParameter("q", message)
                                .build()
                );
            } catch (URISyntaxException e) {
                throw new RuntimeException("Unable to create URI for wit.ai request", e);
            }
            request.setHeader("Authorization", config.getFullUserToken());

            try (val response = httpClient.execute(request).getEntity().getContent()) {
                return ofNullable(WitAiUtil.parseRecognitionResult(response));
            } catch (final IOException e) {
                log.debug("An exception occurred while trying to recognize a message", e);

                return empty();
            }

        }, executor);
    }

    @Override
    public CompletableFuture<Optional<RecognitionResult>> recognizeSpeech(@NonNull final InputStream mp3Stream,
                                                                          final Configuration configuration) {
        val config = configuration == null ? defaultConfiguration : configuration;
        return CompletableFuture.supplyAsync(() -> {
            final HttpPost request;
            try {
                request = new HttpPost(
                        new URIBuilder(WitAiUtil.GET_SPEECH_MEANING_ENDPOINT)
                                .setParameter("v", WitAiUtil.getCurrentApiVersion())
                                .build()
                );
            } catch (URISyntaxException e) {
                throw new RuntimeException("Unable to create URI for wit.ai request", e);
            }
            request.setHeader("Authorization", config.getFullUserToken());
            request.setHeader("Content-Type", "audio/mpeg3");
            request.setEntity(new InputStreamEntity(mp3Stream));

            try (val response = httpClient.execute(request).getEntity().getContent()) {
                return ofNullable(WitAiUtil.parseRecognitionResult(response));
            } catch (final IOException e) {
                log.debug("An exception occurred while trying to recognize a message", e);

                return empty();
            }

        }, executor);
    }

    /**
     * Configuration of {@link WitAiRecognizer}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Configuration {

        @Builder.Default @NonNull String userToken = "1234567890abcdef";
        @Builder.Default String apiVersion = WitAiUtil.getCurrentApiVersion();

        public String getFullUserToken() {
            return "Bearer " + getUserToken();
        }
    }
}
