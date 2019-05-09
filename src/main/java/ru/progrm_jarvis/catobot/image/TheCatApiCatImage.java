package ru.progrm_jarvis.catobot.image;

import com.google.gson.annotations.SerializedName;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link CatImage} based on TheCatApi.
 * This object's structure represents <a href="https://docs.thecatapi.com/api-reference/models/">the API model</a>
 * plus adds some logic related to {@link CatImage}-related functionality.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class TheCatApiCatImage implements CatImage {

    /**
     * Factory to create image bytes
     */
    @Nullable transient Supplier<@NotNull byte[]> imageFactory;

    String id, url;
    int width, height;
    @NonNull @Singular Collection<Breed> breeds;
    @NonNull @Singular Collection<Category> categories;

    /**
     * Image bytes of a cat initialized lazily using {@link #imageFactory}
     */
    @Nullable @Getter(lazy = true) private final transient byte[] image = Objects.requireNonNull(imageFactory, "imageFactory has not been initialized").get();

    /**
     * Type of the image computed from the url's ending extension
     */
    @Nullable @Getter(lazy = true) private final String type = url.substring(url.lastIndexOf('.') + 1);

    /**
     * Cat's <a href="https://docs.thecatapi.com/api-reference/models/category">category</a>.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    public static class Category {
        int id;
        @NonNull String name;
    }


    /**
     * Cat's <a href="https://docs.thecatapi.com/api-reference/models/breed">breed</a>.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    public static class Breed {
        @NonNull String id, name;
        @Nullable String temperament, origin;
        @SerializedName("life_spawn") @Nullable String lifeSpan;
        @SerializedName("alt_names") @Nullable String alternateNames;
        @SerializedName("wikipedia_url") @Nullable String wikipediaUrl;
        @SerializedName("weight_imperial") @Nullable String weightImperial;
        @SerializedName("country_code") @Nullable String countryCode;
        int
                experimental, hypoallergenic, hairless, natural, rare,
                rex, adaptability, grooming, intelligence, vocalisation;
        @SerializedName("suppress_tail") int suppressTail;
        @SerializedName("short_legs") int shortLegs;
        @SerializedName("affection_level") int affectionLevel;
        @SerializedName("child_friendly") int childFriendly;
        @SerializedName("dog_friendly") int dogFriendly;
        @SerializedName("energy_level") int energyLevel;
        @SerializedName("health_issues") int healthIssues;
        @SerializedName("shedding_level") int sheddingLevel;
        @SerializedName("social_needs") int socialNeeds;
        @SerializedName("stranger_friendly") int strangerFriendly;
    }
}
