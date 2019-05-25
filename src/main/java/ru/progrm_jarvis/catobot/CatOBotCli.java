package ru.progrm_jarvis.catobot;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.callback.CallbackApi;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.LoggerFactory;
import ru.progrm_jarvis.catobot.ai.Recognizer;
import ru.progrm_jarvis.catobot.ai.WitAiRecognizer;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class CatOBotCli implements CatOBot {

    protected static final Gson CONFIG_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .create();

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

        shutdown = new AtomicBoolean();

        Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread(this::close));
    }

    public void run() {
        if (shutdown.get()) throw new AlreadyShutDownException("This CatOBot is already shut down");
        vk.startLongPolling();
    }

    @Override
    public void close() {
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

        boolean useSsl;
        @Builder.Default int imageFactoryWorkers = 0, vkApiWorkers = 0, recognizerWorkers = 0,
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

        @SerializedName("vk-handler") @NonNull @Builder.Default File vkHandlerFile = new File("handler/vk.groovy");
    }
}
