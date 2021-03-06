package ru.progrm_jarvis.catobot;

import lombok.NoArgsConstructor;
import ru.progrm_jarvis.catobot.ai.Recognizer;
import ru.progrm_jarvis.catobot.image.factory.CatImageSharer;
import ru.progrm_jarvis.catobot.image.repository.CatImageRepository;
import ru.progrm_jarvis.catobot.subscription.UserManager;
import ru.progrm_jarvis.catobot.vk.VkCatsManager;

import java.util.concurrent.ScheduledExecutorService;

/**
 * Cat'o'Bot base used as a basic objects for its API usage (including scripting API).
 */
public interface CatOBot extends Runnable, AutoCloseable {

    /**
     * Gets {@link ScheduledExecutorService scheduler} of this bot.
     *
     * @return scheduler of this bot
     */
    ScheduledExecutorService getScheduler();

    /**
     * Gets {@link EventHandler event handler} of this bot.
     *
     * @return event handler of this bot
     */
    EventHandler getEventHandler();

    /**
     * Gets {@link UserManager} of this bot.
     *
     * @return user-manager of this bot
     */
    UserManager getUserManager();

    CatImageSharer getCatImageSharer();

    /**
     * Gets {@link CatImageRepository} of this bot.
     *
     * @return cat-image repository of this bot
     */
    CatImageRepository getCatImages();

    /**
     * Gets {@link VkCatsManager} of this bot.
     *
     * @return vk-cats manager of this bot
     */
    VkCatsManager getVk();

    /**
     * Gets {@link Recognizer} of this bot.
     *
     * @return recognizer of this bot
     */
    Recognizer getRecognizer();

    interface EventHandler {

        static EventHandler getStub() {
            return EventHandler.StubHolder.STUB;
        }

        default void onEnable() {}

        default void onDisable() {}

        default void handle(String eventType, Object event) {}

        enum StubHolder implements EventHandler {
            STUB {
                @Override
                public String toString() {
                    return "EventHandler Stub";
                }
            }
        }
    }

    interface CatOBotEventHandler extends AutoCloseable {

        static CatOBotEventHandler getStub() {
            return StubHolder.STUB;
        }

        default void onEnable() {}

        default void onDisable() {}

        default void close() {}

        enum StubHolder implements CatOBotEventHandler {
            STUB
        }
    }

    /**
     * An exception thrown whenever an attempt to shut down an already shut down bot happens.
     */
    @NoArgsConstructor
    class AlreadyShutDownException extends RuntimeException {

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

    /**
     * An exception occurring while initializing a bot.
     */
    @NoArgsConstructor
    class BotInitializationException extends RuntimeException {

        public BotInitializationException(final String message) {
            super(message);
        }

        public BotInitializationException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public BotInitializationException(final Throwable cause) {
            super(cause);
        }

        public BotInitializationException(final String message, final Throwable cause, final boolean enableSuppression,
                                          final boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
