package com.mealmenu;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

public final class HomePage extends ScrollPane {

    private final Stage owner;
    private final MealPlanStore planStore;
    private final AppUsageTracker usageTracker;
    private final LocalAccountService accountService;
    private final Consumer<LocalDate> onSchedulePlan;
    private final Consumer<AppDialogs.QuickStartSelection> onQuickStart;
    private final Label greeting = new Label();
    private final Label todayDate = new Label();
    private final Label clock = new Label();
    private final Label daysUsing = new Label();
    private final Label reminder = new Label();
    private final VBox todayPlanList = new VBox(8);
    private final VBox menuList = new VBox(9);
    private final Label menuStatus = new Label();
    private final Timeline liveClock;
    private final List<TimedLabel> todayTimers = new ArrayList<>();
    private final List<MenuTimer> menuTimers = new ArrayList<>();
    private LocalDate cachedDate;
    private final VBox eatNowList = new VBox(8);
    private Consumer<String> onOpenHall = slug -> { };
    private long eatNowRefreshedAt;

    public HomePage(
            Stage owner,
            MealPlanStore planStore,
            AppUsageTracker usageTracker,
            LocalAccountService accountService,
            Consumer<LocalDate> onSchedulePlan,
            Consumer<AppDialogs.QuickStartSelection> onQuickStart
    ) {
        this.owner = owner;
        this.planStore = planStore;
        this.usageTracker = usageTracker;
        this.accountService = accountService;
        this.onSchedulePlan = onSchedulePlan;
        this.onQuickStart = onQuickStart;
        eatNowList.getChildren().add(new Label("Checking the dining halls…"));

        getStyleClass().add("page-scroll");
        setFitToWidth(true);
        setPannable(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        VBox content = new VBox(15);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(18, 17, 20, 17));

        VBox greetingCard = new VBox(7, greeting, todayDate, clock, daysUsing);
        greetingCard.getStyleClass().add("greeting-card");
        greeting.getStyleClass().add("greeting-title");
        todayDate.getStyleClass().add("greeting-date");
        clock.getStyleClass().add("greeting-clock");
        daysUsing.getStyleClass().add("greeting-days");

        VBox reminderCard = card("Today's Plan", reminder, todayPlanList);
        reminder.getStyleClass().add("muted-text");

        Button scheduleButton = new Button("Schedule");
        scheduleButton.getStyleClass().add("secondary-button");
        scheduleButton.setMaxWidth(Double.MAX_VALUE);
        scheduleButton.setMinHeight(48);
        scheduleButton.setOnAction(event -> openSchedule());

        Button quickStartButton = new Button("Quick Start");
        quickStartButton.getStyleClass().add("primary-button");
        quickStartButton.setMaxWidth(Double.MAX_VALUE);
        quickStartButton.setMinHeight(48);
        quickStartButton.setOnAction(event -> AppDialogs.showQuickStart(owner)
                .ifPresent(onQuickStart));
        HBox actions = new HBox(10, scheduleButton, quickStartButton);
        actions.setAlignment(Pos.CENTER);
        HBox.setHgrow(scheduleButton, Priority.ALWAYS);
        HBox.setHgrow(quickStartButton, Priority.ALWAYS);
        VBox actionSection = new VBox(7,
                new Label("Make a plan"), actions);
        actionSection.getStyleClass().add("home-action-section");

        menuStatus.getStyleClass().add("status-text");
        menuStatus.setWrapText(true);
        VBox menuCard = card("Upcoming Confirmed Menu", menuList, menuStatus);
        VBox eatNowCard = card("Eat Now · Dining Halls", eatNowList);
        content.getChildren().addAll(greetingCard, eatNowCard, reminderCard, actionSection, menuCard);
        setContent(content);

        liveClock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateGreeting()));
        liveClock.setCycleCount(Timeline.INDEFINITE);
        liveClock.play();
        refresh();
    }

    /** Where a tap on a dining hall in "Eat Now" goes (the Eat tab). */
    public void setOnOpenHall(Consumer<String> handler) {
        onOpenHall = handler;
        refreshEatNow();
    }

    public void refresh() {
        if (System.currentTimeMillis() - eatNowRefreshedAt > 5 * 60_000) {
            refreshEatNow();
        }
        cachedDate = LocalDate.now();
        updateGreeting();
        refreshTodayPlans();
        refreshMenu();
        updatePlanTiming(LocalDateTime.now());
    }

    /** Which halls are open right now and a few dishes from the current meal. */
    private void refreshEatNow() {
        eatNowRefreshedAt = System.currentTimeMillis();
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(Campus.ZONE);
        DiningService dining = DiningService.get();
        List<DiningService.Hall> halls = new java.util.ArrayList<>(dining.halls());
        halls.sort(java.util.Comparator.comparing(hall -> !hall.openHours().status(now).open()));
        eatNowList.getChildren().clear();
        for (DiningService.Hall hall : halls) {
            OpenHours.Status status = hall.openHours().status(now);
            Label name = new Label(hall.shortName() + "  ·  " + status.text());
            name.getStyleClass().addAll("recipe-title", "open-" + status.state().name().toLowerCase(Locale.ROOT));
            Label dishes = new Label(status.open() ? "Loading menu…" : hall.campus());
            dishes.getStyleClass().add("dish-tags");
            dishes.setWrapText(true);
            VBox row = new VBox(2, name, dishes);
            row.getStyleClass().add("dish-row");
            row.setOnMouseClicked(event -> onOpenHall.accept(hall.slug()));
            eatNowList.getChildren().add(row);
            String meal = DiningService.currentMealSlug(hall, now);
            if (status.open() && meal != null) {
                dining.menu(hall.slug(), meal, now.toLocalDate()).thenAccept(result ->
                        javafx.application.Platform.runLater(() -> {
                            List<String> top = DiningService.highlights(result.stations(), 3);
                            String mealName = hall.meals().stream().filter(m -> m.slug().equals(meal))
                                    .map(DiningService.Meal::name).findFirst().orElse("");
                            dishes.setText(top.isEmpty() ? mealName + ": no menu posted"
                                    : mealName + ": " + String.join(", ", top));
                        }));
            }
        }
    }

    public void stopClock() {
        liveClock.stop();
    }

    private void updateGreeting() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate currentDate = now.toLocalDate();
        if (!currentDate.equals(cachedDate)) {
            cachedDate = currentDate;
            refreshTodayPlans();
            refreshMenu();
        }
        int hour = now.getHour();
        String hello;
        if (hour >= 5 && hour < 12) {
            hello = "Good morning";
        } else if (hour >= 12 && hour < 17) {
            hello = "Good afternoon";
        } else if (hour >= 17 && hour < 22) {
            hello = "Good evening";
        } else {
            hello = "Hello, night owl";
        }
        greeting.setText(hello + ", " + currentUsername() + "!");
        todayDate.setText(now.format(DateTimeFormatter.ofPattern(
                "EEEE, MMMM d, yyyy", Locale.ENGLISH)));
        clock.setText(now.format(DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH)));
        daysUsing.setText("Day " + usageTracker.daysUsing() + " with dailyrecipes");
        updatePlanTiming(now);
    }

    private String currentUsername() {
        return accountService.username();
    }

    private void refreshTodayPlans() {
        todayPlanList.getChildren().clear();
        todayTimers.clear();
        List<DailyPlan> plans = planStore.getPlans(LocalDate.now());
        if (plans.isEmpty()) {
            reminder.setText("Nothing is scheduled today yet. Add a meal when you are ready.");
            Label empty = new Label("Your day is open.");
            empty.getStyleClass().add("muted-text");
            todayPlanList.getChildren().add(empty);
            return;
        }
        reminder.setText(plans.size() == 1
                ? "You have 1 meal planned for today."
                : "You have " + plans.size() + " meals planned for today.");
        for (DailyPlan plan : plans) {
            Recipe recipe = RecipeCatalog.byId(plan.recipeId()).orElse(null);
            if (recipe == null) {
                continue;
            }
            Label item = EatDialogs.cjk(new Label(plan.mealPeriod() + " · " + plan.timeText()
                    + "  ·  " + recipe.name()));
            item.getStyleClass().add("reminder-item");
            item.setWrapText(true);
            Label timing = new Label();
            timing.setWrapText(true);
            todayPlanList.getChildren().add(new VBox(3, item, timing));
            todayTimers.add(new TimedLabel(plan, timing));
        }
    }

    private void refreshMenu() {
        menuList.getChildren().clear();
        menuTimers.clear();
        menuStatus.setText("");
        List<DailyPlan> plans = planStore.getUpcomingPlans(LocalDate.now());
        if (plans.isEmpty()) {
            Label empty = new Label("Dishes you add from Eat or Kitchen will appear here.");
            empty.getStyleClass().add("muted-text");
            empty.setWrapText(true);
            menuList.getChildren().add(empty);
            return;
        }
        for (DailyPlan plan : plans) {
            RecipeCatalog.byId(plan.recipeId())
                    .ifPresent(recipe -> menuList.getChildren().add(createMenuRow(plan, recipe)));
        }
    }

    private HBox createMenuRow(DailyPlan plan, Recipe recipe) {
        String dateLabel = plan.date().equals(LocalDate.now())
                ? plan.mealPeriod()
                : plan.date().format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                + " · " + plan.mealPeriod();
        Label period = new Label(dateLabel);
        period.setText(dateLabel + "\n" + plan.timeText());
        period.getStyleClass().add("time-badge");
        period.setWrapText(true);
        period.setMinWidth(62);
        period.setMaxWidth(88);
        Label name = EatDialogs.cjk(new Label(recipe.name()));
        name.getStyleClass().add("recipe-title");
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        Label timing = new Label();
        timing.setId("plan-countdown-" + plan.id());
        timing.setWrapText(true);
        VBox details = new VBox(4, name, timing);
        details.setMinWidth(0);
        details.setMaxWidth(Double.MAX_VALUE);
        Button more = new Button("⋯");
        more.getStyleClass().add("more-button");
        more.setAccessibleText("Meal options");
        more.setId("plan-options-" + plan.id());
        more.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(8, period, details, more);
        row.setId("menu-plan-" + plan.id());
        row.getStyleClass().add("menu-row");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(details, Priority.ALWAYS);
        menuTimers.add(new MenuTimer(plan, timing, name, row));
        boolean[] longPressTriggered = {false};
        row.setOnMouseClicked(event -> {
            if (longPressTriggered[0]) {
                longPressTriggered[0] = false;
                event.consume();
                return;
            }
            if (event.getButton() == MouseButton.PRIMARY && event.isStillSincePress()
                    && plan.isToday(LocalDateTime.now())) {
                if (AppDialogs.showPlannedRecipeDetails(owner, plan, planStore)) {
                    refresh();
                    menuStatus.setText("Meal completed and removed from your schedule.");
                }
            }
        });
        long[] pressedAt = {0};
        double[] pressPoint = {0, 0};
        row.setOnMousePressed(event -> {
            pressedAt[0] = System.nanoTime();
            pressPoint[0] = event.getScreenX();
            pressPoint[1] = event.getScreenY();
            longPressTriggered[0] = false;
        });
        // Dragging to scroll is not a long press.
        row.setOnMouseDragged(event -> {
            if (Math.abs(event.getScreenX() - pressPoint[0]) > 8
                    || Math.abs(event.getScreenY() - pressPoint[1]) > 8) {
                pressedAt[0] = 0;
            }
        });
        row.setOnMouseReleased(event -> {
            if (pressedAt[0] != 0 && System.nanoTime() - pressedAt[0] > 550_000_000L) {
                longPressTriggered[0] = true;
                showMenuActions(row, plan);
            }
            pressedAt[0] = 0;
        });
        more.setOnAction(event -> {
            event.consume();
            showMenuActions(more, plan);
        });
        more.setOnMouseClicked(javafx.scene.input.MouseEvent::consume);
        return row;
    }

    private void showMenuActions(javafx.scene.Node anchor, DailyPlan plan) {
        AppDialogs.showPlanActions(owner, anchor, plan, planStore, message -> {
            refresh();
            menuStatus.setText(message);
        });
    }

    private void openSchedule() {
        AppDialogs.showSchedule(owner, planStore, this::refresh).ifPresent(onSchedulePlan);
    }

    private void updatePlanTiming(LocalDateTime now) {
        for (TimedLabel item : todayTimers) {
            AppDialogs.updateTimingLabel(item.label(), item.plan(), now);
        }
        for (MenuTimer item : menuTimers) {
            AppDialogs.updateTimingLabel(item.label(), item.plan(), now);
            boolean today = item.plan().isToday(now);
            item.name().setOpacity(today ? 1 : 0.58);
            item.row().setCursor(today ? Cursor.HAND : Cursor.DEFAULT);
        }
    }

    private VBox card(String title, javafx.scene.Node... children) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-heading");
        VBox card = new VBox(10, heading);
        card.getStyleClass().add("content-card");
        card.getChildren().addAll(children);
        return card;
    }

    private record TimedLabel(DailyPlan plan, Label label) { }

    private record MenuTimer(DailyPlan plan, Label label, Label name, HBox row) { }
}

