package ru.progrm_jarvis.catobot.ai;

import com.google.gson.JsonObject;

/**
 * Result of recognition containing its entities.
 */
@FunctionalInterface
public interface RecognitionResult {

    /**
     * JSON-object representing data recognized.
     *
     * @return recognized data in form of JSON-object
     */
    JsonObject getEntities();
}
