package com.example.chattrigger;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.PlayerChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

public final class ChatTriggerClient implements ClientModInitializer {
    public static final String MOD_ID = "chattrigger";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Queue<QueuedMessage> OUTGOING = new ArrayDeque<>();
    private static final Map<String, Long> PLAYER_COOLDOWNS = new HashMap<>();

    private static Config config;
    private static boolean automationEnabled;

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "general")
    );
    private static final KeyMapping TOGGLE_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.chattrigger.toggle",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_P,
            KEY_CATEGORY
    ));
    private static String activeServer = "";
    private static long nextPublicMessageAt = 0L;
    private static long nextOutgoingAllowedAt = 0L;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        automationEnabled = config.enabled;
        ClientPlayConnectionEvents.Join.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.Disconnect.register((handler, client) -> resetSession());
        ClientReceiveMessageEvents.CHAT.register(this::onChatMessage);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("Chat Trigger Client loaded. Config: {}", Config.getConfigPath());
    }

    private static void onJoin(Minecraft client) {
        resetSession();
        if (!automationEnabled || !config.enabled || client.getCurrentServer() == null) return;

        activeServer = normalize(client.getCurrentServer().ip);
        if (!isAllowed(activeServer)) {
            activeServer = "";
            return;
        }

        if (config.timedPublicMessageEnabled && !config.timedPublicMessage.isBlank()) {
            nextPublicMessageAt = System.currentTimeMillis()
                    + config.timedPublicMessageInitialDelaySeconds * 1000L;
        }
        LOGGER.info("Chat automation enabled for configured server {}", activeServer);
    }

    private void onChatMessage(Component message, PlayerChatMessage signedMessage,
                               GameProfile sender, net.minecraft.network.chat.ChatType.Bound params,
                               Instant receptionTimestamp) {
        Minecraft client = Minecraft.getInstance();
        if (!isActive(client) || !config.triggerReplyEnabled || sender == null || client.player == null) return;
        if (config.triggerText.isBlank() || config.privateMessage.isBlank()) return;

        String username = sender.getName();
        if (username == null || username.isBlank()
                || username.equalsIgnoreCase(client.player.getGameProfile().getName())) return;

        String body = message == null ? "" : message.getString().trim();
        if (!matchesTrigger(body)) return;

        long now = System.currentTimeMillis();
        String playerKey = username.toLowerCase(Locale.ROOT);
        long cooldownUntil = PLAYER_COOLDOWNS.getOrDefault(playerKey, 0L);
        if (now < cooldownUntil) return;

        String reply = config.privateMessage.replace("{player}", username);
        String command = config.privateMessageCommand
                .replace("{player}", username)
                .replace("{message}", reply)
                .trim();
        if (command.isBlank()) return;

        enqueueCommand(command);
        PLAYER_COOLDOWNS.put(playerKey, now + config.playerCooldownSeconds * 1000L);
    }

    private static boolean matchesTrigger(String chatBody) {
        String target = config.triggerText.trim();
        String body = chatBody;
        if (!config.triggerCaseSensitive) {
            target = target.toLowerCase(Locale.ROOT);
            body = body.toLowerCase(Locale.ROOT);
        }
        if (config.triggerMatchMode.equals("exact")) return body.equals(target);
        return body.contains(target);
    }

    private void onClientTick(Minecraft client) {
        while (TOGGLE_KEY.consumeClick()) {
            automationEnabled = !automationEnabled;
            if (automationEnabled) {
                activateForCurrentServer(client);
                if (client.player != null) {
                    if (isActive(client)) {
                        client.player.displayClientMessage(Component.literal("[Chat Trigger] Enabled"), true);
                    } else {
                        client.player.displayClientMessage(Component.literal("[Chat Trigger] Enabled (waiting for an allowed server)"), true);
                    }
                }
            } else {
                activeServer = "";
                OUTGOING.clear();
                nextPublicMessageAt = 0L;
                if (client.player != null) {
                    client.player.displayClientMessage(Component.literal("[Chat Trigger] Disabled"), true);
                }
            }
        }

        if (!isActive(client)) return;

        long now = System.currentTimeMillis();
        if (config.timedPublicMessageEnabled && !config.timedPublicMessage.isBlank()
                && nextPublicMessageAt > 0L && now >= nextPublicMessageAt) {
            enqueueChat(config.timedPublicMessage);
            // Schedule from the intended interval, not from how long the send queue took.
            nextPublicMessageAt = now + config.timedPublicMessageIntervalSeconds * 1000L;
        }

        if (OUTGOING.isEmpty() || client.player == null || now < nextOutgoingAllowedAt) return;
        QueuedMessage next = OUTGOING.poll();
        if (next == null) return;

        if (next.command) {
            String command = next.content.startsWith("/") ? next.content.substring(1) : next.content;
            client.player.connection.sendCommand(command);
        } else {
            client.player.connection.sendChat(next.content);
        }
        nextOutgoingAllowedAt = now + config.minimumSecondsBetweenAnyMessages * 1000L;
    }

    private static void activateForCurrentServer(Minecraft client) {
        if (!config.enabled || client.getCurrentServer() == null) return;
        String current = normalize(client.getCurrentServer().ip);
        if (!isAllowed(current)) return;
        activeServer = current;
        if (config.timedPublicMessageEnabled && !config.timedPublicMessage.isBlank()) {
            nextPublicMessageAt = System.currentTimeMillis()
                    + config.timedPublicMessageInitialDelaySeconds * 1000L;
        }
    }

    private static boolean isActive(Minecraft client) {
        if (!automationEnabled || !config.enabled || client.player == null || client.getCurrentServer() == null
                || activeServer.isBlank()) return false;
        String current = normalize(client.getCurrentServer().ip);
        return current.equals(activeServer) && isAllowed(current);
    }

    private static boolean isAllowed(String server) {
        return config.allowedServers.stream().map(ChatTriggerClient::normalize).anyMatch(server::equals);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void enqueueChat(String message) {
        OUTGOING.add(new QueuedMessage(message, false));
    }

    private static void enqueueCommand(String command) {
        OUTGOING.add(new QueuedMessage(command, true));
    }

    private static void resetSession() {
        activeServer = "";
        OUTGOING.clear();
        PLAYER_COOLDOWNS.clear();
        nextPublicMessageAt = 0L;
        nextOutgoingAllowedAt = 0L;
    }

    private record QueuedMessage(String content, boolean command) { }
}
