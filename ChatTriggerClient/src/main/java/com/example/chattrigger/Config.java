package com.example.chattrigger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("chattrigger.json");

    // Master switch and server allowlist
    boolean enabled = true;
    List<String> allowedServers = new ArrayList<>(List.of("play.example.net"));

    // Repeating public chat message
    boolean timedPublicMessageEnabled = true;
    String timedPublicMessage = "Hello everyone!";
    int timedPublicMessageIntervalSeconds = 60;
    int timedPublicMessageInitialDelaySeconds = 60;

    // Incoming chat trigger and private reply
    boolean triggerReplyEnabled = true;
    String triggerText = "4000";
    // Supported values: "exact" (whole message) or "contains" (text appears anywhere in message)
    String triggerMatchMode = "contains";
    boolean triggerCaseSensitive = false;
    String privateMessage = "Thanks for sending the number!";
    String privateMessageCommand = "/msg {player} {message}";

    // Rate limits
    int playerCooldownSeconds = 300;
    int minimumSecondsBetweenAnyMessages = 2;

    static Config load() {
        try {
            if (Files.notExists(PATH)) {
                Config defaults = new Config();
                save(defaults);
                return defaults;
            }
            String json = Files.readString(PATH, StandardCharsets.UTF_8);
            Config loaded = GSON.fromJson(json, Config.class);
            if (loaded == null) loaded = new Config();
            loaded.sanitize();
            return loaded;
        } catch (IOException | JsonParseException exception) {
            ChatTriggerClient.LOGGER.warn("Could not read config; using defaults", exception);
            return new Config();
        }
    }

    private void sanitize() {
        if (allowedServers == null) allowedServers = new ArrayList<>();
        if (timedPublicMessage == null) timedPublicMessage = "";
        if (triggerText == null) triggerText = "";
        if (triggerMatchMode == null) triggerMatchMode = "contains";
        triggerMatchMode = triggerMatchMode.equalsIgnoreCase("exact") ? "exact" : "contains";
        if (privateMessage == null) privateMessage = "";
        if (privateMessageCommand == null) privateMessageCommand = "/msg {player} {message}";
        timedPublicMessageIntervalSeconds = Math.max(1, timedPublicMessageIntervalSeconds);
        timedPublicMessageInitialDelaySeconds = Math.max(0, timedPublicMessageInitialDelaySeconds);
        playerCooldownSeconds = Math.max(0, playerCooldownSeconds);
        minimumSecondsBetweenAnyMessages = Math.max(0, minimumSecondsBetweenAnyMessages);
    }

    private static void save(Config config) throws IOException {
        Files.createDirectories(PATH.getParent());
        Files.writeString(PATH, GSON.toJson(config), StandardCharsets.UTF_8);
    }

    static Path getConfigPath() { return PATH; }
}
