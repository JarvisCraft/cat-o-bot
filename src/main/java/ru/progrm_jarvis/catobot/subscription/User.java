package ru.progrm_jarvis.catobot.subscription;

import com.google.gson.JsonObject;

public interface User {

    UserManager getManager();

    String getKey();

    JsonObject getMetadata();

    void save();
}
