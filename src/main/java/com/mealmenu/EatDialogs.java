package com.mealmenu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detail dialogs for dining hall dishes and restaurants. */
final class EatDialogs {

    private static final List<String> MEAL_PERIODS = List.of(
            "Breakfast", "Lunch", "Dinner", "Late-night snack");

    private EatDialogs() {
    }

    static void showDiningItem(Stage owner, DiningService.Hall hall, String station,
                               DiningService.Item item, LocalDate date, String period,
                               EatPage.PlanAdder planAdder) {
        VBox body = new VBox(11);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));
        Label name = new Label(item.name());
        name.getStyleClass().add("recipe-title");
        name.setWrapText(true);
        Label where = new Label(station + " · " + hall.name());
        where.getStyleClass().add("muted-text");
        where.setWrapText(true);
        FlowPane facts = new FlowPane(6, 6);
        if (item.serving() != null) {
            facts.getChildren().add(AppDialogs.badge(item.serving()));
        }
        if (item.calories() != null) {
            facts.getChildren().add(AppDialogs.badge(Math.round(item.calories()) + " cal"));
        }
        if (item.protein() != null) {
            facts.getChildren().add(AppDialogs.badge(trim(item.protein()) + "g protein"));
        }
        if (item.carbs() != null) {
            facts.getChildren().add(AppDialogs.badge(trim(item.carbs()) + "g carbs"));
        }
        if (item.fat() != null) {
            facts.getChildren().add(AppDialogs.badge(trim(item.fat()) + "g fat"));
        }
        body.getChildren().addAll(name, where, facts);
        if (!item.icons().isEmpty()) {
            Label icons = new Label(String.join(" · ", item.icons()));
            icons.getStyleClass().add("recipe-detail");
            icons.setWrapText(true);
            body.getChildren().addAll(AppDialogs.sectionTitle("Diet & allergens"), icons);
        }
        Label note = new Label("Menu and nutrition from Rutgers Dining (Nutrislice). "
                + "Menus can change; check allergens with dining staff.");
        note.getStyleClass().add("muted-text");
        note.setWrapText(true);
        body.getChildren().add(note);

        Stage stage = AppDialogs.createModal(owner, "Dining Hall Dish", 322, 470);
        Label status = statusLabel();
        Button navigate = new Button("Navigate");
        navigate.getStyleClass().add("secondary-button");
        navigate.setOnAction(event -> Links.directions(hall.name(), hall.address(),
                hall.lat() == null ? 0 : hall.lat(), hall.lng() == null ? 0 : hall.lng()));
        Button add = new Button("Add to My Menu");
        add.getStyleClass().add("primary-button");
        add.setOnAction(event -> choosePlan(stage, item.name(), date, period).ifPresent(choice ->
                status.setText(planAdder.add(choice.date(), choice.period(),
                        EatPlan.id(hall.slug(), item.name()))
                        ? "Added to your menu." : "Could not save. Please try again.")));
        body.getChildren().addAll(actionRow(navigate, add), status);
        AppDialogs.setCenter(stage, scroll(body));
        stage.showAndWait();
    }

    static void showRestaurant(Stage owner, RestaurantCatalog.Restaurant restaurant, Campus campus,
                               EatPage.PlanAdder planAdder) {
        Stage stage = AppDialogs.createModal(owner, restaurant.name(), 330, 600);
        AppDialogs.setCenter(stage, scroll(restaurantDetail(stage, restaurant, campus, planAdder, false)));
        stage.showAndWait();
    }

    /** Restaurant info, action buttons and menu; used by the dialog and the desktop detail pane. */
    static VBox restaurantDetail(Stage owner, RestaurantCatalog.Restaurant restaurant, Campus campus,
                                 EatPage.PlanAdder planAdder, boolean withTitle) {
        Stage stage = owner;
        VBox body = new VBox(10);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));

        FlowPane badges = new FlowPane(6, 6);
        badges.getChildren().add(AppDialogs.badge(restaurant.cuisine()));
        if (restaurant.priceLevel() != null) {
            badges.getChildren().add(AppDialogs.badge(restaurant.priceLevel()));
        }
        if (restaurant.hasLocation()) {
            badges.getChildren().add(AppDialogs.badge(String.format(Locale.US, "%.1f mi from %s",
                    campus.milesTo(restaurant.lat(), restaurant.lng()), campus.label())));
        }
        if (withTitle) {
            Label title = cjk(new Label(restaurant.name()));
            title.getStyleClass().add("detail-title");
            title.setWrapText(true);
            body.getChildren().add(title);
        }
        body.getChildren().add(badges);
        body.getChildren().add(infoLine("📍 " + restaurant.address()));
        if (restaurant.hours() != null) {
            OpenHours.Status status = restaurant.statusNow();
            Label open = infoLine(status.text());
            open.getStyleClass().add("open-" + status.state().name().toLowerCase(Locale.ROOT));
            body.getChildren().addAll(open, infoLine("🕒 " + restaurant.hours()));
        }
        if (restaurant.phone() != null) {
            body.getChildren().add(infoLine("☎ " + restaurant.phone()));
        }
        if (!restaurant.tags().isEmpty()) {
            body.getChildren().add(infoLine(String.join(" · ", restaurant.tags())));
        }

        Button navigate = new Button("Navigate");
        navigate.getStyleClass().add("primary-button");
        navigate.setOnAction(event -> Links.directions(restaurant.name(), restaurant.address(),
                restaurant.lat() == null ? 0 : restaurant.lat(),
                restaurant.lng() == null ? 0 : restaurant.lng()));
        List<Button> buttons = new ArrayList<>(List.of(navigate));
        if (restaurant.website() != null) {
            buttons.add(linkButton("Website", restaurant.website()));
        }
        if (restaurant.orderUrl() != null && !restaurant.orderUrl().equals(restaurant.website())) {
            buttons.add(linkButton("Order", restaurant.orderUrl()));
        }
        body.getChildren().add(actionRow(buttons.toArray(Button[]::new)));

        Label status = statusLabel();
        body.getChildren().add(status);
        if (restaurant.menu().isEmpty()) {
            body.getChildren().add(infoLine("No menu collected yet — use Website or Order to see it."));
        } else {
            Label hint = new Label("Tap a dish to add it to your menu.");
            hint.getStyleClass().add("muted-text");
            body.getChildren().add(hint);
        }
        for (RestaurantCatalog.MenuSection section : restaurant.menu()) {
            Label sectionTitle = cjk(AppDialogs.sectionTitle(section.section()));
            sectionTitle.setWrapText(true);
            body.getChildren().add(sectionTitle);
            for (RestaurantCatalog.MenuItem item : section.items()) {
                body.getChildren().add(menuRow(stage, restaurant, item, status, planAdder));
            }
        }
        if (restaurant.menuSource() != null) {
            Button source = new Button("Menu source: " + host(restaurant.menuSource())
                    + (restaurant.verifiedOn() == null ? "" : " · checked " + restaurant.verifiedOn()));
            source.getStyleClass().add("link-button");
            source.setWrapText(true);
            source.setOnAction(event -> Links.open(restaurant.menuSource()));
            Label disclaimer = new Label("Prices and dishes may have changed since then.");
            disclaimer.getStyleClass().add("muted-text");
            body.getChildren().addAll(source, disclaimer);
        }
        return body;
    }

    private static VBox menuRow(Stage stage, RestaurantCatalog.Restaurant restaurant,
                                RestaurantCatalog.MenuItem item, Label status, EatPage.PlanAdder planAdder) {
        Label name = cjk(new Label(item.name()));
        name.getStyleClass().add("dish-name");
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label price = new Label(item.priceText());
        price.getStyleClass().add("dish-meta");
        price.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(6, name, price);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox row = new VBox(1, top);
        if (item.description() != null) {
            Label description = cjk(new Label(item.description()));
            description.getStyleClass().add("dish-tags");
            description.setWrapText(true);
            row.getChildren().add(description);
        }
        row.getStyleClass().add("dish-row");
        row.setOnMouseClicked(event -> choosePlan(stage, item.name(), LocalDate.now(),
                AppDialogs.defaultMealPeriod()).ifPresent(choice ->
                status.setText(planAdder.add(choice.date(), choice.period(),
                        EatPlan.id(restaurant.id(), item.name()))
                        ? item.name() + " added to your menu."
                        : "Could not save. Please try again.")));
        return row;
    }

    /** Small date + meal-time picker shared by both dialogs. */
    private static java.util.Optional<PlanChoice> choosePlan(Stage owner, String dish,
                                                             LocalDate date, String period) {
        Stage stage = AppDialogs.createModal(owner, "Add to My Menu", 300, 280);
        VBox body = new VBox(10);
        body.getStyleClass().add("dialog-content");
        body.setPadding(new Insets(14));
        Label label = cjk(new Label(dish));
        label.getStyleClass().add("recipe-title");
        label.setWrapText(true);
        DatePicker picker = new DatePicker(date == null ? LocalDate.now() : date);
        picker.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> periods = new ComboBox<>();
        periods.getItems().setAll(MEAL_PERIODS);
        periods.setValue(MEAL_PERIODS.contains(period) ? period : AppDialogs.defaultMealPeriod());
        periods.setMaxWidth(Double.MAX_VALUE);
        PlanChoice[] result = new PlanChoice[1];
        Button add = new Button("Add");
        add.getStyleClass().add("primary-button");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(event -> {
            if (picker.getValue() != null && periods.getValue() != null) {
                result[0] = new PlanChoice(picker.getValue(), periods.getValue());
                stage.close();
            }
        });
        body.getChildren().addAll(label, picker, periods, add);
        AppDialogs.setCenter(stage, body);
        stage.showAndWait();
        return java.util.Optional.ofNullable(result[0]);
    }

    /** JavaFX has no font fallback lists in CSS, so labels with Chinese text get a CJK font. */
    static <T extends javafx.scene.control.Labeled> T cjk(T node) {
        if (node.getText() != null && node.getText().codePoints()
                .anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            node.getStyleClass().add("cjk-text");
        }
        return node;
    }

    private static HBox actionRow(Button... buttons) {
        HBox row = new HBox(7, buttons);
        for (Button button : buttons) {
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        return row;
    }

    private static Button linkButton(String text, String url) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> Links.open(url));
        return button;
    }

    private static Label infoLine(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("recipe-detail");
        label.setWrapText(true);
        return label;
    }

    private static Label statusLabel() {
        Label status = new Label();
        status.getStyleClass().add("status-text");
        status.setWrapText(true);
        return status;
    }

    private static javafx.scene.control.ScrollPane scroll(VBox body) {
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? url : host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException error) {
            return url;
        }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private record PlanChoice(LocalDate date, String period) {
    }
}
