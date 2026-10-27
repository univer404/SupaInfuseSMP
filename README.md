# Infuse — Paper/Purpur plugin conversion

Java 21 conversion of the supplied InfuseS3 Skript pack for Paper/Purpur 1.21.11.

## Build

Requires JDK 21 and Maven:

```sh
mvn --batch-mode clean package
```

The plugin JAR is written to `target/Infuse.jar`. GitHub Actions compiles the branch and uploads the JAR as a workflow artifact.

## Install

1. Download the `Infuse.jar` build artifact and place it in the server's `plugins` directory.
2. Start the server once.
3. Configure `plugins/Infuse/config.yml`, `gui.yml`, and `messages.yml`.
4. Restart the server or run `/infuse reload`.

The texture-pack prompt is enabled by default and downloads the original pack from the public InfuseS3 repository. The URL and SHA-1 can be changed under `settings.resource-pack`. The pack is externally hosted rather than copied into this repository.

## Commands

- `/infuse` — open the Infuse menu.
- `/use <primary|secondary>` — activate a Spark.
- `/swap` — swap primary and secondary effects.
- `/control <primary|secondary> <swap|drop|sneak|sneak-drop|sneak-swap|none>` — bind Spark activation.
- `/item <effect|all> [true|false]` — give effect potions (admin).
- `/ldrain`, `/rdrain` — return an equipped effect as an item when it is off cooldown.
- `/recipes` — browse the recipe book.
- Sneak-right-click a brewing stand to open the crafting GUI.
- `/trust <add|remove|list|clear> [player]`, `/untrust <player>` — manage effect exemptions.
- `/infuse add|remove|get|history` — admin effect management, supporting `-p=`, `-e=`, `-s=`, and `-a=` flags.
- `/conf <get|set|list>` — view and edit config values (admin).
- `/mapregion <add|delete|list|clear>` — disable Infuse in WorldGuard regions (WorldGuard required).
- `/resetcraft <effect|all>` and `/resetcooldown [primary|secondary]` — admin maintenance commands.
- `/toggledisplay` — toggle the per-player HUD.

## PlaceholderAPI

When PlaceholderAPI is installed, the plugin registers the `infuse` expansion. Supported identifiers include:

- `%infuse_effect_name_primary%`, `%infuse_effect_name_secondary%`
- `%infuse_effect_icon_primary%`, `%infuse_effect_icon_secondary%`
- `%infuse_effect_augmented_primary%`, `%infuse_effect_augmented_secondary%`
- `%infuse_effect_timer_main_primary%`, `%infuse_effect_timer_main_secondary%`
- `%infuse_effect_timer_alt_primary%`, `%infuse_effect_timer_alt_secondary%`
- `%infuse_effect_spark_ticks_primary%`, `%infuse_effect_spark_ticks_secondary%`
- `%infuse_effect_cooldown_ticks_primary%`, `%infuse_effect_cooldown_ticks_secondary%`

## Files

- `config.yml`: worlds, effect colors, cooldowns, craft limits, trust settings, resource pack, and effect tuning.
- `gui.yml`: main menu, effect selector, and recipe-book layout.
- `messages.yml`: message strings.
- Player slots, augmented state, controls, trust lists, display preference, history, and craft counts use YAML persistence.

## Conversion status

The Java branch includes consumable effect potions with the original custom-model-data IDs, primary/secondary slots, cooldown-aware Spark activation, action bindings, trust delays, death drops, configurable GUIs, recipe browsing/crafting, augmented rituals, WorldGuard disabled regions, PlaceholderAPI identifiers, and a substantial subset of the effect-specific passive/combat/Spark mechanics.

This is still an **in-progress conversion, not yet a verified production-equivalent replacement**. Some original per-effect edge cases, exact animation sequences, and the original SkBee region-mapping workflow are not identical yet. The latest Java source must pass CI and be tested on a live 1.21.11 server before production use.
