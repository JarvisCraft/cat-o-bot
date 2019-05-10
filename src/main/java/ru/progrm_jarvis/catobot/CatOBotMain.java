package ru.progrm_jarvis.catobot;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.callback.CallbackApi;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Message;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import ru.progrm_jarvis.catobot.image.CatImage;
import ru.progrm_jarvis.catobot.image.TheCatApiCatImage;
import ru.progrm_jarvis.catobot.image.factory.TheCatApiCatImageFactory;
import ru.progrm_jarvis.catobot.image.repository.CatImageRepository;
import ru.progrm_jarvis.catobot.image.repository.PreLoadingCatImageRepository;
import ru.progrm_jarvis.catobot.vk.SimpleVkCatsManager;
import ru.progrm_jarvis.catobot.vk.VkCatsManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.lang.Math.min;

@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class CatOBotMain {

    protected static final Gson PRETTY_GSON = new GsonBuilder()
                .setPrettyPrinting()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
                .create();

    @NonNull ExecutorService senderWorkers;
    @NonNull CatImageRepository<TheCatApiCatImage, TheCatApiCatImageFactory.Configuration> catImages;
    @NonNull VkCatsManager vk;

    @NonNull AtomicBoolean shutdown;

    @Getter protected final Thread shutdownHook;

    public CatOBotMain() throws IOException {
        log.info("Loading config");
        val config = loadConfig(new File("config.json"));
        log.info("Config loaded:\n{}", PRETTY_GSON.toJson(config));

        senderWorkers = createExecutorService(config.getSenderWorkers());

        log.info("Initializing cat images repository...");
        catImages = new PreLoadingCatImageRepository<>(
                new TheCatApiCatImageFactory(
                        config.getTheCatApiConfig(), HttpClients.createDefault(),
                        createExecutorService(config.getImageFactoryWorkers())
                ), null, config.getPreloadedImagesCacheSize(), config.getPreloadInterval());
        log.info("Initialized cat images repository: {}", catImages);

        log.info("Initializing VK-manager...");
        vk =  new SimpleVkCatsManager(
                config.getVkApiConfig(), createExecutorService(config.getVkApiWorkers()),
                new LongPollEventListener(config.createMessageToCatImagesCountFunction())
        );
        log.info("Initialized VK-manager: {}", vk);
        vk.startLongPolling();

        shutdown = new AtomicBoolean(false);

        Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread(this::shutdown));
    }

    protected void await() throws AlreadyShutDownException {
        if (shutdown.get()) throw new AlreadyShutDownException("This CatOBot is already shut down");

        try {
            val reader = new Scanner(System.in);
            while (true) if (reader.nextLine().equals("stop")) {
                shutdown();

                return;
            }
        } catch (final Throwable e) {
            log.error("An exception occurred while awaiting", e);
            shutdown();

            throw e;
        }
    }

    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            try {
                vk.close();
            } catch (final Throwable e) {
                log.error("An exception occurred while shutting down VK-manager", e);
            }
            try {
                catImages.close();
            } catch (final Throwable e) {
                log.error("An exception occurred while shutting down repository of cat images", e);
            }
            try {
                senderWorkers.shutdown();
            } catch (final Throwable e) {
                log.error("An exception occurred while shutting down sender workers", e);
            }

            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }

    public static void main(@NonNull final String... args) throws IOException {
        for (val arg : args) if (arg.toLowerCase().equals("--debug")) Configurator
                .setLevel(System.getProperty("log4j.logger"), Level.DEBUG);

        log.info("Stating new instance of Cat'O'Bot");
        new CatOBotMain().await();
        log.info("Cat'O'Bot has ended its job, goodbye <3");
        /*
        final int insuccessful;
        while (true) try {
            log.info("Stating new instance of Cat'O'Bot");
            new CatOBotMain().await();
            log.info("Cat'O'Bot has ended its job, goodbye <3");

            return;
        } catch (final AlreadyShutDownException | IOException expected) {
            // an expected exception restarts the bot
            log.error("An exception occurred, restarting", expected);
        }
         */
    }

    protected Config loadConfig(@NonNull final File file) throws IOException {
        if (file.isFile()) try (val reader = Files.newBufferedReader(file.toPath())) {
            return PRETTY_GSON.fromJson(reader, Config.class);
        } else {
            {
                val parent = file.getParentFile();
                if (parent != null) Files.createDirectories(parent.toPath());
            }
            val config = new Config();
            try (val writer = Files.newBufferedWriter(file.toPath())) {
                PRETTY_GSON.toJson(config, writer);
            }

            return config;
        }
    }

    /**
     * Creates an executor based on the given amount of workers.
     *
     * The following strategy is used for various inputs
     * <dt>0 workers</dt>
     * <dd>{@link Executors#newCachedThreadPool()}</dd>
     * <dt>1 worker</dt>
     * <dd>{@link Executors#newSingleThreadExecutor()}</dd>
     * <dt>2 or more workers</dt>
     * <dd>{@link Executors#newFixedThreadPool(int)}</dd>
     *
     * @param workers amount of workers to use or {@link 0} for unlimited pool
     * @return new executor based on the given amount of workers of it
     *
     * @throws IllegalArgumentException if the amount of workers is negative
     */
    protected static ExecutorService createExecutorService(final int workers) {
        if (workers < 0) throw new IllegalArgumentException("Number of workers cannot be negative");

        switch (workers) {
            case 0: return Executors.newCachedThreadPool();
            case 1: return Executors.newSingleThreadExecutor();
            default: return Executors.newFixedThreadPool(workers);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    protected static class Config {

        private static final byte MAX_CAT_COUNT_DECIMAL_DIGITS = 3;

        boolean useSsl;
        @Builder.Default int senderWorkers = 0, imageFactoryWorkers = 0, vkApiWorkers = 0,
                preloadedImagesCacheSize = 100, preloadInterval = 1_000_000;
        @SerializedName("the-cat-api") @Builder.Default @NonNull TheCatApiCatImageFactory.Configuration theCatApiConfig
                = TheCatApiCatImageFactory.Configuration.builder().build();
        @SerializedName("vk-api") @Builder.Default @NonNull SimpleVkCatsManager.Configuration vkApiConfig
                = SimpleVkCatsManager.Configuration.builder()
                .groupToken("1234567890abcdef1234567890abcdef")
                .build();
        @Builder.Default int maxImages = 10;
        @NonNull @Singular List<String> catAliases;

        public Function<Message, Integer> createMessageToCatImagesCountFunction() {
            val basePatternBuilder = new StringBuilder();
            val size = catAliases.size();
            for (var i = 0; i < size; i++) {
                if (i != 0) basePatternBuilder.append('|');
                basePatternBuilder.append('(').append('?').append(':').append(catAliases.get(i)).append(')');
            }
            val basePattern = basePatternBuilder.toString();

            final Pattern
                    generalPattern = Pattern.compile("\\b" + basePattern),
                    numberedPattern = Pattern.compile(
                            "\\b(\\d{1," + MAX_CAT_COUNT_DECIMAL_DIGITS + "})\\s(" + basePattern + ")"
                    );

            log.debug("Created pattern `" + generalPattern + "` for matching single cat requests");
            log.debug("Created pattern `" + numberedPattern + "` for matching numbered cat requests");

            return message -> {
                var matcher = generalPattern.matcher(message.getText());
                if (matcher.find()) {
                    matcher = numberedPattern.matcher(message.getText());
                    if (matcher.find()) return max(1, min(maxImages, Integer.parseInt(matcher.group(1))));
                    return 1;
                }

                return 0;
            };
        }
    }

    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    public class LongPollEventListener extends CallbackApi {

        @NonNull Function<Message, Integer> messageCountParser;

        @Override
        public void messageNew(final Integer groupId, final Message message) {
            log.debug("Received message: {} from {}", message.getId(), message.getPeerId());

            val imagesCount = messageCountParser.apply(message);
            if (imagesCount > 0) senderWorkers.submit(() -> {
                try {
                    val images = catImages.pickRandomCatImages(imagesCount, null)
                            .parallelStream()
                            .flatMap(imageFuture -> {
                                final Optional<TheCatApiCatImage> image;
                                try {
                                    image = imageFuture.get();
                                } catch (InterruptedException | ExecutionException e) {
                                    log.error("An exception occurred while getting one of the images", e);
                                    return Stream.empty();
                                }

                                return image.map(Stream::of).orElseGet(Stream::empty);
                            })
                            .toArray(CatImage[]::new);

                    if (images.length == 0) vk.sendCatsUnavailable(message.getPeerId());
                    else vk.sendCatImages(message.getPeerId(), images);
                } catch (final RuntimeException | ClientException | ApiException | IOException e) {
                    log.error("An exception occurred while trying to respond to " + message, e);
                }
            });
        }
    }

    @NoArgsConstructor
    protected static final class AlreadyShutDownException extends RuntimeException {

        public AlreadyShutDownException(final String message) {
            super(message);
        }

        public AlreadyShutDownException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public AlreadyShutDownException(final Throwable cause) {
            super(cause);
        }

        public AlreadyShutDownException(final String message, final Throwable cause, final boolean enableSuppression,
                                        final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
