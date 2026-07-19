FIRST CRUSADE — HIVE CITY BLOCK SETS
Forge 1.20.1 / Java 17

This package adds 48 new Hive City blocks based on the approved concept sheets:

SET I — STRUCTURE (16)
- Armored Bulkhead Wall
- Recessed Steel Wall Panel
- Gothic Arch Wall
- Tall Ribbed Pillar
- Buttress Column
- Cathedral Cornice
- Lower Wall Molding
- Spire Cap Block
- Balcony Edge Trim
- Bridge Support Block
- Giant Door Segment
- Narrow Lancet Recess
- Triangular Relief Panel
- Window Slot Frame
- Heavy Structural Frame
- Vertical Seam Strip

SET II — INDUSTRIAL SYSTEMS (16)
- Straight Pipe
- Elbow Pipe
- T Pipe Junction
- Cross Pipe Junction
- Pipe Support Clamp
- Vertical Service Conduit
- Cable Bundle Block
- Vent Outlet
- Floor Vent
- Lift Rail
- Gantry Beam
- Suspended Track Anchor
- Maintenance Hatch
- Machine Casing
- Hazard Grated Floor
- Reinforced Platform Edge

SET III — FLOORS, LIGHTING & DETAILS (16)
- Glowing Shrine Window
- Stained Window Variant
- Candle Alcove
- Wall Sconce
- Shrine Recess
- Bloodstained Floor Tile
- Cathedral Floor Tile
- Metal Floor Plate
- Floor Grate
- Cathedral Stair Block
- Landing Slab
- Balustrade Railing
- Skull Relief Panel
- Gargoyle Pedestal
- Industrial Crate
- Brazier Block

FILES ADDED
- Java block registry: src/main/java/com/example/examplemod/hive/HiveCityConceptBlocks.java
- 48 blockstates
- 48 block models
- 48 item models
- 48 loot tables
- 48 pixel-art 64x64 UV atlases
- English and Brazilian Portuguese translations
- Pickaxe/axe harvesting tags
- 48 editable Blockbench .bbmodel source files
- UV and model preview sheets

BLOCKBENCH SOURCES
Open the .bbmodel files located in:
blockbench_sources/hive_city_blocks/

The texture is embedded in every .bbmodel and is also available in each set's textures folder.

BUILD NOTE
The files and JSON resources were statically validated. A Gradle build could not be completed in the isolated environment because the Gradle 8.8 wrapper distribution was not locally cached and internet access was unavailable. On the normal development PC, run:

gradlew build

or:

gradlew runClient

============================================================
HIVE CITY REBUILD COMPLETE V1
============================================================
The consolidated architecture package is documented in:
  docs/HIVE_CITY_REBUILD_COMPLETE_V1.md

Offline validation report:
  HIVE_CITY_FINAL_VALIDATION.txt
