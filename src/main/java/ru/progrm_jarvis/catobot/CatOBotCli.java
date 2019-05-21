package ru.progrm_jarvis.catobot;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.callback.CallbackApi;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.Message;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import ru.progrm_jarvis.catobot.ai.Recognizer;
import ru.progrm_jarvis.catobot.ai.WitAiRecognizer;
import ru.progrm_jarvis.catobot.image.CatImage;
import ru.progrm_jarvis.catobot.image.TheCatApiCatImage;
import ru.progrm_jarvis.catobot.image.factory.TheCatApiCatImageFactory;
import ru.progrm_jarvis.catobot.image.repository.CatImageRepository;
import ru.progrm_jarvis.catobot.image.repository.PreLoadingCatImageRepository;
import ru.progrm_jarvis.catobot.vk.SimpleVkCatsManager;
import ru.progrm_jarvis.catobot.vk.VkCatsManager;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
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
public class CatOBotCli implements CatOBot {

    protected static final Gson CONFIG_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .create();

    @NonNull ExecutorService senderWorkers;
    @NonNull @Getter CatImageRepository<TheCatApiCatImage, TheCatApiCatImageFactory.Configuration> catImages;
    @NonNull @Getter VkCatsManager vk;
    @NonNull @Getter Recognizer recognizer;

    @NonNull AtomicBoolean shutdown;

    @Getter protected final Thread shutdownHook;

    public CatOBotCli() throws BotInitializationException {
        log.info("Loading config");
        final Config config;
        try {
            config = loadConfig(new File("config.json"));
        } catch (final IOException e) {
            throw new BotInitializationException("Unable to load bot config", e);
        }
        log.info("Config loaded:\n{}", CONFIG_GSON.toJson(config));

        final Function<CatOBot, CallbackApi> vkCallbackHandlerFactory;
        {
            val vkHandlerFile = config.getVkHandlerFile();

            val engine = new ScriptEngineManager()
                    .getEngineByExtension(FilenameUtils.getExtension(vkHandlerFile.getName()));
            if (engine == null) throw new BotInitializationException(
                    "Unknown script extension of VK-handler: `" + vkHandlerFile.getName() + "`"
            );

            val bindings = engine.createBindings();
            bindings.put("log", LoggerFactory.getLogger("VK-Handler"));

            try (val reader = new BufferedReader(new FileReader(vkHandlerFile))) {
                //noinspection unchecked
                vkCallbackHandlerFactory = (Function<CatOBot, CallbackApi>) engine.eval(reader, bindings);
            } catch (final IOException e) {
                throw new BotInitializationException("Unable to load VK-handler script", e);
            } catch (final ScriptException e) {
                throw new BotInitializationException("Unable to compile VK-handler script", e);
            } catch (final ClassCastException e) {
                throw new BotInitializationException("VK-handler should extend " + CallbackApi.class + " interface", e);
            }
        }
        log.info("Loaded callback-api handler {}", vkCallbackHandlerFactory);

        senderWorkers = createExecutorService(config.getSenderWorkers());

        log.info("Initializing cat images repository...");
        catImages = new PreLoadingCatImageRepository<>(
                new TheCatApiCatImageFactory(
                        config.getTheCatApiConfig(), HttpClients.createDefault(),
                        createExecutorService(config.getImageFactoryWorkers())
                ), null, config.getPreloadedImagesCacheSize(), config.getPreloadInterval());
        log.info("Initialized cat images repository: {}", catImages);

        log.info("Initializing recognizer...");
        recognizer = new WitAiRecognizer(
                HttpClients.createDefault(), createExecutorService(config.recognizerWorkers), config.getWitAiConfig()
        );
        log.info("Initialized recognizer: {}", recognizer);

        log.info("Initializing VK-manager...");
        vk = new SimpleVkCatsManager(
                config.getVkApiConfig(), createExecutorService(config.getVkApiWorkers()),
                vkCallbackHandlerFactory.apply(this)
        );
        log.info("Initialized VK-manager: {}", vk);
        vk.startLongPolling();

        shutdown = new AtomicBoolean(false);

        Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread(this::shutdown));
    }

    public boolean run() {
        if (shutdown.get()) throw new AlreadyShutDownException("This CatOBot is already shut down");

        val reader = new Scanner(System.in);
        while (true) switch (reader.nextLine()) {
            case "stop": case "end": {
                shutdown();

                return false;
            }
            case "reload": case "restart": {
                shutdown();

                return true;
            }
        }
    }

    @Override
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

    protected Config loadConfig(@NonNull final File file) throws IOException {
        if (file.isFile()) try (val reader = Files.newBufferedReader(file.toPath())) {
            return CONFIG_GSON.fromJson(reader, Config.class);
        } else {
            {
                val parent = file.getParentFile();
                if (parent != null) Files.createDirectories(parent.toPath());
            }
            val config = new Config();
            try (val writer = Files.newBufferedWriter(file.toPath())) {
                CONFIG_GSON.toJson(config, writer);
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
        @Builder.Default int senderWorkers = 0, imageFactoryWorkers = 0, vkApiWorkers = 0, recognizerWorkers = 0,
                preloadedImagesCacheSize = 100, preloadInterval = 1_000_000;
        @SerializedName("the-cat-api") @Builder.Default @NonNull TheCatApiCatImageFactory.Configuration theCatApiConfig
                = TheCatApiCatImageFactory.Configuration.builder().build();
        @SerializedName("vk-api") @Builder.Default @NonNull SimpleVkCatsManager.Configuration vkApiConfig
                = SimpleVkCatsManager.Configuration.builder()
                .groupToken("1234567890abcdef1234567890abcdef")
                .build();
        @SerializedName("wit-ai") @Builder.Default @NonNull WitAiRecognizer.Configuration witAiConfig
                = WitAiRecognizer.Configuration.builder()
                .userToken("1234567890abcdef1234567890abcdef")
                .build();
        @Builder.Default int maxImages = 10;
        @NonNull @Singular List<String> catAliases;

        @SerializedName("vk-handler") @NonNull @Builder.Default File vkHandlerFile = new File("handler/vk.groovy");

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
                val text = message.getText().toLowerCase();
                var matcher = generalPattern.matcher(text);
                if (matcher.find()) {
                    matcher = numberedPattern.matcher(text);
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

                    if (images.length == 0) vk.sendCatsUnavailable(message.getPeerId(), message.getId());
                    else vk.sendCatImages(message.getPeerId(), message.getId(), images);
                } catch (final RuntimeException | ClientException | ApiException | IOException e) {
                    log.error("An exception occurred while trying to respond to " + message, e);
                }
            });
        }
    }

    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    protected static class CallbackApiWrapper extends CallbackApi {

        @NonNull CallbackApi callbackApi;

        protected interface __Excludes {
            boolean parse(String json);

            boolean parse(JsonObject json);

            void messageNew(final Integer groupId, final String secret, final Message message);
        }
    }
}
