package com.mealmenu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.mealmenu.DiningService.num;
import static com.mealmenu.DiningService.str;

/**
 * Restaurants near Rutgers–New Brunswick with real menus collected from each
 * restaurant's own site or ordering page (see data/restaurants and
 * scripts/build_restaurants.py). Every entry records where its menu came from.
 */
public final class RestaurantCatalog {

    private static final String RESOURCE = "/com/mealmenu/data/restaurants.json";
    private static List<Restaurant> restaurants;

    private RestaurantCatalog() {
    }

    public static synchronized List<Restaurant> all() {
        if (restaurants == null) {
            restaurants = load();
        }
        return restaurants;
    }

    public static Optional<Restaurant> byId(String id) {
        return all().stream().filter(restaurant -> restaurant.id().equals(id)).findFirst();
    }

    private static List<Restaurant> load() {
        List<Restaurant> result = new ArrayList<>();
        try (InputStream in = RestaurantCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return List.of();
            }
            for (JsonElement element : JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                List<MenuSection> menu = new ArrayList<>();
                if (o.has("menu") && o.get("menu").isJsonArray()) {
                    for (JsonElement sectionElement : o.getAsJsonArray("menu")) {
                        JsonObject section = sectionElement.getAsJsonObject();
                        List<MenuItem> items = new ArrayList<>();
                        for (JsonElement itemElement : section.getAsJsonArray("items")) {
                            JsonObject item = itemElement.getAsJsonObject();
                            items.add(new MenuItem(str(item, "name"), num(item, "price"),
                                    blankToNull(str(item, "description"))));
                        }
                        menu.add(new MenuSection(str(section, "section"), List.copyOf(items)));
                    }
                }
                List<String> tags = new ArrayList<>();
                if (o.has("tags") && o.get("tags").isJsonArray()) {
                    o.getAsJsonArray("tags").forEach(tag -> tags.add(tag.getAsString()));
                }
                result.add(new Restaurant(str(o, "id"), str(o, "name"), str(o, "cuisine"),
                        orElse(str(o, "cuisineGroup"), str(o, "cuisine")), str(o, "area"),
                        orElse(str(o, "areaGroup"), str(o, "area")), str(o, "address"), num(o, "lat"), num(o, "lng"),
                        str(o, "phone"), str(o, "website"), str(o, "orderUrl"),
                        str(o, "priceLevel"), str(o, "hours"), OpenHours.fromJson(o.get("openHours")),
                        List.copyOf(tags),
                        str(o, "menuSource"), str(o, "verifiedOn"), List.copyOf(menu)));
            }
        } catch (IOException | RuntimeException error) {
            return List.copyOf(result);
        }
        return List.copyOf(result);
    }

    private static String orElse(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    public record Restaurant(String id, String name, String cuisine, String cuisineGroup,
                             String area, String areaGroup, String address,
                             Double lat, Double lng, String phone, String website, String orderUrl,
                             String priceLevel, String hours, OpenHours openHours, List<String> tags,
                             String menuSource, String verifiedOn, List<MenuSection> menu) {

        public OpenHours.Status statusNow() {
            return openHours.status(java.time.ZonedDateTime.now(Campus.ZONE));
        }

        public boolean hasLocation() {
            return lat != null && lng != null;
        }

        /** Matches the restaurant name, cuisine, area, tags or any dish on its menu. */
        public boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String q = query.toLowerCase(Locale.ROOT).strip();
            if (contains(name, q) || contains(cuisine, q) || contains(cuisineGroup, q) || contains(area, q)
                    || tags.stream().anyMatch(tag -> contains(tag, q))) {
                return true;
            }
            return menu.stream().flatMap(section -> section.items().stream())
                    .anyMatch(item -> contains(item.name(), q) || contains(item.description(), q));
        }

        public List<String> matchingDishes(String query, int limit) {
            if (query == null || query.isBlank()) {
                return List.of();
            }
            String q = query.toLowerCase(Locale.ROOT).strip();
            return menu.stream().flatMap(section -> section.items().stream())
                    .filter(item -> contains(item.name(), q))
                    .map(MenuItem::name).distinct().limit(limit).toList();
        }

        private static boolean contains(String text, String query) {
            return text != null && text.toLowerCase(Locale.ROOT).contains(query);
        }
    }

    public record MenuSection(String section, List<MenuItem> items) {
    }

    public record MenuItem(String name, Double price, String description) {
        public String priceText() {
            return price == null ? "" : String.format(Locale.US, "$%.2f", price);
        }
    }
}
