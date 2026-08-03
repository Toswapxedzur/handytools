# Handy Tools

Handy Tools is a NeoForge 1.21.1 mod for reworking familiar tools with more
purposeful mechanics and full-body animation. It currently registers six
material hammers:

- Wooden Hammer
- Cobblestone Hammer
- Iron Hammer
- Golden Hammer
- Diamond Hammer
- Netherite Hammer

Each hammer currently uses its flat Ex Nihilo: Sequentia sprite in every item
display context. The retired full-scale 3D models and textures are preserved
outside the shipped resources under `archive/legacy-3d-hammer/`.

## Shared tool-action lifecycle

Continuous tool use is driven by one reusable, server-authoritative phase
contract:

1. `PREPARATION`: normal held pose to the designated operating/contact pose.
2. `OPERATION_RAISE`: contact pose to the raised pose above the head.
3. `OPERATION_DESCEND`: raised pose back to contact.
4. `RELEASE`: contact pose back to the normal held pose.

Raise and descend repeat while right click remains held. The server fires the
tool's impact hook only when descent reaches contact, so future block changes,
damage, sounds, particles, durability, and cooldowns can share the exact action
timing rather than being inferred from a client animation. The hammer's final
impact mechanic is intentionally not assigned yet.

For the hammer, the preparation endpoint must place the handle line along the
acting arm rather than perpendicular to it. That is now an explicit animation
contract, not geometry hard-coded into the item renderer.

Releasing during preparation finishes the move to the contact pose without
starting a stroke; releasing during a stroke finishes that stroke. Release then
starts from contact, where both adjacent smoothstep curves have zero velocity,
so the tool never snaps or reverses direction abruptly. Phase state exposes
partial-tick smoothstep progress for the PAL animation layer that will be added
next.

Hammer targeting retains the focused-use constraints: the block center must be
within 1.5 blocks and inside a 60-degree view cone, and its outline bounding-box
top must be 0.5 to 1.5 blocks above the player's feet. Movement, camera turning,
screen opening, hotbar changes, attacks, mining, and other interactions remain
locked through preparation, operation, and release.

Player Animation Library (PAL) is embedded as a required nested NeoForge mod,
so users only need to install the Handy Tools JAR. Create 6 and JEI 19 remain
optional dependencies; Handy Tools builds and runs without either one.

## Build

```bash
./gradlew build
```

The distributable JAR is written to `build/libs/`.

## Asset credits

The six flat hammer item sprites are from Ex Nihilo: Sequentia and are licensed
separately under CC BY-NC-SA 4.0. See `THIRD_PARTY_NOTICES.md` for attribution,
source details, and the exact files covered by that license.

The embedded Player Animation Library is separately licensed under MIT. Its
original JAR and license file are preserved inside the Handy Tools JAR.

## Branches and releases

Long-lived branches are named for their Minecraft version and loader, such as
`1.21.1-neoforge`. Published mod versions use Git tags and GitHub releases.
