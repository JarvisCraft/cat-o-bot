package ru.progrm_jarvis.catobot.vk;

import com.vk.api.sdk.callback.CallbackApi;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.exceptions.LongPollServerKeyExpiredException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.groups.LongPollServer;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.core.util.JsonUtils;
import org.jetbrains.annotations.NotNull;
import ru.progrm_jarvis.catobot.image.CatImage;
import ru.progrm_jarvis.catobot.util.CollectionUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class SimpleVkCatsManager implements VkCatsManager {

    @NonNull Configuration configuration;
    @NonNull ExecutorService executor;
    @NonNull VkApiClient vk;
    @NonNull CallbackApi longPollEventHandler;
    @NonNull GroupActor groupActor;

    @NonNull AtomicBoolean longPollingSession;

    public SimpleVkCatsManager(@NonNull final Configuration configuration,
                               @NonNull final ExecutorService executor,
                               @NonNull final CallbackApi longPollEventHandler) {
        this.configuration = configuration;
        this.executor = executor;
        this.longPollEventHandler = longPollEventHandler;

        vk = new VkApiClient(new HttpTransportClient());
        groupActor = new GroupActor(configuration.getGroupId(), configuration.getGroupToken());

        longPollingSession = new AtomicBoolean();
    }

    /**
     * Gets new data for long-poll server connection.
     *
     * @return long-poll server
     */
    protected LongPollServer getLongPollServer() throws ClientException, ApiException {
        return vk.groups().getLongPollServer(groupActor, configuration.getGroupId()).execute();
    }

    /**
     * Stars long-polling session if it has not been started yet.
     */
    @Override
    public void startLongPolling() {
        if (longPollingSession.compareAndSet(false, true)) {
            log.info("Starting long-polling");
            executor.execute(() -> {
                log.info("Started long-polling");
                try {
                    @NotNull String server, key;
                    int ts;
                    {
                        // initially get long-polling server
                        val longPollServer = getLongPollServer();
                        server = longPollServer.getServer();
                        key = longPollServer.getKey();
                        ts = longPollServer.getTs();
                    }

                    while (longPollingSession.get()) {
                        try {
                            val response = vk.longPoll().getEvents(server, key, ts)
                                    .waitTime(10)
                                    .execute();

                            ts = response.getTs();

                            log.debug("Received long-poll response: " + response.getUpdates());
                            for (val update : response.getUpdates()) longPollEventHandler.parse(update);
                        } catch (final LongPollServerKeyExpiredException e) {
                            log.debug("Long-poll key has expired, getting new", e);

                            // initially get long-polling server
                            val longPollServer = getLongPollServer();
                            server = longPollServer.getServer();
                            key = longPollServer.getKey();
                            ts = longPollServer.getTs();
                        }
                    }
                } catch (final ApiException | ClientException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * Uninterruptedly stops long-polling session.
     */
    @Override
    public void stopLongPolling() {
        longPollingSession.set(false);
    }

    @Override
    public void sendCatsUnavailable(final int peerId) {
        CollectionUtil.getRandom(configuration.getMessages().getCatsUnavailable()).ifPresent(
                message -> {
                    try {
                        vk.messages().send(groupActor)
                                .peerId(peerId)
                                .message(message)
                                .execute();
                    } catch (final ClientException | ApiException e) {
                        throw new RuntimeException(
                                "An exception occurred while trying to send cats unavailability message", e
                        );
                    }
                }
        );

    }

    /**
     * Sends cat images to the specified peer.
     *
     * @param peerId receiver of the message
     * @param images cat images to send
     * @throws ClientException if an exception occurs while performing the request
     * @throws ApiException if an exception occurs while using VK-API
     */
    @Override
    public void sendCatImages(final int peerId, @NonNull final CatImage... images)
            throws IOException, ClientException, ApiException {
        val photoUploadUrl = vk.photos().getMessagesUploadServer(groupActor)
                .peerId(peerId)
                .execute()
                .getUploadUrl();

        val script = new StringBuilder("var a;");
        for (var i = 0; i < images.length; i++) {
            val image = images[i];
            //val tempFile = File.createTempFile("catobot_temp_img", null);
            val tempFile = File.createTempFile("tmp_cat_img", '.' + image.getType());
            tempFile.deleteOnExit();
            Files.write(tempFile.toPath(), image.getImage());

            // upload the image
            val upload = vk.upload().photoMessage(photoUploadUrl.toString(), tempFile).execute();
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();

            // store image in variable c and create attachment from it
            script.append("{var c=API.photos.saveMessagesPhoto({\"photo\":\"")
                    .append(upload.getPhoto().replace("\"", "\\\""))
                    .append("\",\"server\":").append(upload.getServer())
                    .append(",\"hash\":\"").append(upload.getHash())
                    .append("\"})[0];a=a");
            if (i != 0) script.append('+').append('"').append(",").append('"');
            script.append("+\"photo\"+c.owner_id+\"_\"+c.id;}");
        }

        script.append("return API.messages.send({\"attachment\":a,\"random_id\":")
                .append(ThreadLocalRandom.current().nextInt() ^ Arrays.hashCode(images))
                .append(",\"peer_id\":").append(peerId);

        CollectionUtil.getRandom(configuration.getMessages().getCatsSent()).ifPresent(message -> {
            script.append(",\"message\":\"");
            JsonUtils.quoteAsString(message, script); // TODO use of other dependency
            script.append('"');
        });

        script.append("});");

        val code = script.toString();
        log.debug("Executing VKScript: {}`", code);
        vk.execute().code(groupActor, script.toString()).execute();
    }

    @Override
    public void close() {
        stopLongPolling();
        executor.shutdown();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Configuration {

        int groupId, /*userId, */albumId;
        @NonNull String groupToken/*, userToken*/;
        @Builder.Default @NonNull Messages messages = new Messages();

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Messages {
            @NonNull @Builder.Default List<String> catsSent = defaultMessages("Get your cats <3");
            @NonNull @Builder.Default List<String> catsUnavailable = defaultMessages("Cats will be back soon");

            protected static List<String> defaultMessages(@NonNull final String... messages) {
                return new ArrayList<>(Arrays.asList(messages));
            }
        }
    }
}
