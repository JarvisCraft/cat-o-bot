package ru.progrm_jarvis.catobot.subscription;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class RedisUserManager implements UserManager {

    @NonNull protected static final Gson GSON = new Gson();

    @NonNull ExecutorService executor;
    @NonNull Jedis jedis;
    @NonNull String userPrefix;

    public RedisUserManager(@NonNull final ExecutorService executor, @NonNull final Configuration configuration) {
        this(
                executor,
                new Jedis(configuration.getHosts().iterator().next()),
                configuration.getPrefix()
        );
    }

    @Override
    public CompletableFuture<Boolean> isPresent(final String userKey) {
        return CompletableFuture.supplyAsync(() -> jedis.exists(userKey), executor);
    }

    @Override
    public void store(@NonNull final User user) {
        jedis.set(user.getKey(), GSON.toJson(user.getMetadata()));
    }

    @Override
    public CompletableFuture<User> getUser(@NonNull final String userKey) {
        return CompletableFuture.supplyAsync(() -> {
            val key = userPrefix + userKey;

            val stored = jedis.get(key);
            if (stored == null) {
                val user = new SimpleUser(this, userKey, new JsonObject());
                store(user);

                return user;
            }

            return new SimpleUser(
                    this, userKey, GSON.fromJson(stored, JsonObject.class)
            );
        }, executor);
    }

    @Override
    public void unstore(@NonNull final String userKey) {
        executor.submit(() -> jedis.del(userPrefix + userKey));
    }

    @Override
    public void close() {
        executor.shutdown(); // should shutdown safely
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Configuration {

        @Builder.Default Set<HostAndPort> hosts = new HashSet<>(
                Collections.singleton(new HostAndPort("localhost", 6379))
        );

        @Builder.Default String prefix = "User.";

        @Builder.Default GenericObjectPoolConfig poolConfig = new JedisPoolConfig();
    }
}
