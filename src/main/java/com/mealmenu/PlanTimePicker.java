package com.mealmenu;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.Locale;

/** A compact, minute-precision time picker using a 24-hour clock. */
final class PlanTimePicker extends HBox {

    private final ComboBox<Integer> hour = new ComboBox<>();
    private final ComboBox<Integer> minute = new ComboBox<>();

    PlanTimePicker(LocalTime initialTime) {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        for (int value = 0; value < 24; value++) {
            hour.getItems().add(value);
        }
        for (int value = 0; value < 60; value++) {
            minute.getItems().add(value);
        }
        StringConverter<Integer> paddedNumber = new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : String.format(Locale.ENGLISH, "%02d", value);
            }

            @Override
            public Integer fromString(String value) {
                return Integer.valueOf(value);
            }
        };
        hour.setConverter(paddedNumber);
        minute.setConverter(paddedNumber);
        hour.setId("plan-hour");
        minute.setId("plan-minute");
        hour.setAccessibleText("Scheduled hour, 24-hour clock");
        minute.setAccessibleText("Scheduled minute");
        hour.setMaxWidth(Double.MAX_VALUE);
        minute.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(hour, Priority.ALWAYS);
        HBox.setHgrow(minute, Priority.ALWAYS);
        getChildren().addAll(hour, new Label(":"), minute);
        setValue(initialTime);
    }

    LocalTime getValue() {
        return LocalTime.of(hour.getValue(), minute.getValue());
    }

    void setValue(LocalTime time) {
        hour.setValue(time.getHour());
        minute.setValue(time.getMinute());
    }
}
