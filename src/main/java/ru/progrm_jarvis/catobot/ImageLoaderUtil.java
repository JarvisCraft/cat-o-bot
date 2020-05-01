package ru.progrm_jarvis.catobot;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import lombok.val;
import lombok.var;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.commons.io.IOUtils.toBufferedInputStream;
import static org.apache.commons.io.IOUtils.toBufferedReader;

public class ImageLoaderUtil {

    public static void main(final String... args) throws InterruptedException {
        val dir = new File("C:\\Users\\PROgrammer_JARvis\\Desktop\\cats");
        val amount = 1;

        val client = HttpClients.createDefault();
        val parser = new JsonParser();

        val executor = new ForkJoinPool();

        val occupied = new AtomicBoolean();

        val ids = new AtomicInteger();
        val countdown = new CountDownLatch(amount);
        for (var i = 0; i < amount; i++) {
            executor.submit(() -> {
                try {
                    final JsonArray response;

                    {
                        val request = new HttpGet("https://api.thecatapi.com/v1/images/search?limit=10");
                        request.setHeader("x-api-key", "2b1c3a7c-3e36-4bea-8716-69b79959ab3f");
                        request.setHeader("User-Agent", "Cat'o'Bot @ ImageLoaderUtil");
                        try (val reader = toBufferedReader(
                                new InputStreamReader(client.execute(request).getEntity().getContent())
                        )) {
                            response = parser.parse(reader).getAsJsonArray();
                        }
                    }

                    occupied.lazySet(false);
                    System.out.println("Got: " + response);
                    for (val jsonElement : response) {
                        val request = new HttpGet(
                                jsonElement.getAsJsonObject().getAsJsonPrimitive("url").getAsString()
                        );
                        request.setHeader("x-api-key", "2b1c3a7c-3e36-4bea-8716-69b79959ab3f");
                        request.setHeader("User-Agent", "Cat'o'Bot @ ImageLoaderUtil");

                        final BufferedImage image;
                        try (val imageStream
                                     = toBufferedInputStream(client.execute(request).getEntity().getContent())) {
                            image = ImageIO.read(imageStream);
                        }
                        ImageIO.write(image, "png", new File(dir, "cat-" + ids.incrementAndGet() + ".png"));
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                } finally {
                    countdown.countDown();
                }
            });
        }

        countdown.await();
    }
}
