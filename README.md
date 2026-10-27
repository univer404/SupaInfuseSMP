# SupaInfuseSMP — Infuse Java Conversion

This branch establishes the Paper plugin conversion foundation from the supplied Infuse Skript archive.

## Build
Requires JDK 21 and Maven. Run `mvn clean package`. The output is `target/Infuse.jar`. GitHub Actions builds the project and uploads the JAR artifact.

## Install
1. Place the JAR in the server's `plugins` directory.
2. Start the server once to generate `config.yml`, `gui.yml`, and `messages.yml`.
3. Configure the settings and restart.

## Conversion status
The initial Java implementation includes command handling, configurable GUI files, player effect-slot persistence, trust-list commands, basic passive effects, and Spark activations. It is **not yet a behavior-complete port** of all original Skript mechanics. The original texture pack has not yet been embedded in the repository; use a hosted pack URL and SHA-1 in `config.yml` to enable the pack prompt. Do not treat this build as a production replacement until all original recipes, combat interactions, augmentations, animation behaviors, and effect-specific rules have been ported and tested.
