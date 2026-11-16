# Chat Trigger Client (Fabric 1.21.11)

A client-side Fabric mod with two independent features:

1. Send a configured public chat message repeatedly on a timer.
2. Watch incoming player chat for a configured text/number and privately reply to the player who sent it.

The mod only runs on server addresses listed in `allowedServers`. It does not need to be installed on the multiplayer server.

## Requirements

- Minecraft Java Edition 1.21.11
- Java 21
- Fabric Loader 0.18.1 or newer
- Fabric API 0.141.1+1.21.11

## Easiest build method (no local Java or Gradle installation)

This project includes a GitHub Actions workflow at `.github/workflows/build.yml`. Upload the project to a GitHub repository, open its **Actions** tab, select **Build Fabric mod**, and click **Run workflow**. When it finishes, open the successful run, scroll to **Artifacts**, and download `ChatTriggerClient-JAR`. Extract the downloaded artifact to get the `.jar`. GitHub builds it in the cloud, so you do not need to install Java or Gradle on your PC.

## Build

1. Install a Java 21 JDK and Gradle 8.14 or newer.
2. Extract this project and open a terminal in the `ChatTriggerClient` folder.
3. Run `gradle build` (or `./gradlew build` if you add/use the Gradle wrapper).
4. The remapped mod JAR should be written to `build/libs/chat-trigger-client-1.0.0.jar`.
5. Put the JAR in `.minecraft/mods` alongside Fabric API.
6. Launch Minecraft once. The mod creates `.minecraft/config/chattrigger.json` on first launch.
7. Edit the JSON while Minecraft is closed, then launch/reconnect. The mod reads the file on startup; it does not hot-reload edits.

## Full configuration example

```json
{
  "enabled": true,
  "allowedServers": [
    "play.example.net",
    "play.example.net:25565"
  ],

  "timedPublicMessageEnabled": true,
  "timedPublicMessage": "Hello everyone!",
  "timedPublicMessageIntervalSeconds": 60,
  "timedPublicMessageInitialDelaySeconds": 60,

  "triggerReplyEnabled": true,
  "triggerText": "4000",
  "triggerMatchMode": "contains",
  "triggerCaseSensitive": false,
  "privateMessage": "Thanks for sending the number!",
  "privateMessageCommand": "/msg {player} {message}",

  "playerCooldownSeconds": 300,
  "minimumSecondsBetweenAnyMessages": 2
}
```

## Configuration reference

### General

| Setting | Type | What it does |
|---|---|---|
| `enabled` | Boolean | Master switch. `false` disables both automation features. |
| `allowedServers` | Array of strings | Exact server addresses where the mod is allowed to run. Matching ignores letter case and surrounding spaces. Add the port if the address shown by Minecraft includes it. |

### Repeating public message

| Setting | Type | What it does |
|---|---|---|
| `timedPublicMessageEnabled` | Boolean | Turns the repeating public message feature on or off, independently of trigger replies. |
| `timedPublicMessage` | String | The exact text sent to public chat. An empty string disables sending. |
| `timedPublicMessageIntervalSeconds` | Integer | How many seconds between repeated messages. For once per minute, use `60`. Values below `1` are corrected to `1`. |
| `timedPublicMessageInitialDelaySeconds` | Integer | How long to wait after joining an allowed server before the first timed message. Use `0` to send as soon as the client can send it; use `60` to wait one minute. |

The timer runs only while connected to an allowed server. After the first message, it repeats at the configured interval. Messages are placed in a send queue and obey the global minimum send gap below.

### Number/text detection and private reply

| Setting | Type | What it does |
|---|---|---|
| `triggerReplyEnabled` | Boolean | Turns chat detection and private replies on or off. |
| `triggerText` | String | The number or text to look for. Examples: `4000`, `15000`, `1000000`, or `hello`. |
| `triggerMatchMode` | String | `contains` triggers when the text appears anywhere in the player's message. `exact` triggers only when the entire trimmed message equals `triggerText`. |
| `triggerCaseSensitive` | Boolean | If `false`, matching ignores upper/lowercase differences. If `true`, capitalization must match. This matters mainly for words, not numbers. |
| `privateMessage` | String | The private message text to send to the player who matched. May include `{player}` if you want their username in the text. |
| `privateMessageCommand` | String | The server command template used to send the private message. `{player}` is replaced with the sender's username and `{message}` with `privateMessage`. Example: `/msg {player} {message}`. Change it to the private-message command your server supports, such as `/tell {player} {message}` if appropriate. |

**Matching examples when `triggerText` is `4000`:**

- `triggerMatchMode: "contains"`: `4000`, `I have 4000 points`, and `score=4000!` all match. `14000` also matches because it contains the character sequence `4000`.
- `triggerMatchMode: "exact"`: only a message whose complete trimmed text is `4000` matches. `I have 4000 points` does not.

Use `exact` if you only want the standalone number and do not want larger numbers containing it to match.

### Anti-spam / rate limits

| Setting | Type | What it does |
|---|---|---|
| `playerCooldownSeconds` | Integer | After a player triggers a private reply, ignore further matching messages from that same player until this many seconds have passed. `300` means five minutes. Set to `0` to disable the per-player cooldown. |
| `minimumSecondsBetweenAnyMessages` | Integer | Minimum gap between any two automated outgoing messages, including public timed messages and private-reply commands. This helps avoid bursts if both features trigger close together. `2` means at least two seconds between sends. |

## Important behavior and limitations

- Server addresses are matched exactly after trimming and lowercasing. `example.net` and `example.net:25565` are treated as different entries.
- `contains` is a literal substring match, not a mathematical number parser or regular expression.
- The sender must be provided by the server's incoming player-chat event. System messages, unsigned formats without a sender profile, and messages hidden before the event cannot be attributed and will not trigger a private reply.
- The server must allow the configured private-message command. The mod cannot bypass permissions, chat restrictions, anti-spam protections, or server rules.
- Automated public messages may be considered spam by some servers. Only enable them where the server permits automated messages, and choose a reasonable interval.
- Config changes are read on the next Minecraft launch. Restart the client after editing the file.


## In-game toggle keybind

The mod registers **Toggle Chat Automation** under **Options → Controls → Key Binds → Chat Trigger Client**. Its default key is **P**. Select the binding and press another key to change it, just like any vanilla Minecraft keybind. Press the key in-game to enable or disable automation for the current game session. The keybind setting is stored by Minecraft in its normal options file; it is not configured in `chattrigger.json`.

The JSON `enabled` property remains the master configuration switch. If it is `false`, automation remains disabled even if the runtime keybind is toggled on. The keybind does not change or rewrite the JSON file. When toggled off, queued messages are cleared immediately. When toggled back on while connected to an allowed server, the timed public-message countdown starts again using `timedPublicMessageInitialDelaySeconds`.

### Config setting reference

- `enabled`: Master switch. Must be `true` for automation to run.
- `allowedServers`: Server addresses (hostnames, optionally with ports) where the mod is allowed to run. Matching ignores letter case and surrounding whitespace.
- `timedPublicMessageEnabled`: Enables the repeating public message feature.
- `timedPublicMessage`: Text sent to public chat.
- `timedPublicMessageIntervalSeconds`: Number of seconds between subsequent public messages; minimum is 1.
- `timedPublicMessageInitialDelaySeconds`: Seconds to wait after joining or re-enabling before the first timed message; minimum is 0.
- `triggerReplyEnabled`: Enables incoming-chat trigger detection.
- `triggerText`: Exact configured text/number to look for, e.g. `4000` or `15000`.
- `triggerMatchMode`: `contains` matches the trigger anywhere in the rendered chat line; `exact` requires the entire rendered line to equal the trigger.
- `triggerCaseSensitive`: If `false`, matching ignores letter case.
- `privateMessage`: Private reply text. `{player}` can be included as a placeholder.
- `privateMessageCommand`: Command template used to send the reply. Supports `{player}` and `{message}`, e.g. `/msg {player} {message}`.
- `playerCooldownSeconds`: Minimum time before that same username can trigger another private reply. Set to `0` to disable per-player cooldown.
- `minimumSecondsBetweenAnyMessages`: Minimum spacing between any two automated outgoing chat messages/commands. Set to `0` to disable this extra spacing.

The mod uses the chat text and sender information exposed by the client. Server chat formatting, proxy plugins, hidden/modified sender data, and server anti-spam rules can affect matching or whether outgoing commands are accepted. Use automation only where the server's rules permit it.
