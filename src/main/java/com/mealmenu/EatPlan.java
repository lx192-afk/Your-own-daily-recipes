package com.mealmenu;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Meal-plan entries for food eaten out (a dining hall dish or a restaurant item).
 * They share MealPlanStore with home recipes using ids of the form
 * {@code eat~<placeId>~<dish name>}.
 */
final class EatPlan {

    private static final String PREFIX = "eat~";

    private EatPlan() {
    }

    static String id(String placeId, String dish) {
        return PREFIX + placeId + "~" + dish;
    }

    static boolean isEat(String id) {
        return id != null && id.startsWith(PREFIX);
    }

    static Optional<Recipe> toRecipe(String id) {
        if (!isEat(id)) {
            return Optional.empty();
        }
        String[] parts = id.split("~", 3);
        if (parts.length < 3) {
            return Optional.empty();
        }
        String placeName = place(id).map(Place::name).orElse("Eating out");
        return Optional.of(new Recipe(id, parts[2] + " · " + placeName, 0,
                "At " + placeName, Set.of("Eat out"), List.of(), List.of()));
    }

    static Optional<Place> place(String id) {
        if (!isEat(id)) {
            return Optional.empty();
        }
        String placeId = id.split("~", 3)[1];
        Optional<Place> hall = DiningService.get().hall(placeId)
                .map(h -> new Place(h.name(), h.address(), h.lat(), h.lng()));
        if (hall.isPresent()) {
            return hall;
        }
        return RestaurantCatalog.byId(placeId)
                .map(r -> new Place(r.name(), r.address(), r.lat(), r.lng()));
    }

    record Place(String name, String address, Double lat, Double lng) {
        void navigate() {
            Links.directions(name, address, lat == null ? 0 : lat, lng == null ? 0 : lng);
        }
    }
}
