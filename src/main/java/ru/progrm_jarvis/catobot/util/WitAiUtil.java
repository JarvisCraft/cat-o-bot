package ru.progrm_jarvis.catobot.util;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import ru.progrm_jarvis.catobot.ai.RecognitionResult;
import ru.progrm_jarvis.catobot.ai.WitAiRecognitionResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.time.LocalDateTime;

/**
 * Utility for <a href="https://wit.ai">WitAI</a> stuff.
 *
 * @see <a href="https://wit.ai/docs">docs of TheCatAPI</a>
 */
@UtilityClass
public class WitAiUtil {

    /**
     * Endpoint for HTTP-request for recognizing natural language messages
     */
    public final URI GET_MESSAGE_MEANING_ENDPOINT = URI.create("https://api.wit.ai/message"),
    /**
     * Endpoint for HTTP-request for recognizing natural speech
     */
    GET_SPEECH_MEANING_ENDPOINT = URI.create("https://api.wit.ai/speech");

    /**
     * {@link Gson GSON} instance used for mapping wit.ai JSON objects
     */
    private final Gson GSON = new Gson();

    /**
     * Gets the current API version. This may vary between calls.
     *
     * @return API-version actual at current moment
     */
    public String getCurrentApiVersion() {
        val currentDate = LocalDateTime.now();

        return Integer.toString(
                currentDate.getYear() * 1_00_00 + currentDate.getMonth().getValue() * 1_00 + currentDate.getDayOfMonth()
        );
    }

    @SneakyThrows(IOException.class)
    public RecognitionResult parseRecognitionResult(@NonNull final InputStream inputStream) {
        try (val reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return GSON.fromJson(reader, WitAiRecognitionResult.class);
        }
    }
}
