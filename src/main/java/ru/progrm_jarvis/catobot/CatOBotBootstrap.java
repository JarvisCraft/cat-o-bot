package ru.progrm_jarvis.catobot;

import io.sentry.Sentry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Bootstrap of a standard Cat'o'Bot instance.
 */
@Slf4j
public class CatOBotBootstrap {

    /**
     * Starts a Cat'o'Bot session which will attempt to restart it if exceptions occur while running.
     *
     * @param args command-line arguments passed
     */
    public static void main(@NonNull final String... args) {
        Sentry.init();

        CatOBot bot;
        while (true) {
            try {
                bot = new CatOBotCli();
            } catch (final Throwable e) {
                log.error("An exception occurred while creating a CatOBot instance, aborting", e);
                break;
            }

            try {
                if (bot.run()) continue;
                break;
            } catch (final RuntimeException e) {
                log.error("An exception occurred while running a CatOBot instance, restarting", e);
                break;
            }
        }

        log.info("Disabled CatOBot, good night");
    }
}
