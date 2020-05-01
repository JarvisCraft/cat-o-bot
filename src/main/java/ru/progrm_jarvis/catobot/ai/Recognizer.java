package ru.progrm_jarvis.catobot.ai;

import lombok.NonNull;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Base for AI-related transformations of human-data to digital data.
 * This includes text recognition and speech recognition.
 *
 * @param <C> configuration type for API-method calls
 */
public interface Recognizer<C> extends AutoCloseable {

    /**
     * Recognizes the specified user message.
     *
     * @param message message to recognize
     * @return result of method recognition
     */
    CompletableFuture<Optional<RecognitionResult>> recognizeMessage(@NonNull String message, C configuration);

    /**
     * Recognizes the specified user speech.
     *
     * @param mp3Stream input-stream of audio-message
     * @return result of method recognition
     */
    CompletableFuture<Optional<RecognitionResult>> recognizeSpeech(@NonNull InputStream mp3Stream, C configuration);
}
