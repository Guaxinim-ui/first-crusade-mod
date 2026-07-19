# Hive City Rebuild — Phase 3: South Ash Gate + Cargo Ring V2

This phase replaces the six modules of the southern perimeter with a unified
192×64×128 layout that is sliced back into the existing 64×64×64 module ids.
This preserves the current district loader while producing continuous roads,
walls, galleries and industrial systems across module boundaries.

## Updated modules

- `cargo/warehouse_01`
- `cargo/cargo_yard_01`
- `cargo/military_depot_01`
- `gates/hive_wall_w_01`
- `gates/south_ash_gate_01`
- `gates/hive_wall_e_01`

## Visual changes

- Wide stepped gatehouse with two integrated towers and a broad crown.
- Irregular wall towers, deep buttresses, balconies and damaged sectors.
- Two internal wall galleries and an accessible upper walkway.
- Gabled cargo buildings rather than simple rectangular boxes.
- Crane, suspended cargo pod, scanner arch, service bridges and rail platforms.
- Arsenal tower, armored vault and an Underhive shaft in the military depot.
- New Hive City block sets used across facades, roofs, floors and details.
- Unified module generation prevents seams and road misalignment.

## Test

```text
/fchive district place firstcrusade:south_ash_gate
```

For a clean test, place it in an open area. The district occupies 192×128 blocks
and reaches approximately 64 blocks above its base Y.
