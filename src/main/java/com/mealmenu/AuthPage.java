package com.mealmenu;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public final class AuthPage extends ScrollPane {

    private final LocalAccountService accountService;
    private final Runnable onAuthenticated;
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final CheckBox keepSignedIn = new CheckBox("Keep me signed in on this device");
    private final CheckBox rememberPassword = new CheckBox("Remember password");
    private final RememberedPasswordStore passwordStore;
    private final ImageView avatarImage = new ImageView();
    private final StackPane avatar = new StackPane();
    private final SVGPath avatarPlaceholder = new SVGPath();
    private final PauseTransition restoreDelay = new PauseTransition(Duration.millis(200));
    private final Label status = new Label();
    private final Button loginTab = new Button("Log In");
    private final Button registerTab = new Button("Register");
    private boolean registering;
    private boolean busy;
    private long restoreVersion;
    private long passwordEditVersion;
    private VBox form;
    private final Button submitButton = new Button("Continue");

    public AuthPage(Stage owner, LocalAccountService accountService, Runnable onAuthenticated) {
        this(owner, accountService, onAuthenticated, new RememberedPasswordStore());
    }

    AuthPage(Stage owner, LocalAccountService accountService, Runnable onAuthenticated,
             RememberedPasswordStore passwordStore) {
        this.accountService = accountService;
        this.onAuthenticated = onAuthenticated;
        this.passwordStore = passwordStore;
        getStyleClass().add("page-scroll");
        setFitToWidth(true);
        setPannable(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        VBox page = new VBox(16);
        page.getStyleClass().add("auth-page");
        page.setPadding(new Insets(34, 22, 28, 22));
        page.setAlignment(Pos.CENTER);

        buildAvatar();
        Label title = new Label("dailyrecipes");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("A calmer way to plan what you eat.");
        subtitle.getStyleClass().add("page-subtitle");

        HBox tabs = new HBox(8, loginTab, registerTab);
        tabs.getStyleClass().add("auth-tabs");
        tabs.setAlignment(Pos.CENTER);
        loginTab.setMaxWidth(Double.MAX_VALUE);
        registerTab.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loginTab, Priority.ALWAYS);
        HBox.setHgrow(registerTab, Priority.ALWAYS);
        loginTab.getStyleClass().add("auth-mode-button");
        registerTab.getStyleClass().add("auth-mode-button");
        loginTab.setOnAction(event -> setRegistering(false));
        registerTab.setOnAction(event -> setRegistering(true));

        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        passwordField.setOnAction(event -> submit());
        keepSignedIn.getStyleClass().add("check-option");
        rememberPassword.getStyleClass().add("check-option");
        rememberPassword.setDisable(!passwordStore.isAvailable());
        if (!passwordStore.isAvailable()) {
            rememberPassword.setTooltip(new Tooltip("Password autofill is available on Windows."));
        }
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            passwordField.clear();
            rememberPassword.setSelected(false);
            restoreVersion++;
            restoreDelay.stop();
            updateAvatar();
            if (!registering && !busy) {
                restoreDelay.playFromStart();
            }
        });
        passwordField.textProperty().addListener((observable, oldValue, newValue) ->
                passwordEditVersion++);
        restoreDelay.setOnFinished(event -> restorePassword());
        rememberPassword.setOnAction(event -> {
            restoreVersion++;
            if (!rememberPassword.isSelected()) {
                String accountId = accountService.accountIdForUsername(usernameField.getText());
                try {
                    passwordStore.forget(accountId);
                } catch (IOException exception) {
                    status.setText("The saved password could not be removed. Please try again.");
                }
                passwordField.clear();
            }
        });
        status.getStyleClass().add("status-text");
        status.setWrapText(true);
        status.setMinHeight(34);

        submitButton.getStyleClass().add("primary-button");
        submitButton.setMaxWidth(Double.MAX_VALUE);
        submitButton.setOnAction(event -> submit());

        Label localNote = new Label(
                "Your account and meal plans are stored locally on this computer. "
                        + "Remember password saves an encrypted copy for your Windows account."
        );
        localNote.getStyleClass().add("muted-text");
        localNote.setWrapText(true);

        form = new VBox(12, tabs, usernameField, passwordField,
                rememberPassword, keepSignedIn, submitButton, status, localNote);
        form.getStyleClass().add("auth-card");
        page.getChildren().addAll(avatar, title, subtitle, form);
        setContent(page);
        setRegistering(false);
        usernameField.setText(accountService.lastUsername());
    }

    private void buildAvatar() {
        avatar.setId("login-avatar");
        avatar.setMinSize(84, 84);
        avatar.setPrefSize(84, 84);
        avatar.setMaxSize(84, 84);
        avatar.setAccessibleText("Account profile photo");
        avatarPlaceholder.setContent(
                "M 0 -22 A 12 12 0 1 1 0 2 A 12 12 0 1 1 0 -22 "
                        + "M -24 28 C -24 3 24 3 24 28 Z");
        avatarPlaceholder.setFill(Color.web("#8FB99A"));
        avatarPlaceholder.setMouseTransparent(true);
        avatarImage.setFitWidth(84);
        avatarImage.setFitHeight(84);
        avatarImage.setSmooth(true);
        avatarImage.setClip(new Circle(42, 42, 42));
        avatarImage.setVisible(false);
        avatarImage.setManaged(false);
        avatar.getChildren().addAll(new Circle(42, Color.web("#DDEEDF")),
                avatarPlaceholder, avatarImage);
    }

    private void updateAvatar() {
        avatarImage.setImage(null);
        avatarImage.setVisible(false);
        avatarImage.setManaged(false);
        avatarPlaceholder.setVisible(true);
        if (registering) {
            return;
        }
        String accountId = accountService.accountIdForUsername(usernameField.getText());
        if (accountId.isBlank()) {
            return;
        }
        Path directory = UserDataPaths.accountDirectory(accountId);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("avatar."))
                    .sorted().findFirst().ifPresent(path -> {
                        Image photo = new Image(path.toUri().toString(), 168, 168, true, true);
                        if (!photo.isError() && photo.getWidth() > 0 && photo.getHeight() > 0) {
                            double side = Math.min(photo.getWidth(), photo.getHeight());
                            avatarImage.setViewport(new Rectangle2D(
                                    (photo.getWidth() - side) / 2,
                                    (photo.getHeight() - side) / 2, side, side));
                            avatarImage.setImage(photo);
                            avatarImage.setManaged(true);
                            avatarImage.setVisible(true);
                            avatarPlaceholder.setVisible(false);
                        }
                    });
        } catch (IOException | IllegalArgumentException exception) {
            // The default avatar remains visible if the saved photo cannot be read.
        }
    }

    private void restorePassword() {
        String username = usernameField.getText();
        String accountId = accountService.accountIdForUsername(username);
        if (registering || busy || !passwordStore.isAvailable()
                || !passwordStore.hasSavedPassword(accountId) || !passwordField.getText().isEmpty()) {
            return;
        }
        long request = ++restoreVersion;
        long editVersion = passwordEditVersion;
        CompletableFuture.supplyAsync(() -> {
            try {
                char[] saved = passwordStore.load(accountId);
                if (accountService.passwordMatches(username, saved)) {
                    return saved;
                }
                Arrays.fill(saved, '\0');
            } catch (IOException exception) {
                // Login remains available when a saved password cannot be restored.
            }
            return new char[0];
        }).thenAccept(saved -> Platform.runLater(() -> {
            try {
                if (request == restoreVersion && editVersion == passwordEditVersion
                        && !registering && !busy && saved.length > 0) {
                    passwordField.setText(new String(saved));
                    rememberPassword.setSelected(true);
                }
            } finally {
                Arrays.fill(saved, '\0');
            }
        }));
    }

    private void setRegistering(boolean value) {
        restoreDelay.stop();
        restoreVersion++;
        boolean changed = registering != value;
        registering = value;
        avatar.setVisible(!value);
        avatar.setManaged(!value);
        loginTab.getStyleClass().remove("auth-mode-active");
        registerTab.getStyleClass().remove("auth-mode-active");
        (registering ? registerTab : loginTab).getStyleClass().add("auth-mode-active");
        passwordField.setPromptText(registering
                ? "Create a password (at least " + LocalAccountService.MIN_PASSWORD_LENGTH + " characters)"
                : "Password");
        status.setText(registering
                ? "Create a local account using a username and password."
                : "Welcome back. Log in to open your meal plans.");
        if (changed) {
            passwordField.clear();
            rememberPassword.setSelected(false);
            keepSignedIn.setSelected(false);
            usernameField.setText(value ? "" : accountService.lastUsername());
        }
        updateAvatar();
        if (!value) {
            restoreDelay.playFromStart();
        }
    }

    private void submit() {
        if (busy) {
            return;
        }
        restoreDelay.stop();
        restoreVersion++;
        busy = true;
        form.setDisable(true);
        String username = usernameField.getText();
        char[] password = passwordField.getText().toCharArray();
        boolean register = registering;
        boolean keepSession = keepSignedIn.isSelected();
        boolean remember = rememberPassword.isSelected();
        status.setText(register ? "Creating your account..." : "Signing in...");
        CompletableFuture.supplyAsync(() -> {
            try {
                String error = register
                        ? accountService.register(username, password, keepSession)
                        : accountService.login(username, password, keepSession);
                if (error != null) {
                    return new SignInResult(error, false);
                }
                try {
                    if (remember) {
                        passwordStore.remember(accountService.accountId(), password);
                    } else {
                        passwordStore.forget(accountService.accountId());
                    }
                } catch (IOException exception) {
                    return new SignInResult(
                            "Signed in, but the saved password could not be updated.", true);
                }
                return new SignInResult(null, true);
            } finally {
                Arrays.fill(password, '\0');
            }
        }).whenComplete((result, failure) -> Platform.runLater(() -> {
            busy = false;
            form.setDisable(false);
            if (failure != null || !result.authenticated()) {
                status.setText(failure == null ? result.message() : "Sign-in failed. Please try again.");
                return;
            }
            passwordField.clear();
            if (result.message() != null) {
                Alert notice = new Alert(Alert.AlertType.INFORMATION, result.message());
                notice.setTitle("dailyrecipes");
                notice.setHeaderText("Password preference could not be saved");
                notice.initOwner(getScene().getWindow());
                notice.showAndWait();
            }
            onAuthenticated.run();
        }));
    }

    private record SignInResult(String message, boolean authenticated) { }
}
