# Handy Tools

Handy Tools is a NeoForge 1.21.1 mod that adds six material hammers:

- Wooden Hammer
- Cobblestone Hammer
- Iron Hammer
- Golden Hammer
- Diamond Hammer
- Netherite Hammer

Each hammer uses a compact mace-style sprite in inventory GUIs. In first
person, third person, dropped-item, item-frame, and other non-GUI contexts, it
uses the full 3D hammer model.

Create 6 is declared as an optional dependency. Handy Tools builds and runs
without Create installed.

## Build

```bash
./gradlew build
```

The distributable JAR is written to `build/libs/`.

## Branches and releases

Long-lived branches are named for their Minecraft version and loader, such as
`1.21.1-neoforge`. Published mod versions use Git tags and GitHub releases.
