# WheatLove

A fun **breeding mechanic** for Paper/Spigot Minecraft servers: **eat wheat** to enter "love mode" and breed — not just animals, but **mobs and even other players**. Player-bred offspring spawn as a baby named after the parent, wearing their head.

A combo of a small **plugin** (handles the breeding logic) and a **data pack** (makes wheat edible and detects when it is eaten).

![Minecraft](https://img.shields.io/badge/Minecraft-Paper%201.21%2B-brightgreen) ![License](https://img.shields.io/badge/license-MIT-blue)

## How it works

1. **Eat wheat** (the data pack makes plain wheat edible).
2. You enter **love mode** (hearts) — like an animal ready to breed.
3. Stand near another willing animal, mob or player and breeding triggers.

The baby is named **`<nick> Junior`** (visible name tag) and:

- **Player + Player** → a small humanoid **mini-player** baby wearing the parent's head (the head sits correctly on its head).
- **Player + Animal** (the animal must also be in love mode) → a **baby of that animal**, named after the player.

Breeding also **drops a little XP** (1–7) and puts the player (and the animal) on a **5-minute cooldown**, just like vanilla animal breeding.

## Components

| Part | Path | Role |
|---|---|---|
| Plugin | `src/main/java/com/wux/wheatlove/WheatLovePlugin.java` | Reads the eat trigger and runs the breeding |
| Data pack | `datapack/` | `make_wheat_edible` item modifier + `eat_wheat` advancement → scoreboard trigger |

## Install

1. Download `WheatLovePlugin.jar` from [Releases](../../releases) → server `plugins/` folder.
2. Download the data pack zip from [Releases](../../releases) → your world's `datapacks/` folder
   (or copy the `datapack/` directory there).
3. Restart the server (or `/reload` + `/datapack enable`).

Requires **Paper/Spigot 1.21+**.

## Build the plugin

```bash
mvn package      # -> target/WheatLovePlugin.jar
```

## License

[MIT](LICENSE) © Wux
