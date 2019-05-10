package ru.progrm_jarvis.catobot.vk;

import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.NonNull;
import lombok.SneakyThrows;
import ru.progrm_jarvis.catobot.image.CatImage;

import java.io.IOException;

public interface VkCatsManager extends AutoCloseable {
    void startLongPolling();

    void stopLongPolling();

    void sendCatsUnavailable(int peerId);

    @SneakyThrows(IOException.class)
    void sendCatImages(int peerId, @NonNull CatImage... images)
            throws ClientException, ApiException;
}
