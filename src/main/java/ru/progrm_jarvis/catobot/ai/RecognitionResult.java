package ru.progrm_jarvis.catobot.ai;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class RecognitionResult {

    @SerializedName("msg_id") @NonNull String messageId;

    @SerializedName("_text") @NonNull String text;

    @NonNull JsonObject entities;
}
