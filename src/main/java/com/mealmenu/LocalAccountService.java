package com.mealmenu;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class LocalAccountService {

    static final int MIN_PASSWORD_LENGTH = 8;
    private static final int ITERATIONS = 160_000;
    private static final int KEY_LENGTH = 256;
    private static final Set<String> ALLOWED_PREFERENCES = Set.of(
            "Vegetarian", "Gluten-Free", "Dairy-Free", "Low-Carb", "High-Protein"
    );

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Account> accounts = new HashMap<>();
    private final Path storageFile = UserDataPaths.applicationDirectory()
            .resolve("accounts.properties");
    private Account currentAccount;
    private String rememberedUsername;
    private String lastUsername;
    /** False when accounts.properties exists but couldn't be read; saving would erase every account. */
    private boolean writable = true;
    /** Records that failed to parse are written back untouched rather than dropped. */
    private final Map<String, String> unreadableRecords = new HashMap<>();

    public LocalAccountService() {
        loadAccounts();
        if (rememberedUsername != null) {
            currentAccount = accounts.get(rememberedUsername);
            if (currentAccount == null) {
                rememberedUsername = null;
            }
        }
    }

    public String register(String username, char[] password, boolean rememberMe) {
        String cleanName = username == null ? "" : username.trim();
        String key = normalize(cleanName);
        String validation = validateUsername(cleanName);
        if (validation != null) {
            return validation;
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return "Use a password with at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (accounts.containsKey(key)) {
            return "That username is already registered on this computer.";
        }

        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        Account account = new Account(
                UUID.randomUUID().toString(),
                cleanName,
                salt,
                hashPassword(password, salt),
                Set.of()
        );
        accounts.put(key, account);
        currentAccount = account;
        rememberedUsername = rememberMe ? key : null;
        String previousLastUsername = lastUsername;
        lastUsername = key;

        if (!saveAccounts()) {
            accounts.remove(key);
            currentAccount = null;
            rememberedUsername = null;
            lastUsername = previousLastUsername;
            return "The account could not be saved to this computer.";
        }
        return null;
    }

    public String login(String username, char[] password, boolean rememberMe) {
        String key = normalize(username);
        Account account = accounts.get(key);
        if (account == null || !MessageDigest.isEqual(
                hashPassword(password, account.salt()), account.passwordHash()
        )) {
            return "The username or password is incorrect.";
        }

        currentAccount = account;
        rememberedUsername = rememberMe ? key : null;
        String previousLastUsername = lastUsername;
        lastUsername = key;
        if (!saveAccounts()) {
            currentAccount = null;
            rememberedUsername = null;
            lastUsername = previousLastUsername;
            return "The local account file could not be updated.";
        }
        return null;
    }

    /** Returns false if "keep me signed in" could not be cleared on disk. */
    public boolean logout() {
        currentAccount = null;
        rememberedUsername = null;
        return saveAccounts();
    }

    public boolean isLoggedIn() {
        return currentAccount != null;
    }

    public String accountId() {
        return currentAccount == null ? "" : currentAccount.id();
    }

    public String username() {
        return currentAccount == null ? "" : currentAccount.username();
    }

    public String lastUsername() {
        Account account = accounts.get(lastUsername);
        return account == null ? "" : account.username();
    }

    public String accountIdForUsername(String username) {
        Account account = accounts.get(normalize(username));
        return account == null ? "" : account.id();
    }

    public boolean passwordMatches(String username, char[] password) {
        Account account = accounts.get(normalize(username));
        return account != null && MessageDigest.isEqual(
                hashPassword(password, account.salt()), account.passwordHash());
    }

    public Set<String> preferences() {
        return currentAccount == null ? Set.of() : currentAccount.preferences();
    }

    public boolean updatePreferences(Set<String> preferences) {
        if (currentAccount == null) {
            return false;
        }
        Set<String> filtered = new HashSet<>(preferences);
        filtered.retainAll(ALLOWED_PREFERENCES);
        Account previous = currentAccount;
        currentAccount = new Account(
                previous.id(), previous.username(), previous.salt(),
                previous.passwordHash(), Set.copyOf(filtered)
        );
        accounts.put(normalize(previous.username()), currentAccount);
        if (saveAccounts()) {
            return true;
        }
        currentAccount = previous;
        accounts.put(normalize(previous.username()), previous);
        return false;
    }

    public String updateInformation(String newUsername, char[] currentPassword, char[] newPassword) {
        if (currentAccount == null) {
            return "Sign in before changing account information.";
        }
        // Remember-me skips the login screen, so require the current password for account changes.
        if (!MessageDigest.isEqual(hashPassword(currentPassword, currentAccount.salt()),
                currentAccount.passwordHash())) {
            return "Your current password is incorrect.";
        }
        String cleanName = newUsername == null ? "" : newUsername.trim();
        String validation = validateUsername(cleanName);
        if (validation != null) {
            return validation;
        }
        String oldKey = normalize(currentAccount.username());
        String newKey = normalize(cleanName);
        if (!oldKey.equals(newKey) && accounts.containsKey(newKey)) {
            return "That username is already registered on this computer.";
        }
        if (newPassword.length > 0 && newPassword.length < MIN_PASSWORD_LENGTH) {
            return "A new password must have at least " + MIN_PASSWORD_LENGTH + " characters.";
        }

        Account oldAccount = currentAccount;
        String previousLastUsername = lastUsername;
        byte[] salt = oldAccount.salt();
        byte[] passwordHash = oldAccount.passwordHash();
        if (newPassword.length > 0) {
            salt = new byte[16];
            secureRandom.nextBytes(salt);
            passwordHash = hashPassword(newPassword, salt);
        }
        currentAccount = new Account(
                oldAccount.id(), cleanName, salt, passwordHash, oldAccount.preferences()
        );
        accounts.remove(oldKey);
        accounts.put(newKey, currentAccount);
        if (rememberedUsername != null && rememberedUsername.equals(oldKey)) {
            rememberedUsername = newKey;
        }
        if (oldKey.equals(lastUsername)) {
            lastUsername = newKey;
        }
        if (saveAccounts()) {
            if (newPassword.length > 0) {
                try {
                    new RememberedPasswordStore().forget(currentAccount.id());
                } catch (IOException exception) {
                    // Autofill also validates the hash, so stale copies cannot be reused.
                    System.err.println("Could not remove the old saved password.");
                }
            }
            return null;
        }

        accounts.remove(newKey);
        accounts.put(oldKey, oldAccount);
        currentAccount = oldAccount;
        lastUsername = previousLastUsername;
        if (rememberedUsername != null && rememberedUsername.equals(newKey)) {
            rememberedUsername = oldKey;
        }
        return "Account information could not be saved.";
    }

    private void loadAccounts() {
        if (!Files.exists(storageFile)) {
            return;
        }
        Properties saved = new Properties();
        try (InputStream input = Files.newInputStream(storageFile)) {
            saved.load(input);
        } catch (IOException | IllegalArgumentException exception) {
            writable = false;
            System.err.println("Could not load local accounts; not saving over them: "
                    + exception.getMessage());
            return;
        }

        for (String property : saved.stringPropertyNames()) {
            if (!property.startsWith("account.")) {
                continue;
            }
            try {
                String key = decode(property.substring("account.".length()));
                String[] values = saved.getProperty(property).split("\\.", -1);
                if (values.length != 5) {
                    unreadableRecords.put(property, saved.getProperty(property));
                    continue;
                }
                Account account = new Account(
                        values[0],
                        decode(values[1]),
                        decodeBytes(values[2]),
                        decodeBytes(values[3]),
                        decodePreferences(values[4])
                );
                accounts.put(key, account);
            } catch (IllegalArgumentException exception) {
                unreadableRecords.put(property, saved.getProperty(property));
                System.err.println("Kept a damaged local account record as-is.");
            }
        }

        String savedUsername = saved.getProperty("remembered-user", "");
        if (!savedUsername.isBlank()) {
            try {
                rememberedUsername = decode(savedUsername);
            } catch (IllegalArgumentException exception) {
                rememberedUsername = null;
            }
        }
        try {
            lastUsername = decode(saved.getProperty("last-user",
                    rememberedUsername == null ? "" : encode(rememberedUsername)));
        } catch (IllegalArgumentException exception) {
            lastUsername = null;
        }
    }

    private boolean saveAccounts() {
        if (!writable) {
            return false;
        }
        Properties saved = new Properties();
        unreadableRecords.forEach(saved::setProperty);
        accounts.forEach((key, account) -> saved.setProperty(
                "account." + encode(key),
                account.id() + "." + encode(account.username()) + "."
                        + encode(account.salt()) + "."
                        + encode(account.passwordHash()) + "."
                        + encode(String.join("\n", account.preferences()))
        ));
        saved.setProperty("remembered-user", rememberedUsername == null
                ? ""
                : encode(rememberedUsername));
        saved.setProperty("last-user", lastUsername == null ? "" : encode(lastUsername));

        try {
            SafeFiles.storeProperties(storageFile, saved, "Daily Menu local accounts");
            return true;
        } catch (IOException exception) {
            System.err.println("Could not save local accounts: " + exception.getMessage());
            return false;
        }
    }

    private String validateUsername(String username) {
        if (username.isBlank()) {
            return "Enter a username.";
        }
        if (username.length() > 24) {
            return "Usernames can be up to 24 characters long.";
        }
        if (!username.matches("[A-Za-z0-9._-]+")) {
            return "Use letters, numbers, periods, underscores, or hyphens in your username.";
        }
        return null;
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] hashPassword(char[] password, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(keySpec)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable.", exception);
        } finally {
            keySpec.clearPassword();
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String decode(String value) {
        return new String(decodeBytes(value), StandardCharsets.UTF_8);
    }

    private byte[] decodeBytes(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private Set<String> decodePreferences(String value) {
        String decoded = decode(value);
        if (decoded.isEmpty()) {
            return Set.of();
        }
        Set<String> preferences = new HashSet<>(Set.of(decoded.split("\\n")));
        preferences.retainAll(ALLOWED_PREFERENCES);
        return Set.copyOf(preferences);
    }

    private record Account(
            String id,
            String username,
            byte[] salt,
            byte[] passwordHash,
            Set<String> preferences
    ) {
        private Account {
            preferences = Set.copyOf(preferences);
        }
    }
}

