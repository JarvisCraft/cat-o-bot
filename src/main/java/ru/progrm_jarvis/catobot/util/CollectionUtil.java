package ru.progrm_jarvis.catobot.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for providing easier use of Java Collections API in specific cases.
 */
@UtilityClass
public class CollectionUtil {

    /**
     * Picks a random element from the specified list.s
     *
     * @param list list from which to take a random element
     * @param <T> type of element needed
     * @return random element taken from the specified list wrapped in {@link Optional}
     * or an empty {@link Optional} if the list is empty
     */
    public <T> Optional<T> getRandom(@NonNull final List<T> list) {
        val size = list.size();

        return size == 0 ? Optional.empty() : Optional.ofNullable(list.get(ThreadLocalRandom.current().nextInt(size)));
    }
}
