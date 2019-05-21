package ru.progrm_jarvis.catobot;

import lombok.NoArgsConstructor;
import ru.progrm_jarvis.catobot.ai.Recognizer;
import ru.progrm_jarvis.catobot.image.repository.CatImageRepository;
import ru.progrm_jarvis.catobot.vk.VkCatsManager;

/**
 * Cat'o'Bot base used as a basic objects for its API usage (including scripting API).
 */
public interface CatOBot {

    /**
     * Runs this Cat'o'Bot.
     *
     * @return {@link true} if the Cat'o'Bot should be restarted (using new instance) and {@link false} otherwise
     *
     * @apiNote call to this method is expected to get `stuck` in it so that the caller
     * gets access back only in case of end of bot stopping or in case of an exception
     */
    boolean run();

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

    /**
     * Shuts this bot instance down.
     */
    void shutdown();

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
