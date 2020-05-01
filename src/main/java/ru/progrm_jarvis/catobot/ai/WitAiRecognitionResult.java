package ru.progrm_jarvis.catobot.ai;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Recognizer result specific fot <a href="https://wit.ai">WitAI</a>.
 */
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class WitAiRecognitionResult implements RecognitionResult {

    @SerializedName("msg_id") @NonNull String messageId;

    @SerializedName("_text") @NonNull String text;

    @NonNull JsonObject entities;
}
