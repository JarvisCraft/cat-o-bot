package ru.progrm_jarvis.catobot;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.vk.api.sdk.callback.CallbackApi;
import lombok.*;
import lombok.Builder.Default;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.impl.client.HttpClients;
import ru.progrm_jarvis.catobot.ai.Recognizer;
import ru.progrm_jarvis.catobot.ai.WitAiRecognizer;
import ru.progrm_jarvis.catobot.image.TheCatApiCatImage;
import ru.progrm_jarvis.catobot.image.factory.TheCatApiCatImageFactory;
import ru.progrm_jarvis.catobot.image.repository.CatImageRepository;
import ru.progrm_jarvis.catobot.image.repository.PreLoadingCatImageRepository;
import ru.progrm_jarvis.catobot.subscription.RedisUserManager;
import ru.progrm_jarvis.catobot.subscription.UserManager;
import ru.progrm_jarvis.catobot.vk.SimpleVkCatsManager;
import ru.progrm_jarvis.catobot.vk.VkCatsManager;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class SimpleCatOBot implements CatOBot {

    protected static final Gson CONFIG_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_DASHES)
            .create();

    @NonNull @Getter ScheduledExecutorService scheduler;
    @NonNull @Getter UserManager userManager;
    @NonNull @Getter CatImageRepository<TheCatApiCatImage, TheCatApiCatImageFactory.Configuration> catImages;
    @NonNull @Getter VkCatsManager vk;
    @NonNull @Getter Recognizer recognizer;

    @NonNull @Getter EventHandler eventHandler;

    @NonNull AtomicBoolean shutdown;

    @Getter protected final Thread shutdownHook;

    public SimpleCatOBot() throws BotInitializationException {
        log.info("Loading config");
        final Config config;
        try {
            config = loadConfig(new File("config.json"));
        } catch (final IOException e) {
            throw new BotInitializationException("Unable to load bot config", e);
        }
        log.info("Config loaded:\n{}", CONFIG_GSON.toJson(config));

        log.info("Loading scheduler");
        scheduler = createScheduledExecutorService(config.getSchedulerWorkers(), false);
        log.info("Loaded scheduler: {}", scheduler);

        log.info("Loading event handler");
        {
            val scriptFile = config.getEventHandlerFile();
            if (scriptFile == null || !scriptFile.isFile()) eventHandler = EventHandler.getStub();
            else eventHandler = SimpleCatOBot.<Function<CatOBot, EventHandler>>loadScript(scriptFile).apply(this);
        }
        log.info("Loaded event handler: {}", eventHandler);

        log.info("Loading user-manager");
        userManager = new RedisUserManager(
                createExecutorService(config.getUserManagerWorkers(), false),
                config.getRedisUserManagerConfig()
        );
        log.info("Loaded user-manager: {}", userManager);

        log.info("Loading callback-api handler");
        final Function<CatOBot, CallbackApi> vkCallbackHandlerFactory = loadScript(config.getVkHandlerFile());
        log.info("Loaded callback-api handler {}", vkCallbackHandlerFactory);

        log.info("Initializing cat images repository...");
        catImages = new PreLoadingCatImageRepository<>(
                new TheCatApiCatImageFactory(
                        config.getTheCatApiConfig(), HttpClients.createDefault(),
                        createExecutorService(config.getImageFactoryWorkers(), true)
                ), null, config.getPreloadedImagesCacheSize(), config.getPreloadInterval());
        log.info("Initialized cat images repository: {}", catImages);

        log.info("Initializing recognizer...");
        recognizer = new WitAiRecognizer(
                HttpClients.createDefault(),
                createExecutorService(config.recognizerWorkers, true),
                config.getWitAiConfig()
        );
        log.info("Initialized recognizer: {}", recognizer);

        log.info("Initializing VK-manager...");
        vk = new SimpleVkCatsManager(
                config.getVkApiConfig(), createExecutorService(config.getVkApiWorkers(), true),
                vkCallbackHandlerFactory.apply(this)
        );
        log.info("Initialized VK-manager: {}", vk);

        shutdown = new AtomicBoolean();

        Runtime.getRuntime().addShutdownHook(shutdownHook = new Thread(this::close));
    }

    protected static <T> T loadScript(@NonNull final File scriptFile) {
        final ScriptEngine engine;
        {
            val fileName = scriptFile.getName();
            engine = new ScriptEngineManager()
                    .getEngineByExtension(FilenameUtils.getExtension(fileName));
            if (engine == null) throw new BotInitializationException(
                    "Unknown script extension of VK-handler: `" + fileName + "`"
            );
        }

        val bindings = engine.createBindings();

        try (val reader = new BufferedReader(new FileReader(scriptFile))) {
            //noinspection unchecked
            return (T) engine.eval(reader, bindings);
        } catch (final IOException e) {
            throw new BotInitializationException("Unable to load script", e);
        } catch (final ScriptException e) {
            throw new BotInitializationException("Unable to compile script", e);
        } catch (final ClassCastException e) {
            throw new BotInitializationException(
                    "Object provided by script should be of other type", e
            );
        }
    }

    public void run() {
        if (shutdown.get()) throw new AlreadyShutDownException("This CatOBot is already shut down");
        vk.startLongPolling();

        eventHandler.onEnable();
    }

    @Override
    public void close() {
        if (shutdown.compareAndSet(false, true)) {
            eventHandler.onDisable();

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
     * @param daemon {@link true} if the created thread should be daemons and {@link false} otherwise
     * @return new executor based on the given amount of workers of it
     *
     * @throws IllegalArgumentException if the amount of workers is negative
     */
    protected static ExecutorService createExecutorService(final int workers,
                                                           boolean daemon) {
        if (workers < 0) throw new IllegalArgumentException("Number of workers cannot be negative");

        final ThreadFactory threadFactory;
        val defaultThreadFactory = Executors.defaultThreadFactory();
        threadFactory = task -> {
            val thread = defaultThreadFactory.newThread(task);
            if (thread.isDaemon() != daemon) thread.setDaemon(daemon);

            return thread;
        };

        switch (workers) {
            case 0: return Executors.newCachedThreadPool(threadFactory);
            case 1: return Executors.newSingleThreadExecutor(threadFactory);
            default: return Executors.newFixedThreadPool(workers, threadFactory);
        }
    }

    /**
     * Creates a scheduled executor based on the given amount of workers.
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
     * @param daemon {@link true} if the created thread should be daemons and {@link false} otherwise
     * @return new scheduled executor based on the given amount of workers of it
     *
     * @throws IllegalArgumentException if the amount of workers is negative
     */
    protected static ScheduledExecutorService createScheduledExecutorService(final int workers,
                                                                    boolean daemon) {
        if (workers < 0) throw new IllegalArgumentException("Number of workers cannot be negative");

        final ThreadFactory threadFactory;
        val defaultThreadFactory = Executors.defaultThreadFactory();
        threadFactory = task -> {
            val thread = defaultThreadFactory.newThread(task);
            if (thread.isDaemon() != daemon) thread.setDaemon(daemon);

            return thread;
        };

        if (workers == 1) return Executors.newSingleThreadScheduledExecutor(threadFactory);
        return Executors.newScheduledThreadPool(workers, threadFactory);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    protected static class Config {

        boolean useSsl;
        @Default int schedulerWorkers = 0,
                userManagerWorkers = 0, imageFactoryWorkers = 0, vkApiWorkers = 0, recognizerWorkers = 0,
                preloadedImagesCacheSize = 100, preloadInterval = 1_000_000;

        @SerializedName("redis-user-manager") @Default @NonNull RedisUserManager.Configuration redisUserManagerConfig
                = RedisUserManager.Configuration.builder().build();

        @SerializedName("the-cat-api") @Default @NonNull TheCatApiCatImageFactory.Configuration theCatApiConfig
                = TheCatApiCatImageFactory.Configuration.builder().build();

        @SerializedName("vk-api") @Default @NonNull SimpleVkCatsManager.Configuration vkApiConfig
                = SimpleVkCatsManager.Configuration.builder()
                .groupToken("1234567890abcdef1234567890abcdef")
                .build();

        @SerializedName("wit-ai") @Default @NonNull WitAiRecognizer.Configuration witAiConfig
                = WitAiRecognizer.Configuration.builder()
                .userToken("1234567890abcdef1234567890abcdef")
                .build();

        @SerializedName("event-handler") @NonNull @Default File eventHandlerFile
                = new File("scripts/event-handler.groovy");
        @SerializedName("vk-handler") @NonNull @Default File vkHandlerFile
                = new File("scripts/vk-handler.groovy");
    }
}
