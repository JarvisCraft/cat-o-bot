package ru.progrm_jarvis.catobot.subscription;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

@Value
@FieldDefaults(level = AccessLevel.PROTECTED)
@NonFinal public class SimpleUser implements User {

    @NonNull UserManager manager;

    @NonNull String key;
    @NonNull JsonObject metadata;

    @Override
    public void save() {
        manager.store(this);
    }
}
