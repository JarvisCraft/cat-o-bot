package ru.progrm_jarvis.catobot.vk;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.NonNull;
import ru.progrm_jarvis.catobot.image.CatImage;

public interface VkCatsManager extends AutoCloseable {
    void startLongPolling();

    void stopLongPolling();

    void sendCatsUnavailable(int peerId);

    void sendCatImages(int peerId, @NonNull CatImage... images) throws ClientException, ApiException;
}
