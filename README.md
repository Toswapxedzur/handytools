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
starts from the exact contact endpoint, so the tool never jumps between phases.
PAL interpolates the authored poses every render frame: preparation and release
use eased cubic motion, the complete raise is linear, and the complete downward
slam uses cubic acceleration into the server-owned impact tick.

The hammer PAL layer moves only the acting arm and its item. The torso, head,
and both legs make a restrained supporting weight shift; the free arm remains
under vanilla control. The pose mirrors for left-handed and off-hand actions.
During preparation, the visible head and body turn toward the captured block
center with the same cubic ease as the pose, while the actual first-person yaw
and pitch remain locked; release reverses that turn. The local player animation
uses the local copy of the authoritative phase clock, avoiding phase-packet
rewinds, while tracking clients use server snapshots. PAL changes between the
four clips without an additional hard phase reset, and every clip shares exact
seam poses: held -> contact -> overhead -> contact -> held.

Release is requested only by Minecraft's release-use callbacks. A transient
client-side `isUsingItem` value during startup cannot redirect preparation into
release, so a held action deterministically reaches raise and descent.

Hammer targeting retains the focused-use constraints: the block center must be
within 1.5 blocks and inside a 60-degree view cone, and its outline bounding-box
top must be 0.5 to 1.5 blocks above the player's feet. Movement, camera turning,
screen opening, hotbar changes, attacks, mining, and other interactions remain
locked through preparation, operation, and release.

Player Animation Library (PAL) is embedded as a required nested NeoForge mod,
so users only need to install the Handy Tools JAR. PAL deliberately leaves the
vanilla first-person camera and hands untouched. First-person Model 2.7.2+ is a
client-only optional dependency; when installed, it renders the PAL-driven
third-person body in first person without letting the animation layer transform
the camera. Create 6 and JEI 19 also remain optional dependencies. Handy Tools
builds and runs without any of those optional mods.

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
