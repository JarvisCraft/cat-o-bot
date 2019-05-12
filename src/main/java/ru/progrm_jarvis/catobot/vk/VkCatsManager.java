package ru.progrm_jarvis.catobot.vk;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;
import ru.progrm_jarvis.catobot.image.CatImage;

import java.io.IOException;

public interface VkCatsManager extends AutoCloseable {
    void startLongPolling();

    void stopLongPolling();

    void sendCatsUnavailable(int peerId, @Nullable Integer repliedMessageId);

    void sendCatImages(int peerId, @Nullable Integer repliedMessageId,
                       @NonNull CatImage... images) throws IOException, ClientException, ApiException;
}
