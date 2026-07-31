# ChloeManager — Minecraft Plugin (MVC Architecture)

A Spigot/Bukkit plugin scaffold using a clean **Model-View-Controller** pattern.

---

## Project Structure

```
src/main/java/com/chloe/chloemanager/
│
├── ChloeManagerPlugin.java          ← Plugin entry point (bootstrapper)
│
├── model/
│   └── PlayerDataModel.java         ← DATA layer: stores & manages state
│
├── view/
│   └── PlayerView.java              ← DISPLAY layer: all chat/title output
│
└── controller/
    └── HelloController.java         ← LOGIC layer: handles commands, wires M+V
```

---

## MVC Roles

| Layer | Responsibility | Knows about |
|-------|---------------|-------------|
| **Model** | Stores and queries data (in-memory, DB, files) | Pure Java only |
| **View** | Formats and sends messages to players/console | Bukkit `CommandSender` / `Player` |
| **Controller** | Validates input, calls Model, calls View | Both Model and View |

The main plugin class only **bootstraps** the three layers and wires them together. It does not contain any logic.

---

## Adding a New Feature (workflow)

1. **Model** — Add a new field or method to an existing model, or create a new `*Model.java` in `model/`.
2. **View** — Add output methods to an existing view, or create a new `*View.java` in `view/`.
3. **Controller** — Create a new `*Controller.java` in `controller/` that implements `CommandExecutor` (and optionally `TabCompleter`).
4. **Register** — In `ChloeManagerPlugin.java`, instantiate the controller and call `getCommand("yourcommand").setExecutor(controller)`.
5. **Declare** — Add the command to `plugin.yml`.

---

## Building

```bash
mvn clean package
```

The shaded jar will be at `target/chloe-manager-1.0.0.jar`. Drop it into your server's `plugins/` folder.

---

## Requirements

- Java 17+
- Maven 3.6+
- Spigot / Paper 1.20.1
