package ru.progrm_jarvis.catobot.vk;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.AudioMessage;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import ru.progrm_jarvis.catobot.image.CatImage;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Manager responsible for all business-logic of cats-sending via VK.
 */
public interface VkCatsManager extends AutoCloseable {

    /**
     * Gets client to use for VK API usage.
     *
     * @return VK API client
     */
    VkApiClient getClient();

    /**
     * Gets group-actor used by VK API client.
     *
     * @return group-actor used by VK API client
     */
    GroupActor getGroupActor();

    /**
     * Gets a random ID for a message to be sent to the peer.
     *
     * @param peerId ID of a peer to whom the message by the ID is expected to be sent
     * @return random ID which might be used for sending a message to the specified peer
     *
     * @apiNote this does not guarantee collision-safety although chances of this happening are minimal
     */
    int getRandomMessageId(final int peerId);

    /**
     * Converts the specified audio-message object into an input-stream of an MP3 audio.
     *
     * @param audioMessage audio message whose MP3-content should be returned as an input stream
     * @return input stream of an audio-message MP3-content
     */
    Optional<InputStream> toMp3InputStream(@NonNull final AudioMessage audioMessage);

    /**
     * Stars long-polling session if it has not been started yet.
     */
    void startLongPolling();

    /**
     * Uninterruptedly stops long-polling session.
     */
    void stopLongPolling();

    /**
     * Send a message informing that cats are currently unavailable to the peer.
     *
     * @param peerId receiver of the message
     * @param repliedMessageId ID of a message which this one replies to
     */
    void sendCatsUnavailable(int peerId, @Nullable Integer repliedMessageId);

    /**
     * Sends cat images to the specified peer.
     *
     * @param peerId receiver of the message
     * @param repliedMessageId ID of a message which this one replies to
     * @param images cat images to send
     */
    void sendCatImages(int peerId, @Nullable Integer repliedMessageId,
                       @NonNull List<CompletableFuture<CatImage>> images);
}
