# Handy Tools

Handy Tools is a NeoForge 1.21.1 mod that adds six material hammers:

- Wooden Hammer
- Cobblestone Hammer
- Iron Hammer
- Golden Hammer
- Diamond Hammer
- Netherite Hammer

Each hammer uses an Ex Nihilo: Sequentia hammer sprite in inventory, first
person, dropped-item, item-frame, head, and fixed contexts. Third person uses
the full 3D hammer model at one model pixel per world pixel.

Hold right click while targeting a block to begin a focused press. The hammer
can lock only when the block center is within 1.5 blocks and 60 degrees of the
player's view, and the block bounding-box top is 0.5 to 1.5 blocks above the
player's feet. Its target-facing swing accelerates cubically around a pivot six
model pixels beyond the wooden handle, stops at the block top, and brings both
third-person hands toward the handle. Movement, camera turning, screen opening,
hotbar changes, and other interactions remain locked until right click is
released.

Create 6 and JEI 19 are declared as optional dependencies. Handy Tools builds
and runs without either mod installed.

## Build

```bash
./gradlew build
```

The distributable JAR is written to `build/libs/`.

## Asset credits

The six flat hammer item sprites are from Ex Nihilo: Sequentia and are licensed
separately under CC BY-NC-SA 4.0. See `THIRD_PARTY_NOTICES.md` for attribution,
source details, and the exact files covered by that license.

## Branches and releases

Long-lived branches are named for their Minecraft version and loader, such as
`1.21.1-neoforge`. Published mod versions use Git tags and GitHub releases.
