package com.mealmenu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rutgers dining hall menus from Rutgers Dining's Nutrislice API.
 * Menus are fetched live; a disk cache and a bundled snapshot
 * (scripts/scrape_dining.py) are used when the network is unavailable.
 */
public final class DiningService {

    private static final String API = "https://rutgers.api.nutrislice.com/menu/api";
    private static final String SNAPSHOT = "/com/mealmenu/data/dining-snapshot.json";
    private static final Set<String> SKIP_ICONS = Set.of(
            "Low Carbon Footprint", "Medium Carbon Footprint", "High Carbon Footprint");
    private static DiningService instance;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Duration FRESH_FOR = Duration.ofMinutes(30);
    private static final Duration RETRY_OFFLINE_AFTER = Duration.ofMinutes(2);

    private final Map<String, CachedWeek> weeks = new ConcurrentHashMap<>();
    /** When a fetch last failed, so offline browsing doesn't wait on a timeout for every click. */
    private final Map<String, java.time.Instant> failures = new ConcurrentHashMap<>();
    private final JsonObject snapshot;
    private final List<Hall> halls;

    private DiningService() {
        snapshot = loadSnapshot();
        halls = parseHalls(snapshot);
    }

    public static synchronized DiningService get() {
        if (instance == null) {
            instance = new DiningService();
        }
        return instance;
    }

    public List<Hall> halls() {
        return halls;
    }

    public Optional<Hall> hall(String slug) {
        return halls.stream().filter(hall -> hall.slug().equals(slug)).findFirst();
    }

    /** Menu for one hall, meal and day. Never fails: falls back to cached or bundled data. */
    public CompletableFuture<MenuResult> menu(String hallSlug, String mealSlug, LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        String key = hallSlug + "_" + mealSlug + "_" + weekStart;
        java.time.Instant now = java.time.Instant.now();
        CachedWeek cached = weeks.get(key);
        if (cached != null && cached.fetched().plus(FRESH_FOR).isAfter(now)) {
            return CompletableFuture.completedFuture(
                    new MenuResult(cached.days().getOrDefault(date.toString(), List.of()), "Live from Rutgers Dining"));
        }
        java.time.Instant failed = failures.get(key);
        if (failed != null && failed.plus(RETRY_OFFLINE_AFTER).isAfter(now)) {
            return CompletableFuture.completedFuture(offlineMenu(key, hallSlug, mealSlug, date));
        }
        String url = API + "/weeks/school/" + hallSlug + "/menu-type/" + mealSlug + "/"
                + weekStart.getYear() + "/" + pad(weekStart.getMonthValue()) + "/"
                + pad(weekStart.getDayOfMonth()) + "/";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "RutgersEats/1.0")
                .GET().build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    JsonObject days = compactWeek(JsonParser.parseString(response.body()).getAsJsonObject());
                    writeCache(key, days);
                    Map<String, List<Station>> parsed = parseDays(days);
                    weeks.put(key, new CachedWeek(parsed, java.time.Instant.now()));
                    failures.remove(key);
                    return new MenuResult(parsed.getOrDefault(date.toString(), List.of()),
                            "Live from Rutgers Dining");
                })
                .exceptionally(error -> {
                    failures.put(key, java.time.Instant.now());
                    return offlineMenu(key, hallSlug, mealSlug, date);
                });
    }

    private MenuResult offlineMenu(String key, String hallSlug, String mealSlug, LocalDate date) {
        Path cacheFile = cacheFile(key);
        if (Files.isRegularFile(cacheFile)) {
            try {
                JsonObject days = JsonParser.parseString(Files.readString(cacheFile)).getAsJsonObject();
                List<Station> stations = parseDays(days).getOrDefault(date.toString(), List.of());
                return new MenuResult(stations, "Offline · showing saved menu");
            } catch (IOException | RuntimeException ignored) {
                // Fall through to the bundled snapshot.
            }
        }
        if (snapshot != null && snapshot.has("weeks")) {
            for (JsonElement element : snapshot.getAsJsonArray("weeks")) {
                JsonObject week = element.getAsJsonObject();
                if (hallSlug.equals(str(week, "hall")) && mealSlug.equals(str(week, "meal"))
                        && week.getAsJsonObject("days").has(date.toString())) {
                    List<Station> stations = parseDays(week.getAsJsonObject("days"))
                            .getOrDefault(date.toString(), List.of());
                    return new MenuResult(stations, "Offline · menu bundled on "
                            + str(snapshot, "generatedAt").substring(0, 10));
                }
            }
        }
        return new MenuResult(List.of(), "Offline · no saved menu for this day");
    }

    /** Port of compact_week() in scripts/scrape_dining.py. */
    static JsonObject compactWeek(JsonObject raw) {
        JsonObject days = new JsonObject();
        for (JsonElement dayElement : raw.getAsJsonArray("days")) {
            JsonObject day = dayElement.getAsJsonObject();
            JsonArray stations = new JsonArray();
            JsonObject current = null;
            for (JsonElement entryElement : day.getAsJsonArray("menu_items")) {
                JsonObject entry = entryElement.getAsJsonObject();
                if (bool(entry, "is_section_title")) {
                    String name = Optional.ofNullable(str(entry, "text")).map(String::strip)
                            .filter(text -> !text.isEmpty()).orElse("Other");
                    current = findStation(stations, name);
                    if (current == null) {
                        current = newStation(stations, name);
                    }
                    continue;
                }
                JsonObject food = obj(entry, "food");
                if (food == null || str(food, "name") == null) {
                    continue;
                }
                if (current == null) {
                    current = newStation(stations, "Menu");
                }
                JsonObject nutrition = obj(food, "rounded_nutrition_info");
                JsonObject serving = obj(food, "serving_size_info");
                JsonObject item = new JsonObject();
                item.addProperty("name", str(food, "name").strip());
                if (nutrition != null) {
                    copyNumber(nutrition, "calories", item, "cal");
                    copyNumber(nutrition, "g_protein", item, "protein");
                    copyNumber(nutrition, "g_carbs", item, "carbs");
                    copyNumber(nutrition, "g_fat", item, "fat");
                }
                if (serving != null && str(serving, "serving_size_amount") != null) {
                    String unit = str(serving, "serving_size_unit");
                    item.addProperty("serving", str(serving, "serving_size_amount")
                            + (unit == null ? "" : " " + unit));
                }
                JsonArray icons = new JsonArray();
                JsonObject iconHolder = obj(food, "icons");
                if (iconHolder != null && iconHolder.has("food_icons")) {
                    for (JsonElement icon : iconHolder.getAsJsonArray("food_icons")) {
                        String iconName = str(icon.getAsJsonObject(), "name");
                        if (iconName != null && !SKIP_ICONS.contains(iconName)) {
                            icons.add(iconName);
                        }
                    }
                }
                item.add("icons", icons);
                JsonArray items = current.getAsJsonArray("items");
                boolean duplicate = false;
                for (JsonElement existing : items) {
                    duplicate |= str(existing.getAsJsonObject(), "name").equals(str(item, "name"));
                }
                if (!duplicate) {
                    items.add(item);
                }
            }
            JsonArray nonEmpty = new JsonArray();
            for (JsonElement station : stations) {
                if (!station.getAsJsonObject().getAsJsonArray("items").isEmpty()) {
                    nonEmpty.add(station);
                }
            }
            if (!nonEmpty.isEmpty()) {
                days.add(str(day, "date"), nonEmpty);
            }
        }
        return days;
    }

    private static Map<String, List<Station>> parseDays(JsonObject days) {
        Map<String, List<Station>> result = new ConcurrentHashMap<>();
        for (String date : days.keySet()) {
            List<Station> stations = new ArrayList<>();
            for (JsonElement stationElement : days.getAsJsonArray(date)) {
                JsonObject station = stationElement.getAsJsonObject();
                List<Item> items = new ArrayList<>();
                for (JsonElement itemElement : station.getAsJsonArray("items")) {
                    JsonObject item = itemElement.getAsJsonObject();
                    List<String> icons = new ArrayList<>();
                    if (item.has("icons")) {
                        item.getAsJsonArray("icons").forEach(icon -> icons.add(icon.getAsString()));
                    }
                    items.add(new Item(titleCase(str(item, "name")), num(item, "cal"),
                            num(item, "protein"), num(item, "carbs"), num(item, "fat"),
                            str(item, "serving"), List.copyOf(icons)));
                }
                stations.add(new Station(titleCase(str(station, "station")), List.copyOf(items)));
            }
            result.put(date, List.copyOf(stations));
        }
        return result;
    }

    private static final List<String> MAIN_STATIONS = List.of("entree", "grill", "rotisserie", "pub",
            "asian", "wok", "mongolian", "halal", "kosher", "pizza", "pasta", "carving");

    /** Hot entrées first; topping bars (mostly tiny-calorie add-ons) and bagels last. */
    static List<Station> mainStationsFirst(List<Station> input) {
        return input.stream().sorted(Comparator.comparingInt(DiningService::stationRank)).toList();
    }

    private static int stationRank(Station station) {
        String name = station.name().toLowerCase(Locale.ROOT);
        double[] calories = station.items().stream().filter(item -> item.calories() != null)
                .mapToDouble(Item::calories).sorted().toArray();
        double median = calories.length == 0 ? 100 : calories[calories.length / 2];
        if (median < 40) {
            return 3;
        }
        if (name.contains("deli") || name.contains("salad")) {
            return 1;
        }
        for (int i = 0; i < MAIN_STATIONS.size(); i++) {
            if (name.contains(MAIN_STATIONS.get(i))) {
                return 0;
            }
        }
        return name.contains("bagel") || name.contains("bread") || name.contains("fruit") ? 2 : 1;
    }

    /** A few headline dishes for a meal: from the main hot stations, skipping sides and toppings. */
    static List<String> highlights(List<Station> stations, int limit) {
        List<String> names = new ArrayList<>();
        for (Station station : mainStationsFirst(stations)) {
            if (stationRank(station) > 0) {
                break;
            }
            for (Item item : station.items()) {
                if (item.calories() != null && item.calories() >= 150 && !names.contains(item.name())) {
                    names.add(item.name());
                }
                if (names.size() >= limit) {
                    return names;
                }
            }
        }
        return names;
    }

    /** The meal being served around now, by typical Rutgers dining hours. */
    static String currentMealSlug(Hall hall, java.time.ZonedDateTime now) {
        boolean weekend = now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY;
        LocalTime time = now.toLocalTime();
        String preferred;
        if (time.isBefore(LocalTime.of(10, 30)) && !weekend) {
            preferred = "breakfast";
        } else if (time.isBefore(LocalTime.of(16, 0))) {
            preferred = "lunch";
        } else if (time.isBefore(LocalTime.of(21, 0))) {
            preferred = "dinner";
        } else {
            preferred = "knight";
        }
        return hall.meals().stream().map(Meal::slug).filter(slug -> slug.contains(preferred)).findFirst()
                .orElse(hall.meals().isEmpty() ? null : hall.meals().get(0).slug());
    }

    private static List<Hall> parseHalls(JsonObject snapshot) {
        List<Hall> result = new ArrayList<>();
        if (snapshot == null || !snapshot.has("halls")) {
            return result;
        }
        String[] days = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
        for (JsonElement element : snapshot.getAsJsonArray("halls")) {
            JsonObject hall = element.getAsJsonObject();
            Map<DayOfWeek, String> hours = new EnumMap<>(DayOfWeek.class);
            Map<DayOfWeek, int[]> ranges = new EnumMap<>(DayOfWeek.class);
            JsonObject hoursObject = obj(hall, "hours");
            for (int i = 0; i < days.length; i++) {
                String range = hoursObject == null ? null : str(hoursObject, days[i]);
                hours.put(DayOfWeek.of(i + 1), range == null ? "Closed" : formatRange(range));
                ranges.put(DayOfWeek.of(i + 1), range == null ? null : minuteRange(range));
            }
            List<Meal> meals = new ArrayList<>();
            for (JsonElement meal : hall.getAsJsonArray("meals")) {
                meals.add(new Meal(str(meal.getAsJsonObject(), "slug"),
                        shortMealName(str(meal.getAsJsonObject(), "name"))));
            }
            String slug = str(hall, "slug");
            result.add(new Hall(slug, str(hall, "name"), campusFor(slug), str(hall, "address"),
                    str(hall, "phone"), num(hall, "lat"), num(hall, "lng"), hours,
                    OpenHours.ofDaily(ranges), List.copyOf(meals)));
        }
        return List.copyOf(result);
    }

    private static String campusFor(String slug) {
        if (slug.startsWith("busch")) {
            return "Busch";
        }
        if (slug.startsWith("livingston")) {
            return "Livingston";
        }
        if (slug.startsWith("neilson")) {
            return "Cook/Douglass";
        }
        return "College Ave";
    }

    static String shortMealName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("breakfast")) {
            return "Breakfast";
        }
        if (lower.startsWith("lunch")) {
            return "Lunch/Brunch";
        }
        if (lower.startsWith("dinner")) {
            return "Dinner";
        }
        if (lower.contains("knight")) {
            return "Late Knight";
        }
        return name;
    }

    private static int[] minuteRange(String range) {
        try {
            LocalTime start = LocalTime.parse(range.split("-")[0]);
            LocalTime end = LocalTime.parse(range.split("-")[1]);
            int s = start.getHour() * 60 + start.getMinute();
            int e = end.getHour() * 60 + end.getMinute();
            return new int[]{s, e <= s ? e + 1440 : e};
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String formatRange(String range) {
        String[] parts = range.split("-");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        try {
            return LocalTime.parse(parts[0]).format(format) + " – " + LocalTime.parse(parts[1]).format(format);
        } catch (RuntimeException error) {
            return range;
        }
    }

    /** "WISCONSIN CHEESE & BEER SOUP" -> "Wisconsin Cheese & Beer Soup". */
    static String titleCase(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean startOfWord = true;
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = !Character.isLetterOrDigit(c) && c != '\'';
        }
        String result = out.toString().strip().replaceAll("\\s+", " ");
        for (String small : List.of("And", "Or", "Of", "With", "In", "On")) {
            result = result.replace(" " + small + " ", " " + small.toLowerCase(Locale.ROOT) + " ");
        }
        return result;
    }

    private static JsonObject loadSnapshot() {
        try (InputStream in = DiningService.class.getResourceAsStream(SNAPSHOT)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    private static Path cacheFile(String key) {
        return UserDataPaths.applicationDirectory().resolve("cache").resolve("dining").resolve(key + ".json");
    }

    private static void writeCache(String key, JsonObject days) {
        try {
            Path file = cacheFile(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file, days.toString());
        } catch (IOException ignored) {
            // The cache is a convenience; live data is already in memory.
        }
    }

    private static JsonObject findStation(JsonArray stations, String name) {
        for (JsonElement station : stations) {
            if (name.equals(str(station.getAsJsonObject(), "station"))) {
                return station.getAsJsonObject();
            }
        }
        return null;
    }

    private static JsonObject newStation(JsonArray stations, String name) {
        JsonObject station = new JsonObject();
        station.addProperty("station", name);
        station.add("items", new JsonArray());
        stations.add(station);
        return station;
    }

    private static void copyNumber(JsonObject from, String fromKey, JsonObject to, String toKey) {
        Double value = num(from, fromKey);
        if (value != null) {
            to.addProperty(toKey, value);
        }
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    static String str(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    static Double num(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? null : value.getAsDouble();
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    private static JsonObject obj(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || !value.isJsonObject() ? null : value.getAsJsonObject();
    }

    public record Hall(String slug, String name, String campus, String address, String phone,
                       Double lat, Double lng, Map<DayOfWeek, String> hours, OpenHours openHours,
                       List<Meal> meals) {
        public String shortName() {
            return name.replace(" Dining Hall", "").replace(" Dining Commons", "").replace("The ", "");
        }

        public String hoursOn(LocalDate date) {
            return hours.getOrDefault(date.getDayOfWeek(), "Hours unavailable");
        }
    }

    private record CachedWeek(Map<String, List<Station>> days, java.time.Instant fetched) {
    }

    public record Meal(String slug, String name) {
    }

    public record Item(String name, Double calories, Double protein, Double carbs, Double fat,
                       String serving, List<String> icons) {
    }

    public record Station(String name, List<Item> items) {
    }

    public record MenuResult(List<Station> stations, String source) {
    }
}
