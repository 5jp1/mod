# Modrinth Downloader — Fabric Mod (MC 26.1)

Adds a **"⬇ Download Mods"** button to the Minecraft main menu. Browse and download anything from [Modrinth](https://modrinth.com) without leaving the game.

---

## ⚠️ Important: MC 26.1 is a new version with big changes

- **Java 25** is required (not 17 or 21)
- **Yarn mappings are gone** — this mod uses Mojang's official mappings
- **Gradle 9.4+** is required
- **Loom plugin ID changed** to `net.fabricmc.fabric-loom`

---

## Requirements

| Dependency     | Version        |
|----------------|----------------|
| Minecraft      | 26.1.2         |
| Fabric Loader  | ≥ 0.18.4       |
| Fabric API     | 0.116.0+26.1   |
| Java           | **25+**        |
| Gradle         | **9.4+**       |

---

## Features

- Browse all Modrinth content: **Mods, Modpacks, Resource Packs, Shaders, Plugins, Datapacks**
- Filter by **Minecraft version** (26.1, 1.21.x, down to 1.12.2)
- Sort by **Downloads, Follows, Newest, Recently Updated, Relevance**
- Live search with pagination
- One-click download — files go straight to the correct folder:
  - Mods → `mods/`
  - Resource Packs → `resourcepacks/`
  - Shaders → `shaderpacks/`
  - Datapacks → `saves/` *(move into a world's `datapacks/` folder manually)*
- Download status: Downloading / ✓ Installed / ✗ Failed

---

## Building from Source

**Step 1:** Make sure you have **JDK 25** installed. Check with:
```bash
java -version
```

**Step 2:** Build:
```bash
cd modrinth-downloader
./gradlew build       # Linux/macOS
gradlew.bat build     # Windows
```

The compiled jar appears at:
```
build/libs/modrinth-downloader-1.0.0.jar
```

**Step 3:** Copy the jar to your `mods/` folder alongside Fabric API 26.1.

> **Note:** First build downloads Minecraft + Fabric toolchain (~500MB). Subsequent builds are fast.

---

## Common Build Errors

| Error | Fix |
|-------|-----|
| `Unsupported class file major version` | You need JDK 25, not JDK 17/21 |
| `Could not resolve net.fabricmc.fabric-api` | Check internet; fabric_version must match `0.116.0+26.1` |
| `Plugin 'net.fabricmc.fabric-loom' not found` | Ensure `maven.fabricmc.net` is in pluginManagement repos |
| `Mixin target not found: TitleScreen` | Rebuild clean: `./gradlew clean build` |

---

## Project Structure

```
src/main/java/com/modrinthdownloader/
├── ModrinthDownloaderClient.java          Fabric entrypoint
├── client/
│   ├── gui/ModrinthBrowserScreen.java     Full in-game browser UI
│   └── util/ModrinthAPI.java              Modrinth API v2 wrapper
└── mixin/TitleScreenMixin.java            Injects button into main menu
```

---

## API

Uses the [Modrinth API v2](https://docs.modrinth.com/) with User-Agent:
```
MyMinecraftApp/1.0.0 (contact@example.com)
```
All HTTP calls are async (Java `HttpClient`) — never blocks the game thread.

---

## License

MIT
