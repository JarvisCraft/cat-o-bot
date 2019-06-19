package ru.progrm_jarvis.catobot.subscription;

import lombok.NonNull;

import java.util.concurrent.CompletableFuture;

/**
 * Manager responsible for storing users.
 */
public interface UserManager extends AutoCloseable {

    CompletableFuture<Boolean> isPresent(String userKey);

    void store(@NonNull User user);

    CompletableFuture<User> getUser(String userKey);

    void unstore(@NonNull String userKey);
}
