# Infuse — Paper/Purpur plugin conversion

A Java 21 conversion of the supplied InfuseS3 Skript pack for Paper/Purpur 1.21.11.

## Build

Requires JDK 21 and Maven:

```sh
mvn --batch-mode clean package
```

The plugin JAR is written to `target/Infuse.jar`. The GitHub Actions workflow builds the branch and publishes the JAR as an Actions artifact.

## Install

1. Download the `Infuse.jar` build artifact and place it in the server's `plugins` directory.
2. Start the server once.
3. Edit `plugins/Infuse/config.yml`, `gui.yml`, and `messages.yml` as needed.
4. Restart the server or run `/infuse reload`.

The resource-pack prompt is enabled by default. The pack is currently fetched from the original public InfuseS3 repository; its URL and SHA-1 are configurable under `settings.resource-pack`. The pack is not vendored into this repository yet.

## Commands

- `/infuse` — open the effect menu.
- `/use <primary|secondary>` — activate a Spark.
- `/swap` — swap the equipped slots.
- `/control <primary|secondary> <swap|drop|sneak|sneak-drop|sneak-swap|none>` — bind a Spark to an action.
- `/item <effect|all> [true|false]` — give effect potions (admin).
- `/ldrain`, `/rdrain` — return an equipped effect to an item when it is off cooldown.
- `/recipes` — browse the 3x3 crafting recipes.
- Sneak-right-click a brewing stand to open the ritual-style crafting GUI.
- `/trust <add|remove|list|clear> [player]` and `/untrust <player>` — manage effect exemptions.
- `/infuse add|remove|get|history` — admin effect management, using the original `-p=`, `-e=`, `-s=`, and `-a=` flags.
- `/infuse reload` — reload settings and GUI layouts.

## Configuration and data

- `config.yml`: worlds, effect colors, cooldowns, craft limits, trust limits, resource pack, and per-effect tuning.
- `gui.yml`: main menu, effect selector, and recipe-book layout.
- `messages.yml`: command feedback.
- Player slot state, augmented state, control bindings, trust lists, history, and craft counts are saved to YAML.

## Conversion status

The current Java branch now includes consumable effect potions using the original custom-model-data IDs, primary/secondary slots, cooldown-aware Spark activation, action bindings, trust delays, death drops, configurable GUIs, recipe browsing/crafting, craft limits, and a subset of effect-specific passive/combat/Spark mechanics.

This is still an **in-progress conversion, not a verified production-equivalent replacement**. The original Skript has more behavior than is currently ported, including some augmented rituals, animation/particle sequences, WorldGuard mapped-region logic, and several detailed per-effect interactions. The latest source must pass CI and receive live-server testing before production use. Please do not assume every original edge case has been replicated yet.
