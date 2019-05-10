package ru.progrm_jarvis.catobot.image.repository;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import lombok.var;
import ru.progrm_jarvis.catobot.image.CatImage;
import ru.progrm_jarvis.catobot.image.factory.CatImageFactory;

import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class PreLoadingCatImageRepository<I extends CatImage, C> implements CatImageRepository<I, C> {

    @NonNull CatImageFactory<I, C> factory;

    int cacheSize;

    long interval;

    @NonNull Deque<SoftReference<Future<Optional<I>>>> cache;

    @NonNull ExecutorService worker;

    C defaultConfiguration;

    public PreLoadingCatImageRepository(@NonNull final CatImageFactory<I, C> factory, final C defaultConfiguration,
                                        final int cacheSize, final long interval) {
        this.factory = factory;
        this.defaultConfiguration = defaultConfiguration;
        this.cacheSize = cacheSize;
        this.interval = interval;

        cache = new ConcurrentLinkedDeque<>();

        worker = Executors.newSingleThreadExecutor();
        worker.execute(this::startLoadingCats);
    }

    protected void startLoadingCats() {
        while (true) {
            val sizeDelta = cacheSize - cache.size();
            for (var i = 0; i < sizeDelta; i++) cache
                    .add(new SoftReference<>(factory.createCatImage(defaultConfiguration)));

            try {
                Thread.sleep(interval);
            } catch (final InterruptedException e) {
                log.debug("Aborted cats loading", e);
                return;
            }
        }
    }

    @Override
    public Future<Optional<I>> pickRandomCatImage(final C configuration) {
        val imageReference = cache.poll();
        if (imageReference == null) return factory.createCatImage(configuration);

        val image = imageReference.get();
        if (image == null) return factory.createCatImage(configuration);

        return image;
    }

    @Override
    public void close() {
        worker.shutdown();
    }
}
