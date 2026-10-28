# Infuse — Paper/Purpur plugin conversion

Java conversion of the supplied InfuseS3 Skript pack for Paper/Purpur. Plugin author metadata is set to `universe404`.

## Build

Requires JDK 21 and Maven:

```sh
mvn --batch-mode clean package
```

The JAR is written to `target/Infuse.jar`. GitHub Actions builds the branch and publishes a downloadable workflow artifact. The build prepares a Minecraft Java 26.3 resource pack, publishes it as a stable release asset, computes its SHA-1, injects the matching URL/checksum into the packaged plugin config, then builds the plugin.

## Install

1. Download the latest successful `Infuse-1.0.0` workflow artifact.
2. Put `Infuse.jar` in the server's `plugins` folder.
3. Start the server once so the plugin creates its configuration files.
4. Review `plugins/Infuse/config.yml`, `gui.yml`, and `messages.yml`.
5. Keep `settings.resource-pack.enabled` enabled to prompt players to download the custom pack. The configured URL and SHA-1 must always match the exact ZIP being hosted.

## Texture pack

The Minecraft Java 26.3 pack is published as a stable release asset: `Infuse-Texture-Pack-26.3.zip` at the `tpack-26.3` release. The build computes the exact SHA-1 for the generated ZIP and injects it into the packaged plugin config, so the resource-pack URL and checksum stay matched.

The pack metadata targets Minecraft Java 26.3 resource-pack format 97.1 using `min_format: [97, 1]` and `max_format: [97, 1]`. This updates compatibility metadata only; it does **not** migrate assets whose model, font, texture, or sound formats changed between releases. Test on a 26.3 client and inspect missing-texture/font/model warnings before using it on a production server.
## Source layout

The Java source follows the package-oriented layout used by the reference project, with one purpose per package:

```text
src/main/java/com/supainfuse/smp/
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
