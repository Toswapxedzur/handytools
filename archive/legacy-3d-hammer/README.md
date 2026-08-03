# Legacy 3D hammer assets

This archive preserves the six full-scale third-person hammer models and their
textures as they existed immediately after commit `7f6be71`. It also contains
a `common-source` snapshot of the original golden master, all nine generated
3D material variants and textures, the subtle seven-color palettes, and their
generator. They are kept outside `src/main/resources`, so none are packaged
into Handy Tools.

The active item models now use the flat Ex Nihilo: Sequentia sprites documented
in `THIRD_PARTY_NOTICES.md`. Git history preserves the removed one-off renderer,
arm solver, and mixins.

Archived paths retain their original resource-pack layout below
`src/main/resources` so they can be restored or consulted without guessing the
old locations. The source snapshot similarly retains its original common
resource layout; the separate flat-item generator and assets are still active
and are intentionally not duplicated here.
