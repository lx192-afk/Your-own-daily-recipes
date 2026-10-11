package com.mealmenu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Bottom tab bar in phone mode, left sidebar in desktop mode. */
public class NavigationBar extends StackPane {

    private final Consumer<String> pageHandler;
    private final Map<String, Button> buttons = new LinkedHashMap<>();

    public NavigationBar(Consumer<String> pageHandler) {
        this(pageHandler, false);
    }

    public NavigationBar(Consumer<String> pageHandler, boolean sidebar) {
        this.pageHandler = pageHandler;
        Pane box;
        if (sidebar) {
            getStyleClass().add("sidebar");
            VBox column = new VBox(6);
            column.setPadding(new Insets(22, 14, 18, 14));
            column.setPrefWidth(190);
            Label brand = new Label("dailyrecipes");
            brand.getStyleClass().add("sidebar-title");
            Label tagline = new Label("Rutgers Eats");
            tagline.getStyleClass().add("muted-text");
            VBox header = new VBox(2, brand, tagline);
            header.setPadding(new Insets(0, 6, 16, 6));
            column.getChildren().add(header);
            box = column;
        } else {
            getStyleClass().add("navigation-bar");
            HBox row = new HBox(6);
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(10, 12, 12, 12));
            box = row;
        }
        for (String page : new String[]{"Home", "Eat", "Kitchen", "Profile"}) {
            Button button = new Button(page);
            button.getStyleClass().add(sidebar ? "sidebar-button" : "nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
            button.setOnAction(event -> pageHandler.accept(page));
            buttons.put(page, button);
            box.getChildren().add(button);
        }
        getChildren().add(box);
        setActivePage("Home");
    }

    public void setActivePage(String pageName) {
        buttons.forEach((name, button) -> {
            button.getStyleClass().remove("nav-button-active");
            if (name.equals(pageName)) {
                button.getStyleClass().add("nav-button-active");
            }
        });
    }
}

