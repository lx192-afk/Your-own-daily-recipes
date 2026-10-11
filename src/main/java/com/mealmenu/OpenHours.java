package com.mealmenu;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Weekly opening hours as minute ranges per day. A range may end after midnight
 * (end > 1440). A day mapped to {@code null} is unknown; an empty list means closed.
 */
public final class OpenHours {

    private static final String[] KEYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    private static final OpenHours UNKNOWN = new OpenHours(new EnumMap<>(DayOfWeek.class));

    private final Map<DayOfWeek, List<int[]>> days;

    private OpenHours(Map<DayOfWeek, List<int[]>> days) {
        this.days = days;
    }

    public static OpenHours unknown() {
        return UNKNOWN;
    }

    /** From {"mon": [[660, 1260]], "tue": [], "wed": null, ...} as written by scripts/hours.py. */
    static OpenHours fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return UNKNOWN;
        }
        JsonObject object = element.getAsJsonObject();
        Map<DayOfWeek, List<int[]>> days = new EnumMap<>(DayOfWeek.class);
        for (int i = 0; i < KEYS.length; i++) {
            JsonElement value = object.get(KEYS[i]);
            if (value == null || !value.isJsonArray()) {
                continue;
            }
            List<int[]> ranges = new ArrayList<>();
            for (JsonElement range : value.getAsJsonArray()) {
                ranges.add(new int[]{range.getAsJsonArray().get(0).getAsInt(),
                        range.getAsJsonArray().get(1).getAsInt()});
            }
            days.put(DayOfWeek.of(i + 1), ranges);
        }
        return new OpenHours(days);
    }

    /** One range per day, e.g. dining hall building hours; {@code null} entries mean closed. */
    static OpenHours ofDaily(Map<DayOfWeek, int[]> ranges) {
        Map<DayOfWeek, List<int[]>> days = new EnumMap<>(DayOfWeek.class);
        ranges.forEach((day, range) -> days.put(day, range == null ? List.of() : List.of(range)));
        return new OpenHours(days);
    }

    public boolean known() {
        return !days.isEmpty();
    }

    public Status status(ZonedDateTime now) {
        if (!known()) {
            return new Status(State.UNKNOWN, "Hours unknown");
        }
        DayOfWeek today = now.getDayOfWeek();
        int minute = now.getHour() * 60 + now.getMinute();
        // Still open from a range that started yesterday and runs past midnight?
        List<int[]> yesterday = days.get(today.minus(1));
        if (yesterday != null) {
            for (int[] range : yesterday) {
                if (range[1] > 1440 && minute + 1440 < range[1]) {
                    return new Status(State.OPEN, "Open · until " + time(range[1]));
                }
            }
        }
        List<int[]> ranges = days.get(today);
        if (ranges == null) {
            return new Status(State.UNKNOWN, "Hours unknown today");
        }
        for (int[] range : ranges) {
            if (minute >= range[0] && minute < range[1]) {
                return new Status(State.OPEN, "Open · until " + time(range[1]));
            }
        }
        for (int[] range : ranges) {
            if (range[0] > minute) {
                return new Status(State.CLOSED, "Closed · opens " + time(range[0]));
            }
        }
        for (int offset = 1; offset <= 7; offset++) {
            DayOfWeek day = today.plus(offset);
            List<int[]> next = days.get(day);
            if (next == null) {
                return new Status(State.CLOSED, "Closed now");
            }
            if (!next.isEmpty()) {
                String when = offset == 1 ? "tomorrow" : day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                return new Status(State.CLOSED, "Closed · opens " + when + " " + time(next.get(0)[0]));
            }
        }
        return new Status(State.CLOSED, "Closed");
    }

    private static String time(int minutes) {
        int m = minutes % 1440;
        return LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofPattern(
                m % 60 == 0 ? "h a" : "h:mm a", Locale.ENGLISH));
    }

    public enum State { OPEN, CLOSED, UNKNOWN }

    public record Status(State state, String text) {
        public boolean open() {
            return state == State.OPEN;
        }
    }
}
