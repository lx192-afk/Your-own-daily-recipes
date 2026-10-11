package com.mealmenu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class KitchenPage extends BorderPane {

    private final Stage owner;
    private final LocalAccountService accountService;
    private final PlanSaver planSaver;
    private final Map<String, ToggleButton> tagButtons = new LinkedHashMap<>();
    private final Map<String, ToggleGroup> tagGroups = new LinkedHashMap<>();
    private Set<String> appliedPreferences;
    private final Label status = new Label();
    private LocalDate targetDate = LocalDate.now();
    private String preferredMealPeriod = AppDialogs.defaultMealPeriod();

    public KitchenPage(Stage owner, LocalAccountService accountService, PlanSaver planSaver) {
        this.owner = owner;
        this.accountService = accountService;
        this.planSaver = planSaver;
        getStyleClass().add("kitchen-page");

        VBox content = new VBox(14);
        content.getStyleClass().add("kitchen-content");
        content.setPadding(new Insets(17, 17, 18, 17));
        Label title = new Label("Kitchen");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Choose up to one tag per group. Every choice is optional.");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        content.getChildren().addAll(
                title,
                subtitle,
                createTagSection("Common Flavors", "Sour", "Sweet", "Bitter",
                        "Spicy", "Light", "Hearty"),
                createTagSection("Meal Time", "Breakfast", "Lunch", "Dinner",
                        "Late-night snack"),
                createTagSection("Temperature", "Hot", "Cold"),
                createTagSection("Mood", "Comforting", "Fresh", "Cozy",
                        "Celebratory", "Quick"),
                createTagSection("Health Preferences", "Vegetarian", "Gluten-Free",
                        "Dairy-Free", "Low-Carb", "High-Protein")
        );
        status.getStyleClass().add("status-text");
        status.setWrapText(true);
        content.getChildren().add(status);

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("page-scroll");
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setCenter(scroll);

        Button reset = new Button("Reset");
        reset.getStyleClass().add("text-button");
        reset.setOnAction(event -> resetChoices());
        Button randomize = new Button("Randomize");
        randomize.getStyleClass().add("secondary-button");
        randomize.setOnAction(event -> randomizeChoices());
        Button plan = new Button("Plan My Meal");
        plan.getStyleClass().add("primary-button");
        plan.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(plan, Priority.ALWAYS);
        plan.setOnAction(event -> showRecommendation());
        HBox actionBar = new HBox(7, reset, randomize, plan);
        actionBar.getStyleClass().add("kitchen-action-bar");
        actionBar.setAlignment(Pos.CENTER);
        actionBar.setPadding(new Insets(9, 12, 11, 12));
        setBottom(actionBar);

        refreshPreferences();
    }

    public void setTargetDate(LocalDate date) {
        targetDate = date == null ? LocalDate.now() : date;
        preferredMealPeriod = AppDialogs.defaultMealPeriod();
    }

    public void refreshPreferences() {
        Set<String> saved = accountService.preferences();
        if (saved.equals(appliedPreferences)) {
            return;
        }
        appliedPreferences = Set.copyOf(saved);
        tagGroups.get("Health Preferences").selectToggle(null);
        for (String tag : List.of("Vegetarian", "Gluten-Free", "Dairy-Free",
                "Low-Carb", "High-Protein")) {
            ToggleButton button = tagButtons.get(tag);
            if (button != null && saved.contains(tag)) {
                button.setSelected(true);
                break;
            }
        }
    }

    public void prepareQuickStart(String tag, LocalDate date, String mealPeriod) {
        resetChoices();
        ToggleButton button = tagButtons.get(tag);
        if (button != null) {
            button.setSelected(true);
        }
        targetDate = date == null ? LocalDate.now() : date;
        preferredMealPeriod = mealPeriod;
        status.setText("Quick Start: " + tag + " ideas for "
                + targetDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                java.util.Locale.ENGLISH) + " " + targetDate.getDayOfMonth() + ".");
    }

    public void showRecommendation() {
        Set<String> selectedTags = selectedTags();
        String mealPeriod = selectedMealPeriod();
        AppDialogs.showRecommendation(owner, selectedTags, targetDate, mealPeriod)
                .ifPresent(selection -> {
                    boolean saved = planSaver.save(
                            selection.date(), selection.mealPeriod(), selection.recipe(), selection.time()
                    );
                    status.setText(saved
                            ? selection.recipe().name() + " added to your menu."
                            : "The meal could not be saved to this device. Please try again.");
                });
    }

    private VBox createTagSection(String title, String... tags) {
        Label heading = new Label(title);
        heading.getStyleClass().add("gradient-section-title");
        heading.setMaxWidth(Double.MAX_VALUE);
        FlowPane choices = new FlowPane(8, 8);
        choices.getStyleClass().add("tag-choice-list");
        ToggleGroup group = new ToggleGroup();
        tagGroups.put(title, group);
        for (String tag : tags) {
            ToggleButton button = new ToggleButton(tag);
            button.getStyleClass().add("tag-choice");
            button.setToggleGroup(group);
            tagButtons.put(tag, button);
            choices.getChildren().add(button);
        }
        VBox section = new VBox(10, heading, choices);
        section.getStyleClass().add("tag-section");
        return section;
    }

    private Set<String> selectedTags() {
        Set<String> selected = new LinkedHashSet<>();
        tagButtons.forEach((tag, button) -> {
            if (button.isSelected()) {
                selected.add(tag);
            }
        });
        return Set.copyOf(selected);
    }

    private String selectedMealPeriod() {
        for (String period : List.of("Breakfast", "Lunch", "Dinner", "Late-night snack")) {
            ToggleButton button = tagButtons.get(period);
            if (button != null && button.isSelected()) {
                return period;
            }
        }
        return preferredMealPeriod;
    }

    private void resetChoices() {
        tagButtons.values().forEach(button -> button.setSelected(false));
        status.setText("All tags cleared. Choose any options, or plan without tags.");
    }

    private void randomizeChoices() {
        // Retry until the random picks leave at least one recipe that fits them.
        for (int attempt = 0; attempt < 20; attempt++) {
            pickRandomTags();
            if (!RecipeCatalog.matching(selectedTags()).isEmpty()) {
                break;
            }
        }
        status.setText("Random tags selected. You can still change any choice.");
    }

    private void pickRandomTags() {
        // Diet choices are the user's requirements, not something to randomize: keep them.
        Set<String> diet = new LinkedHashSet<>(selectedTags());
        diet.retainAll(RecipeCatalog.DIET_TAGS);
        resetChoices();
        diet.forEach(tag -> tagButtons.get(tag).setSelected(true));
        List<List<String>> groups = new ArrayList<>(List.of(
                List.of("Sour", "Sweet", "Bitter", "Spicy", "Light", "Hearty"),
                List.of("Breakfast", "Lunch", "Dinner", "Late-night snack"),
                List.of("Hot", "Cold"),
                List.of("Comforting", "Fresh", "Cozy", "Celebratory", "Quick")
        ));
        int count = ThreadLocalRandom.current().nextInt(2, groups.size() + 1);
        for (int i = 0; i < count; i++) {
            int groupIndex = ThreadLocalRandom.current().nextInt(groups.size());
            List<String> group = groups.remove(groupIndex);
            String tag = group.get(ThreadLocalRandom.current().nextInt(group.size()));
            tagButtons.get(tag).setSelected(true);
        }
    }

    @FunctionalInterface
    public interface PlanSaver {
        boolean save(LocalDate date, String mealPeriod, Recipe recipe, LocalTime time);
    }
}

