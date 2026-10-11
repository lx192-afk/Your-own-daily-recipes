package com.mealmenu;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

public record DailyPlan(
        String id,
        LocalDate date,
        String mealPeriod,
        String recipeId,
        LocalTime scheduledTime
) {
    public DailyPlan {
        Objects.requireNonNull(id);
        Objects.requireNonNull(date);
        Objects.requireNonNull(mealPeriod);
        Objects.requireNonNull(recipeId);
        Objects.requireNonNull(scheduledTime);
    }

    // Older callers and saved plans use a default time for their meal period.
    public DailyPlan(String id, LocalDate date, String mealPeriod, String recipeId) {
        this(id, date, mealPeriod, recipeId, defaultTime(mealPeriod));
    }

    public static LocalTime defaultTime(String mealPeriod) {
        return switch (mealPeriod) {
            case "Breakfast" -> LocalTime.of(8, 0);
            case "Lunch" -> LocalTime.of(12, 0);
            case "Dinner" -> LocalTime.of(18, 0);
            case "Late-night snack" -> LocalTime.of(22, 0);
            default -> LocalTime.of(12, 0);
        };
    }

    public LocalDateTime scheduledAt() {
        return date.atTime(scheduledTime);
    }

    public String timeText() {
        return scheduledTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH));
    }

    public boolean isToday(LocalDateTime now) {
        return date.equals(now.toLocalDate());
    }

    public boolean canComplete(LocalDateTime now) {
        return isToday(now) && !now.isBefore(scheduledAt());
    }

    public String timingText(LocalDateTime now) {
        if (now.isBefore(scheduledAt())) {
            Duration remaining = Duration.between(now, scheduledAt());
            long seconds = remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
            long days = seconds / 86_400;
            String clock = String.format(Locale.ENGLISH, "%02d:%02d:%02d",
                    seconds / 3_600 % 24, seconds / 60 % 60, seconds % 60);
            return "Starts in " + (days > 0 ? days + "d " : "") + clock;
        }
        return isToday(now) ? "Ready to complete" : "Past plan";
    }
}

