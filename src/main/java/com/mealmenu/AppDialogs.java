package com.mealmenu;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class AppDialogs {

    private static final List<String> MEAL_PERIODS = List.of(
            "Breakfast", "Lunch", "Dinner", "Late-night snack"
    );

    private AppDialogs() {
    }

    static void showRecipeDetails(Stage owner, Recipe recipe) {
        if (EatPlan.isEat(recipe.id())) {
            showEatOutDetails(owner, recipe);
            return;
        }
        showScrollModal(owner, recipe.name(), 322, 520, recipeContent(recipe));
    }

    private static VBox recipeContent(Recipe recipe) {
        if (EatPlan.isEat(recipe.id())) {
            return eatOutContent(recipe);
        }
        VBox details = new VBox(12);
        details.setPadding(new Insets(14));
        details.getStyleClass().add("dialog-content");

        HBox meta = new HBox(8,
                badge(recipe.minutes() + " min"),
                badge(recipe.tags().stream().limit(3).reduce((a, b) -> a + " · " + b)
                        .orElse("American classic")));
        meta.setAlignment(Pos.CENTER_LEFT);
        Label description = new Label(recipe.description());
        description.getStyleClass().add("recipe-description");
        description.setWrapText(true);
        Label ingredientsTitle = sectionTitle("Ingredients");
        Label ingredients = new Label(String.join("  •  ", recipe.ingredients()));
        ingredients.getStyleClass().add("recipe-detail");
        ingredients.setWrapText(true);
        VBox steps = new VBox(8);
        for (int index = 0; index < recipe.steps().size(); index++) {
            Label step = new Label((index + 1) + ".  " + recipe.steps().get(index));
            step.getStyleClass().add("recipe-detail");
            step.setWrapText(true);
            steps.getChildren().add(step);
        }
        details.getChildren().addAll(meta, description, ingredientsTitle,
                ingredients, sectionTitle("How to make it"), steps);
        return details;
    }

    static boolean showPlannedRecipeDetails(Stage owner, DailyPlan plan, MealPlanStore store) {
        DailyPlan current = store.find(plan).orElse(null);
        if (current == null || !current.isToday(store.now())) {
            return false;
        }
        Recipe recipe = RecipeCatalog.byId(current.recipeId()).orElse(null);
        if (recipe == null) {
            return false;
        }
        Stage stage = createModal(owner, recipe.name(), 322, 560);
        setCenter(stage, scrollContent(recipeContent(recipe)));
        Label scheduled = new Label(current.mealPeriod() + " · " + current.timeText());
        scheduled.getStyleClass().add("muted-text");
        Label timing = new Label();
        timing.setId("plan-completion-countdown");
        timing.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("status-text");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);
        Button complete = new Button("Complete");
        complete.setId("complete-plan");
        complete.getStyleClass().add("primary-button");
        complete.setMaxWidth(Double.MAX_VALUE);
        boolean[] completed = {false};
        Runnable update = () -> {
            DailyPlan latest = store.find(plan).orElse(null);
            LocalDateTime now = store.now();
            complete.setDisable(latest == null || !latest.canComplete(now));
            if (latest == null) {
                timing.setText("This plan is no longer scheduled.");
            } else {
                scheduled.setText(latest.mealPeriod() + " · " + latest.timeText());
                updateTimingLabel(timing, latest, now);
                if (!latest.isToday(now)) {
                    timing.setText("Only today's plans can be completed.");
                }
            }
        };
        complete.setOnAction(event -> {
            // Storage enforces the date and time again at the moment of completion.
            if (store.complete(plan)) {
                completed[0] = true;
                stage.close();
            } else {
                update.run();
                error.setText("Could not complete this plan. Check its time and try again.");
                error.setManaged(true);
                error.setVisible(true);
            }
        });
        VBox footer = new VBox(7, scheduled, timing, error, complete);
        footer.getStyleClass().add("dialog-content");
        footer.setPadding(new Insets(10, 14, 14, 14));
        ((BorderPane) stage.getScene().getRoot()).setBottom(footer);
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> update.run()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        update.run();
        countdown.play();
        try {
            stage.showAndWait();
        } finally {
            countdown.stop();
        }
        return completed[0];
    }

    static void updateTimingLabel(Label label, DailyPlan plan, LocalDateTime now) {
        label.setText(plan.timingText(now));
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: "
                + (plan.canComplete(now) ? "#65836A" : "#91A295") + ";");
    }

    static void showPlanActions(Stage owner, Node anchor, DailyPlan plan,
                                MealPlanStore store, Consumer<String> onResult) {
        ContextMenu menu = new ContextMenu();
        MenuItem edit = new MenuItem("Edit");
        MenuItem delete = new MenuItem("Delete");
        MenuItem copy = new MenuItem("Copy to a future date");
        edit.setOnAction(event -> showEditPlan(owner, plan).ifPresent(change -> {
            boolean saved = store.update(plan, change.mealPeriod(),
                    change.recipe().id(), change.time());
            onResult.accept(saved ? "Menu item updated." : "Could not save this change.");
        }));
        delete.setOnAction(event -> {
            boolean saved = store.remove(plan);
            onResult.accept(saved ? "Menu item deleted." : "Could not save this change.");
        });
        copy.setOnAction(event -> chooseFutureDate(owner).ifPresent(date -> {
            boolean saved = store.copyTo(plan, date);
            onResult.accept(saved ? "Copied to " + date.format(DateTimeFormatter.ofPattern(
                    "MMM d", Locale.ENGLISH)) + "." : "Could not save the copied meal.");
        }));
        menu.getItems().setAll(edit, delete, copy);
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private static void showEatOutDetails(Stage owner, Recipe recipe) {
        showScrollModal(owner, "Eating Out", 322, 300, eatOutContent(recipe));
    }

    private static VBox eatOutContent(Recipe recipe) {
        VBox details = new VBox(12);
        details.setPadding(new Insets(14));
        details.getStyleClass().add("dialog-content");
        Label name = new Label(recipe.name());
        name.getStyleClass().add("recipe-title");
        name.setWrapText(true);
        details.getChildren().add(name);
        EatPlan.place(recipe.id()).ifPresent(place -> {
            Label address = new Label(place.address());
            address.getStyleClass().add("recipe-detail");
            address.setWrapText(true);
            Button navigate = new Button("Navigate to " + place.name());
            navigate.getStyleClass().add("primary-button");
            navigate.setMaxWidth(Double.MAX_VALUE);
            navigate.setOnAction(event -> place.navigate());
            details.getChildren().addAll(address, navigate);
        });
        return details;
    }

    static Optional<QuickStartSelection> showQuickStart(Stage owner) {
        Stage stage = createModal(owner, "Quick Start", 318, 380);
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));
        Label intro = new Label("What sounds right for you today?");
        intro.getStyleClass().add("muted-text");
        GridPane grid = new GridPane();
        grid.setHgap(9);
        grid.setVgap(9);
        String[][] options = {
                {"Breakfast", "Breakfast"}, {"Lunch", "Lunch"},
                {"Dinner", "Dinner"}, {"Late-night snack", "Late-night snack"},
                {"Hearty", "Hearty"}, {"Light", "Light"}
        };
        QuickStartSelection[] selected = new QuickStartSelection[1];
        for (int i = 0; i < options.length; i++) {
            String tag = options[i][0];
            String period = options[i][1];
            Button choice = new Button(tag);
            choice.getStyleClass().add("quick-choice-button");
            choice.setMaxWidth(Double.MAX_VALUE);
            choice.setMinHeight(48);
            GridPane.setHgrow(choice, Priority.ALWAYS);
            choice.setOnAction(event -> {
                selected[0] = new QuickStartSelection(tag,
                        MEAL_PERIODS.contains(period) ? period : defaultMealPeriod());
                stage.close();
            });
            grid.add(choice, i % 2, i / 2);
        }
        body.getChildren().addAll(intro, grid);
        setCenter(stage, body);
        stage.showAndWait();
        return Optional.ofNullable(selected[0]);
    }

    static Optional<LocalDate> showSchedule(Stage owner, MealPlanStore store) {
        return showSchedule(owner, store, () -> { });
    }

    static Optional<LocalDate> showSchedule(Stage owner, MealPlanStore store, Runnable onChanged) {
        Stage stage = createModal(owner, "Meal Schedule", 322, 500);
        VBox body = new VBox(12);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));
        Label hint = new Label("Open today's meals. Use the options menu to manage other days.");
        hint.getStyleClass().add("muted-text");
        hint.setWrapText(true);
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.setEditable(false);
        datePicker.setId("schedule-date");
        VBox plans = new VBox(8);
        ScrollPane planScroll = new ScrollPane(plans);
        planScroll.setFitToWidth(true);
        planScroll.setPrefHeight(235);
        planScroll.getStyleClass().add("dialog-scroll");
        Label status = new Label();
        status.getStyleClass().add("status-text");
        status.setWrapText(true);
        List<ScheduleItem> items = new ArrayList<>();
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            plans.getChildren().clear();
            items.clear();
            LocalDate date = datePicker.getValue();
            if (date == null) {
                return;
            }
            List<DailyPlan> dailyPlans = store.getPlans(date);
            if (dailyPlans.isEmpty()) {
                Label empty = new Label("No meals planned for this day.");
                empty.getStyleClass().add("muted-text");
                plans.getChildren().add(empty);
                return;
            }
            for (DailyPlan plan : dailyPlans) {
                Recipe recipe = RecipeCatalog.byId(plan.recipeId()).orElse(null);
                if (recipe == null) {
                    continue;
                }
                Button row = new Button(plan.mealPeriod() + " · " + plan.timeText()
                        + "\n" + recipe.name());
                row.getStyleClass().add("schedule-row");
                row.setMaxWidth(Double.MAX_VALUE);
                row.setWrapText(true);
                row.setId("schedule-plan-" + plan.id());
                row.setOnAction(event -> {
                    if (showPlannedRecipeDetails(stage, plan, store)) {
                        refresh[0].run();
                        status.setText("Meal completed and removed from your schedule.");
                        onChanged.run();
                    }
                });
                Label timing = new Label();
                timing.setWrapText(true);
                Button more = new Button("⋯");
                more.getStyleClass().add("more-button");
                more.setAccessibleText("Meal options");
                more.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
                more.setOnAction(event -> showPlanActions(stage, more, plan, store, message -> {
                    refresh[0].run();
                    status.setText(message);
                    onChanged.run();
                }));
                VBox text = new VBox(4, row, timing);
                text.setMinWidth(0);
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox entry = new HBox(6, text, more);
                entry.setAlignment(Pos.CENTER_LEFT);
                plans.getChildren().add(entry);
                row.setDisable(!plan.isToday(store.now()));
                updateTimingLabel(timing, plan, store.now());
                items.add(new ScheduleItem(plan, row, timing));
            }
        };
        datePicker.valueProperty().addListener((observable, oldDate, newDate) -> {
            if (newDate != null) {
                refresh[0].run();
            }
        });
        refresh[0].run();

        LocalDate[] result = new LocalDate[1];
        Button planButton = new Button("Plan a Meal for This Day");
        planButton.getStyleClass().add("primary-button");
        planButton.setMaxWidth(Double.MAX_VALUE);
        planButton.setOnAction(event -> {
            result[0] = datePicker.getValue();
            stage.close();
        });
        body.getChildren().addAll(hint, datePicker, sectionTitle("Planned Meals"),
                planScroll, status, planButton);
        VBox.setVgrow(planScroll, Priority.ALWAYS);
        setCenter(stage, body);
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            LocalDateTime now = store.now();
            for (ScheduleItem item : items) {
                item.open().setDisable(!item.plan().isToday(now));
                updateTimingLabel(item.timing(), item.plan(), now);
            }
        }));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
        try {
            stage.showAndWait();
        } finally {
            countdown.stop();
        }
        return Optional.ofNullable(result[0]);
    }

    static Optional<PlanSelection> showRecommendation(
            Stage owner,
            Set<String> selectedTags,
            LocalDate targetDate,
            String initialMealPeriod
    ) {
        Stage stage = createModal(owner, "Your Meal Idea", 326, 530);
        VBox body = new VBox(11);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));

        DatePicker datePicker = new DatePicker(targetDate == null ? LocalDate.now() : targetDate);
        datePicker.setMaxWidth(Double.MAX_VALUE);
        datePicker.setEditable(false);
        ComboBox<String> periodPicker = new ComboBox<>();
        periodPicker.getItems().setAll(MEAL_PERIODS);
        periodPicker.setValue(MEAL_PERIODS.contains(initialMealPeriod)
                ? initialMealPeriod : defaultMealPeriod());
        periodPicker.setMaxWidth(Double.MAX_VALUE);
        PlanTimePicker timePicker = new PlanTimePicker(DailyPlan.defaultTime(periodPicker.getValue()));
        periodPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                timePicker.setValue(DailyPlan.defaultTime(newValue));
            }
        });

        Label recipeName = new Label();
        recipeName.getStyleClass().add("recipe-title");
        recipeName.setWrapText(true);
        Label recipeMeta = new Label();
        recipeMeta.getStyleClass().add("muted-text");
        Label description = new Label();
        description.getStyleClass().add("recipe-description");
        description.setWrapText(true);
        Label ingredients = new Label();
        ingredients.getStyleClass().add("recipe-detail");
        ingredients.setWrapText(true);

        Recipe[] current = new Recipe[1];
        Set<String> seen = new java.util.HashSet<>();
        int matchCount = RecipeCatalog.matching(selectedTags).size();
        Consumer<Recipe> display = recipe -> {
            current[0] = recipe;
            seen.add(recipe.id());
            recipeName.setText(recipe.name());
            // Tags the user asked for first, so it's clear why this recipe was picked.
            List<String> shownTags = new java.util.ArrayList<>(recipe.tags().stream()
                    .filter(selectedTags::contains).sorted().toList());
            recipe.tags().stream().filter(tag -> !selectedTags.contains(tag)).sorted()
                    .forEach(shownTags::add);
            recipeMeta.setText(recipe.minutes() + " min  ·  "
                    + String.join("  ·  ", shownTags.subList(0, Math.min(4, shownTags.size()))));
            description.setText(recipe.description());
            ingredients.setText("Ingredients: " + String.join(", ", recipe.ingredients()));
        };
        Button viewRecipe = new Button("View Full Recipe");
        viewRecipe.getStyleClass().add("link-button");
        viewRecipe.setOnAction(event -> showRecipeDetails(stage, current[0]));

        Label addStatus = new Label();
        addStatus.getStyleClass().add("status-text");
        addStatus.setWrapText(true);
        Button another = new Button("Try Another");
        another.getStyleClass().add("secondary-button");
        another.setMaxWidth(Double.MAX_VALUE);
        another.setOnAction(event -> {
            if (seen.size() >= matchCount) {
                seen.clear();
                seen.add(current[0].id());
            }
            RecipeCatalog.recommend(selectedTags, seen).ifPresent(display);
            addStatus.setText(matchCount + (matchCount == 1 ? " recipe matches" : " recipes match")
                    + " your choices.");
        });

        PlanSelection[] result = new PlanSelection[1];
        Button add = new Button("Add to Menu");
        add.getStyleClass().add("primary-button");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> {
            if (datePicker.getValue() == null || periodPicker.getValue() == null) {
                addStatus.setText("Choose a date and meal time first.");
                return;
            }
            result[0] = new PlanSelection(datePicker.getValue(),
                    periodPicker.getValue(), current[0], timePicker.getValue());
            stage.close();
        });

        HBox actions = new HBox(8, another, add);
        HBox.setHgrow(another, Priority.ALWAYS);
        HBox.setHgrow(add, Priority.ALWAYS);
        another.setMaxWidth(Double.MAX_VALUE);
        Optional<Recipe> first = RecipeCatalog.recommend(selectedTags, seen);
        if (first.isPresent()) {
            display.accept(first.get());
            addStatus.setText(matchCount + (matchCount == 1 ? " recipe matches" : " recipes match")
                    + " your choices.");
            body.getChildren().addAll(
                    new Label("Plan this suggestion for:"), datePicker, periodPicker,
                    sectionTitle("Scheduled Time (24-hour)"), timePicker,
                    recipeName, recipeMeta, description, ingredients, viewRecipe,
                    addStatus
            );
            actions.setPadding(new Insets(10, 14, 14, 14));
            ((BorderPane) stage.getScene().getRoot()).setBottom(actions);
        } else {
            // Diet and meal-time choices are strict, so say so instead of suggesting something that breaks them.
            List<String> required = selectedTags.stream()
                    .filter(tag -> RecipeCatalog.DIET_TAGS.contains(tag)
                            || RecipeCatalog.MEAL_TAGS.contains(tag))
                    .sorted().toList();
            recipeName.setText("No recipe fits all of these");
            description.setText("Nothing in the recipe list is " + String.join(" + ", required)
                    + ". Remove one of these choices, or check the Eat tab for dining hall dishes "
                    + "you can filter by diet.");
            body.getChildren().addAll(recipeName, description);
        }
        setCenter(stage, scrollContent(body));
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    static Optional<PlanEdit> showEditPlan(Stage owner, DailyPlan plan) {
        Stage stage = createModal(owner, "Edit Planned Meal", 318, 390);
        VBox body = new VBox(12);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));
        ComboBox<String> period = new ComboBox<>();
        period.getItems().setAll(MEAL_PERIODS);
        period.setValue(plan.mealPeriod());
        period.setMaxWidth(Double.MAX_VALUE);
        PlanTimePicker timePicker = new PlanTimePicker(plan.scheduledTime());
        ComboBox<Recipe> recipe = new ComboBox<>();
        recipe.getItems().setAll(RecipeCatalog.all());
        Recipe currentRecipe = RecipeCatalog.byId(plan.recipeId()).orElse(RecipeCatalog.all().get(0));
        if (!recipe.getItems().contains(currentRecipe)) {
            recipe.getItems().add(0, currentRecipe);
        }
        recipe.setValue(currentRecipe);
        recipe.setMaxWidth(Double.MAX_VALUE);
        PlanEdit[] result = new PlanEdit[1];
        Button save = new Button("Save Changes");
        save.getStyleClass().add("primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> {
            result[0] = new PlanEdit(period.getValue(), recipe.getValue(), timePicker.getValue());
            stage.close();
        });
        body.getChildren().addAll(sectionTitle("Meal Time"), period,
                sectionTitle("Scheduled Time (24-hour)"), timePicker,
                sectionTitle("Recipe"), recipe, save);
        setCenter(stage, body);
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    static Optional<LocalDate> chooseFutureDate(Stage owner) {
        Stage stage = createModal(owner, "Copy to a Future Date", 318, 230);
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));
        Label hint = new Label("Choose a date after today for this meal.");
        hint.getStyleClass().add("muted-text");
        DatePicker picker = new DatePicker(LocalDate.now().plusDays(1));
        picker.setMaxWidth(Double.MAX_VALUE);
        Callback<javafx.scene.control.DatePicker, DateCell> dayCellFactory = datePicker ->
                new DateCell() {
                    @Override
                    public void updateItem(LocalDate date, boolean empty) {
                        super.updateItem(date, empty);
                        setDisable(empty || date == null || !date.isAfter(LocalDate.now()));
                    }
                };
        picker.setDayCellFactory(dayCellFactory);
        LocalDate[] result = new LocalDate[1];
        Button copy = new Button("Choose Date");
        copy.getStyleClass().add("primary-button");
        copy.setMaxWidth(Double.MAX_VALUE);
        copy.setOnAction(event -> {
            if (picker.getValue() != null && picker.getValue().isAfter(LocalDate.now())) {
                result[0] = picker.getValue();
                stage.close();
            }
        });
        body.getChildren().addAll(hint, picker, copy);
        setCenter(stage, body);
        stage.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    static void showScrollModal(Stage owner, String title, double width,
                                       double height, Node content) {
        Stage stage = createModal(owner, title, width, height);
        setCenter(stage, scrollContent(content));
        stage.showAndWait();
    }

    private static ScrollPane scrollContent(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    static Stage createModal(Stage owner, String title,
                                     double width, double height) {
        Stage stage = new Stage();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }
        stage.setTitle(title);
        stage.setResizable(false);
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        HBox header = new HBox(8);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 14, 10, 16));
        Label heading = EatDialogs.cjk(new Label(title));
        heading.getStyleClass().add("dialog-title");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button("×");
        close.getStyleClass().add("dialog-close-button");
        close.setAccessibleText("Close dialog");
        close.setOnAction(event -> stage.close());
        header.getChildren().addAll(heading, spacer, close);
        root.setTop(header);
        Scene scene = new Scene(root, width, height);
        URL stylesheet = AppDialogs.class.getResource("/com/mealmenu/app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
                event.consume();
            }
        });
        stage.setScene(scene);
        return stage;
    }

    static void setCenter(Stage stage, Node center) {
        ((BorderPane) stage.getScene().getRoot()).setCenter(center);
    }

    static Label badge(String text) {
        Label badge = new Label(text);
        badge.getStyleClass().add("time-badge");
        badge.setWrapText(true);
        return badge;
    }

    static Label sectionTitle(String text) {
        Label title = new Label(text);
        title.getStyleClass().add("card-heading");
        return title;
    }

    static String defaultMealPeriod() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 10) {
            return "Breakfast";
        }
        if (hour < 15) {
            return "Lunch";
        }
        if (hour < 22) {
            return "Dinner";
        }
        return "Late-night snack";
    }

    record QuickStartSelection(String tag, String mealPeriod) {
    }

    record PlanSelection(LocalDate date, String mealPeriod, Recipe recipe, LocalTime time) {
    }

    record PlanEdit(String mealPeriod, Recipe recipe, LocalTime time) {
    }

    private record ScheduleItem(DailyPlan plan, Button open, Label timing) {
    }
}

