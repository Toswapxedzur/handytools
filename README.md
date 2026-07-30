# Handy Tools

Handy Tools is a NeoForge 1.21.1 mod that adds six material hammers:

- Wooden Hammer
- Cobblestone Hammer
- Iron Hammer
- Golden Hammer
- Diamond Hammer
- Netherite Hammer

Each hammer uses a compact mace-style sprite in inventory, first person,
dropped-item, item-frame, head, and fixed contexts. Third person uses the full
3D hammer model at one model pixel per world pixel.

Hold right click while targeting a block to begin a focused press. The hammer
falls into its pressed position, the third-person player grips it with both
hands, and movement, camera turning, screen opening, hotbar changes, and other
interactions remain locked until right click is released.

Create 6 and JEI 19 are declared as optional dependencies. Handy Tools builds
and runs without either mod installed.

## Build

```bash
./gradlew build
```

The distributable JAR is written to `build/libs/`.

## Branches and releases

Long-lived branches are named for their Minecraft version and loader, such as
`1.21.1-neoforge`. Published mod versions use Git tags and GitHub releases.
