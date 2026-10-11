package com.mealmenu;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProfilePage extends ScrollPane {

    private final Stage owner;
    private final LocalAccountService accountService;
    private final Runnable onLogout;
    private final Label usernameLabel = new Label();
    private final Label photoStatus = new Label("Tap your photo to choose an image.");
    private final ImageView avatarImage = new ImageView();
    private final Label avatarInitials = new Label("DR");
    private final VBox settingsPanel = new VBox(10);
    private final VBox informationPanel = new VBox(10);
    private final CheckBox vegetarian = new CheckBox("Vegetarian");
    private final CheckBox glutenFree = new CheckBox("Gluten-Free");
    private final CheckBox dairyFree = new CheckBox("Dairy-Free");
    private final CheckBox lowCarb = new CheckBox("Low-Carb");
    private final CheckBox highProtein = new CheckBox("High-Protein");
    private final TextField usernameInput = new TextField();
    private final PasswordField currentPassword = new PasswordField();
    private final PasswordField newPassword = new PasswordField();
    private final PasswordField confirmPassword = new PasswordField();
    private final Label settingsStatus = new Label();
    private final Label informationStatus = new Label();

    public ProfilePage(Stage owner, LocalAccountService accountService, Runnable onLogout) {
        this(owner, accountService, onLogout, WindowMode.PHONE, mode -> { });
    }

    public ProfilePage(Stage owner, LocalAccountService accountService, Runnable onLogout,
                       WindowMode windowMode, java.util.function.Consumer<WindowMode> onWindowMode) {
        this.owner = owner;
        this.accountService = accountService;
        this.onLogout = onLogout;
        getStyleClass().add("page-scroll");
        setFitToWidth(true);
        setPannable(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        VBox content = new VBox(15);
        content.getStyleClass().add("page-content");
        content.setPadding(new Insets(20, 18, 24, 18));
        Label title = new Label("Profile");
        title.getStyleClass().add("page-title");

        Circle avatarCircle = new Circle(42, Color.web("#DDEEDF"));
        avatarImage.setFitWidth(84);
        avatarImage.setFitHeight(84);
        avatarImage.setPreserveRatio(true);
        avatarImage.setSmooth(true);
        avatarImage.setClip(new Circle(42, 42, 42));
        avatarImage.setVisible(false);
        avatarInitials.getStyleClass().add("avatar-initials");
        StackPane avatar = new StackPane(avatarCircle, avatarImage, avatarInitials);
        avatar.setMinSize(84, 84);
        avatar.setPrefSize(84, 84);
        avatar.setMaxSize(84, 84);
        avatar.getStyleClass().add("avatar-button");
        avatar.setOnMouseClicked(event -> chooseProfilePhoto());

        usernameLabel.getStyleClass().add("profile-name");
        usernameLabel.setText(accountService.username());
        photoStatus.getStyleClass().add("muted-text");
        HBox profileHeader = new HBox(14, avatar, usernameLabel);
        profileHeader.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(usernameLabel, Priority.ALWAYS);
        VBox profileCard = card("Your Profile", profileHeader, photoStatus);

        Button settingsButton = new Button("Settings  ›");
        settingsButton.getStyleClass().add("profile-action-button");
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        settingsButton.setOnAction(event -> togglePanel(settingsPanel));
        Button logoutButton = new Button("Log Out  ›");
        logoutButton.getStyleClass().add("profile-action-button");
        logoutButton.setMaxWidth(Double.MAX_VALUE);
        logoutButton.setOnAction(event -> onLogout.run());
        Button informationButton = new Button("Information  ›");
        informationButton.getStyleClass().add("profile-action-button");
        informationButton.setMaxWidth(Double.MAX_VALUE);
        informationButton.setOnAction(event -> togglePanel(informationPanel));
        VBox actions = new VBox(8, settingsButton, logoutButton, informationButton);
        actions.getStyleClass().add("profile-actions");

        buildSettingsPanel();
        buildInformationPanel();
        loadPreferences();
        loadAvatar();
        content.getChildren().addAll(title, profileCard, actions,
                settingsPanel, informationPanel, windowCard(windowMode, onWindowMode));
        setContent(content);
    }

    private VBox windowCard(WindowMode current, java.util.function.Consumer<WindowMode> onChange) {
        javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        javafx.scene.control.ToggleButton desktop = new javafx.scene.control.ToggleButton("Desktop");
        javafx.scene.control.ToggleButton phone = new javafx.scene.control.ToggleButton("Phone");
        for (javafx.scene.control.ToggleButton button : new javafx.scene.control.ToggleButton[]{desktop, phone}) {
            button.setToggleGroup(group);
            button.getStyleClass().add("mode-button");
            button.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        (current == WindowMode.DESKTOP ? desktop : phone).setSelected(true);
        group.selectedToggleProperty().addListener((observable, old, selected) -> {
            if (selected == null) {
                group.selectToggle(old);
                return;
            }
            onChange.accept(selected == desktop ? WindowMode.DESKTOP : WindowMode.PHONE);
        });
        Label hint = new Label("Desktop is a resizable window with a sidebar; Phone is the compact phone-sized view.");
        hint.getStyleClass().add("muted-text");
        hint.setWrapText(true);
        return card("Window Style", new HBox(6, desktop, phone), hint);
    }

    private void buildSettingsPanel() {
        Label heading = new Label("Food Preferences");
        heading.getStyleClass().add("card-heading");
        Label hint = new Label("Kitchen uses one saved preference as its default.");
        hint.getStyleClass().add("muted-text");
        hint.setWrapText(true);
        Button save = new Button("Save Settings");
        save.getStyleClass().add("primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> savePreferences());
        settingsStatus.getStyleClass().add("status-text");
        settingsStatus.setWrapText(true);
        settingsPanel.getStyleClass().add("content-card");
        settingsPanel.getChildren().setAll(heading, hint, vegetarian, glutenFree,
                dairyFree, lowCarb, highProtein, save, settingsStatus);
        settingsPanel.setVisible(false);
        settingsPanel.setManaged(false);
    }

    private void buildInformationPanel() {
        Label heading = new Label("Account Information");
        heading.getStyleClass().add("card-heading");
        usernameInput.setPromptText("Username");
        usernameInput.setText(accountService.username());
        currentPassword.setPromptText("Current password (required to save changes)");
        newPassword.setPromptText("New password (leave blank to keep current)");
        confirmPassword.setPromptText("Confirm new password");
        Label hint = new Label("Usernames use letters, numbers, periods, underscores, or hyphens.");
        hint.getStyleClass().add("muted-text");
        hint.setWrapText(true);
        Button save = new Button("Update Information");
        save.getStyleClass().add("primary-button");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(event -> updateInformation());
        informationStatus.getStyleClass().add("status-text");
        informationStatus.setWrapText(true);
        informationPanel.getStyleClass().add("content-card");
        informationPanel.getChildren().setAll(heading, usernameInput, currentPassword, newPassword,
                confirmPassword, hint, save, informationStatus);
        informationPanel.setVisible(false);
        informationPanel.setManaged(false);
    }

    private void togglePanel(VBox selected) {
        boolean shouldShow = !selected.isVisible();
        settingsPanel.setVisible(selected == settingsPanel && shouldShow);
        settingsPanel.setManaged(settingsPanel.isVisible());
        informationPanel.setVisible(selected == informationPanel && shouldShow);
        informationPanel.setManaged(informationPanel.isVisible());
    }

    private void loadPreferences() {
        Set<String> preferences = accountService.preferences();
        vegetarian.setSelected(preferences.contains("Vegetarian"));
        glutenFree.setSelected(preferences.contains("Gluten-Free"));
        dairyFree.setSelected(preferences.contains("Dairy-Free"));
        lowCarb.setSelected(preferences.contains("Low-Carb"));
        highProtein.setSelected(preferences.contains("High-Protein"));
    }

    private void savePreferences() {
        Set<String> preferences = new LinkedHashSet<>();
        if (vegetarian.isSelected()) preferences.add("Vegetarian");
        if (glutenFree.isSelected()) preferences.add("Gluten-Free");
        if (dairyFree.isSelected()) preferences.add("Dairy-Free");
        if (lowCarb.isSelected()) preferences.add("Low-Carb");
        if (highProtein.isSelected()) preferences.add("High-Protein");
        settingsStatus.setText(accountService.updatePreferences(preferences)
                ? "Preferences saved on this device."
                : "Preferences could not be saved.");
    }

    private void updateInformation() {
        if (!newPassword.getText().equals(confirmPassword.getText())) {
            informationStatus.setText("The new passwords do not match.");
            return;
        }
        char[] password = newPassword.getText().toCharArray();
        char[] current = currentPassword.getText().toCharArray();
        String error;
        try {
            error = accountService.updateInformation(usernameInput.getText(), current, password);
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(current, '\0');
        }
        if (error != null) {
            informationStatus.setText(error);
            return;
        }
        usernameLabel.setText(accountService.username());
        usernameInput.setText(accountService.username());
        currentPassword.clear();
        newPassword.clear();
        confirmPassword.clear();
        informationStatus.setText("Account information updated.");
    }

    private void chooseProfilePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a Profile Photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"
        ));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) {
            return;
        }
        String extension = fileExtension(selected.getName());
        if (!Set.of("png", "jpg", "jpeg", "gif").contains(extension)) {
            photoStatus.setText("Choose a PNG, JPG, JPEG, or GIF image.");
            return;
        }
        // Load synchronously so a renamed non-image is rejected before anything is replaced.
        if (new Image(selected.toURI().toString(), 96, 96, true, true, false).isError()) {
            photoStatus.setText("That file isn't a readable image.");
            return;
        }
        Path directory = UserDataPaths.accountDirectory(accountService.accountId());
        try {
            Files.createDirectories(directory);
            Path staged = directory.resolve("avatar-new.tmp");
            Files.copy(selected.toPath(), staged, StandardCopyOption.REPLACE_EXISTING);
            Path destination = directory.resolve("avatar." + extension);
            try (var files = Files.list(directory)) {
                for (Path old : files.filter(path -> path.getFileName().toString().startsWith("avatar.")
                        && !path.equals(destination)).toList()) {
                    Files.deleteIfExists(old);
                }
            }
            Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            displayAvatar(destination);
            photoStatus.setText("Profile photo saved for this account.");
        } catch (IOException exception) {
            photoStatus.setText("The profile photo could not be saved.");
        }
    }

    private void loadAvatar() {
        Path directory = UserDataPaths.accountDirectory(accountService.accountId());
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().startsWith("avatar."))
                    .findFirst().ifPresent(this::displayAvatar);
        } catch (IOException exception) {
            photoStatus.setText("The saved profile photo could not be loaded.");
        }
    }

    private void displayAvatar(Path path) {
        Image image = new Image(path.toUri().toString(), 96, 96, true, true, false);
        if (image.isError()) {
            photoStatus.setText("The selected image could not be opened.");
            return;
        }
        avatarImage.setImage(image);
        avatarImage.setVisible(true);
        avatarInitials.setVisible(false);
    }

    private String fileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private VBox card(String title, javafx.scene.Node... children) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-heading");
        VBox card = new VBox(10, heading);
        card.getStyleClass().add("content-card");
        card.getChildren().addAll(children);
        return card;
    }
}
