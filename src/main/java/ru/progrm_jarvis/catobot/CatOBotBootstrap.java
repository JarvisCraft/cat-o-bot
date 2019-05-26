package ru.progrm_jarvis.catobot;

import io.sentry.Sentry;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.fusesource.jansi.AnsiConsole;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

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

        AnsiConsole.systemInstall();
        final Terminal terminal;
        try {
            terminal = TerminalBuilder.terminal();
        } catch (final IOException e) {
            log.error("Unable to get Terminal", e);

            System.exit(-1);
            return;
        }

        val cli = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("CatOBot")
                .build();

        botSession: while (true) {
            // create new AutoCloseable instance of CatOBot
            try (val bot = new CatOBotCli()) {
                bot.run(); // run the Bot
                while (true) {
                    try {
                        // read line input
                        val line = cli.readLine();
                        // try to handle line input
                        switch (line.toLowerCase()) {
                            case "reload": case "restart": continue botSession;
                            case "stop": case "end": break botSession;
                        }
                    } catch (final UserInterruptException e) { // handle < [CTRL] + [C] >
                        log.info("The session has been interrupted, aborting");
                        break botSession;
                    }
                }
            } catch (final CatOBot.BotInitializationException e) {
                log.error("An exception occurred while initializing CatOBot, aborting", e);
                break;
            } catch (final Throwable e) {
                log.error("An exception occurred while running CatOBot, aborting", e);
            }
        }

        log.info("Disabled CatOBot, good night");
    }
}
