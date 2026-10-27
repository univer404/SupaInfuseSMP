# Infuse — Paper/Purpur plugin conversion

Java 21 conversion of the supplied InfuseS3 Skript pack for Paper/Purpur 1.21.11.

## Build

Requires JDK 21 and Maven:

```sh
mvn --batch-mode clean package
```

The JAR is written to `target/Infuse.jar`. GitHub Actions builds the branch and publishes a downloadable workflow artifact. The build also validates the configured resource-pack HTTPS URL, SHA-1, ZIP integrity, and `pack.mcmeta` so broken or changed hosting is caught by CI.

## Install

1. Download the latest successful `Infuse-1.0.0` workflow artifact.
2. Put `Infuse.jar` in the server's `plugins` folder.
3. Start the server once so the plugin creates its configuration files.
4. Review `plugins/Infuse/config.yml`, `gui.yml`, and `messages.yml`.
5. Keep `settings.resource-pack.enabled` enabled to prompt players to download the custom pack. The configured URL and SHA-1 must always match the exact ZIP being hosted.

## Texture pack

The original supplied pack is configured at the HTTPS URL in `src/main/resources/config.yml`; its expected SHA-1 is `2a4079e2f08424192e4ee8b8d3df26d1f386cd15`. The pack contains custom effect glyphs, potion/wind-charge item models, textures, and four custom sounds. The build validates the currently configured hosted ZIP, but the binary pack itself is still hosted by the original upstream repository rather than copied into this repository. The pack's original `pack.mcmeta` declares legacy `pack_format: 64`; this may show a compatibility warning on Minecraft 1.21.11 (resource-pack format 75). The metadata warning is distinct from whether the server can download the pack.

## Source layout

The Java source follows the package-oriented layout used by the reference project, with one purpose per package:

```text
src/main/java/com/catadmirer/infuseSMP/
├── Infuse.java                  # plugin lifecycle and shared effect state
├── commands/
│   └── InfuseCommandHandler.java
├── effects/
│   ├── EffectRegistry.java      # playable effect keys and icon ordering
│   └── SparkAbilityManager.java # Spark activation dispatch
├── expansions/
│   └── InfusePlaceholders.java  # optional PlaceholderAPI expansion
├── extraeffects/
│   ├── AugmentationManager.java
│   └── EmeraldEnchantingListener.java
├── gui/
│   ├── InfuseGui.java           # main and effect-selection menus
│   ├── RecipeBookGui.java       # recipe browser
│   └── CraftingGui.java         # crafting and ritual flow
├── listeners/
│   ├── DamageListener.java
│   ├── EffectItemListener.java
│   ├── PlayerLifecycleListener.java
│   └── SparkControlListener.java
├── managers/
│   └── MessageManager.java      # cached messages.yml reader
└── util/
    ├── regions/
    │   └── WorldGuardRegionManager.java
    └── trust/
        └── TrustManager.java
```

Configuration stays in `src/main/resources`; GUI layout is deliberately separate in `gui.yml`.

## Commands

- `/infuse` — open the Infuse menu.
- `/use <primary|secondary>` — activate a Spark.
- `/swap` — swap primary and secondary effects.
- `/control <primary|secondary> <swap|drop|sneak|sneak-drop|sneak-swap|none>` — bind Spark activation.
- `/item <effect|all> [true|false]` — give effect potions (admin).
- `/ldrain`, `/rdrain` — return an equipped effect as an item when it is off cooldown.
- `/recipes` — browse the recipe book.
- Sneak-right-click a brewing stand to open the crafting GUI.
- `/trust <add|remove|list|clear> [player]`, `/untrust <player>` — manage trust lists.
- `/infuse add|remove|get|history` — admin effect management.
- `/conf <get|set|list>` — view and edit config values (admin).
- `/mapregion <add|delete|list|clear>` — disable Infuse in WorldGuard regions (WorldGuard required).
- `/resetcraft <effect|all>`, `/resetcooldown [primary|secondary]`, and `/toggledisplay`.

## Optional integrations

### PlaceholderAPI

When installed, the plugin registers the `infuse` expansion. Identifiers include:

- `%infuse_effect_name_primary%`, `%infuse_effect_name_secondary%`
- `%infuse_effect_icon_primary%`, `%infuse_effect_icon_secondary%`
- `%infuse_effect_augmented_primary%`, `%infuse_effect_augmented_secondary%`
- `%infuse_effect_timer_main_primary%`, `%infuse_effect_timer_main_secondary%`
- `%infuse_effect_timer_alt_primary%`, `%infuse_effect_timer_alt_secondary%`
- `%infuse_effect_spark_ticks_primary%`, `%infuse_effect_spark_ticks_secondary%`
- `%infuse_effect_cooldown_ticks_primary%`, `%infuse_effect_cooldown_ticks_secondary%`

### WorldGuard

Install WorldGuard to use `/mapregion`. Without it, the plugin still supports the disabled-world list in `config.yml`.

## Status

The conversion has dedicated command, listener, GUI, effect-registry, augmentation/enchanting, message, trust, region, and placeholder packages. It implements the main effect items, primary/secondary slots, Spark activation/cooldowns, controls, trust delays, death handling, configurable GUIs, recipe browsing/crafting, augmented rituals, region restrictions, and a subset of effect-specific passive/combat abilities.

It remains an **in-progress conversion** rather than a verified feature-for-feature replacement. Some effect edge cases, original animation sequences, and SkBee-specific behavior still need parity testing. Use a test server first and do not treat it as production-equivalent until the remaining parity checks and live-server tests are complete.
