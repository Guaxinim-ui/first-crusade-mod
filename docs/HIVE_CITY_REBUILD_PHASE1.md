# HIVE CITY REBUILD — PHASE 1

## Objective

Establish a safe visual/technical baseline before replacing the full generated city. This phase
adds one 64x64x64 showcase sector built with the 48 new Hive City blocks, fixes critical district
registration/rotation issues, and makes the Spire functional.

## New test sector

- District ID: `firstcrusade:visual_test`
- Module ID: `firstcrusade:test/hive_visual_test_01`
- Template: `firstcrusade:hive/test/hive_visual_test_01`
- Size: `64x64x64`
- Easy command: `/fchive city preview`
- Manual command: `/fchive district place firstcrusade:visual_test`

The sector contains:

- monumental south gate;
- cathedral-industrial nave;
- open central firing/pathfinding lane;
- two elevated side galleries;
- safe stair access;
- industrial west service wall;
- lift rails, pipe network, vents, machines and cable banks;
- devotional east wall with windows, niches and braziers;
- raised north sanctuary;
- patrol, worker, civilian, cover, commander and loot markers.

All 48 new concept blocks are represented at least once in the template palette.

## Critical fixes

### Manufactorum registration

The existing three NBT templates now have matching module JSON files and a new
`hive_districts/manufactorum.json`. City generation no longer silently skips the industrial layer.

### Interior ground level

Manufactorum now begins at `GROUND_Y` instead of `GROUND_Y + 64`. Hab Stacks, Administratum and
Spire remain stacked in 64-block levels above it. This removes the empty 64-block void under every
interior cell.

### District rotation

Whole-district rotation now rotates each module bounding box inside a normalized positive
footprint. Rotated templates also receive placement-origin compensation, preventing 90/180/270
degree structures from shifting into negative local coordinates or escaping their planned cells.

### Functional Spire

A first functional `spire_crown_01.nbt` was generated as a `64x96x64` cathedral-command tower and
centered inside the 192x128 center district using offset `[64, 0, 32]`.

## Generators

- `tools/gen_hive_visual_test.py`
- `tools/gen_hive_spire.py`
- `tools/render_hive_isometric.py`

The templates and QA previews can be regenerated without opening Minecraft.

## Test order in Minecraft

1. Run `gradlew runClient`.
2. Open or create a world with commands enabled.
3. Run `/reload`.
4. Run `/fchive city tp`.
5. Run `/fchive city preview`.
6. Inspect rotations, model facing, light levels, collision and mob routes.
7. Only after approving this baseline, regenerate the gate/cargo modules and the remaining districts.

## Known limits of Phase 1

- The existing gate, cargo, hab, admin and underhive NBTs have not yet been visually rebuilt.
- The new test template is a design-validation slice, not the final city cell.
- Full Gradle compilation could not be run in the artifact environment because Gradle 8.8 was not
  cached and internet access was unavailable. JSON/NBT and Java syntax checks were run locally.
