package ru.progrm_jarvis.catobot.vk;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import ru.progrm_jarvis.catobot.image.CatImage;

import java.io.IOException;

/**
 * Manager responsible for all business-logic of cats-sending via VK.
 */
public interface VkCatsManager extends AutoCloseable {

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
     * @throws ClientException if an exception occurs while performing the request
     * @throws ApiException if an exception occurs while using VK-API
     */
    void sendCatImages(int peerId, @Nullable Integer repliedMessageId,
                       @NonNull CatImage... images) throws IOException, ClientException, ApiException;
}
