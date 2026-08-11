package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * A living Ork Camp. It keeps a standing garrison of Boyz, builds WAAAGH! over time, and once
 * the WAAAGH! peaks it hurls a war party at the city it threatens. Destroy the camp block to
 * silence the threat at its source — making the camp a natural objective for a sortie.
 */
public class OrkCampBlockEntity extends BlockEntity {
    private static final String CAMP_ORK_TAG = "FirstCrusadeCampOrk";
    private static final String CAMP_POS_TAG = "FirstCrusadeCampPos";

    private static final int MAX_CAMP_LEVEL = 4;

    // Momentum (gathered each 200-tick cycle) needed to grow from the current level to the next;
    // indexed by current campLevel. The camp swells from a small encampment into a fortified Ork
    // stronghold — the Ork mirror of an Imperial city levelling up.
    private static final int[] GROWTH_TO_NEXT = {0, 24, 60, 120};

    private static final int GARRISON_RADIUS = 24;
    private static final int WAAAGH_PER_CYCLE = 5;
    private static final int WAAAGH_THRESHOLD = 150;
    private static final int WAR_PARTY_SIZE = 3;

    private static final int WAR_PARTIES_BEFORE_WARBOSS = 3;

    // Hard ceiling on how many Orks may be milling about a target city at once. Because war-party
    // Orks are persistent (they never despawn), without this they pile up into an endless swarm.
    // When the field is already saturated the camp holds its assault back (its WAAAGH dissipates).
    private static final int WAR_FIELD_ORK_CAP = 20;
    private static final int WAR_FIELD_RADIUS = 56;

    // ===== Ork city economy (so a camp is a working settlement, not a free mob spawner) =====
    // Grots (Gretchin) are the labouring populace: they scavenge LOOT, which the city spends to raise
    // Boyz and to muster war hosts. Boyz are no longer conjured from nothing — grots -> loot -> Boyz,
    // the Ork mirror of the Imperial citizens -> resources -> troops loop. War hosts mobilise EXISTING
    // Boyz (the garrison thins when it attacks and must be rebuilt from the economy).
    private static final int LOOT_PER_GROT = 2;        // loot each grot scavenges per cycle
    private static final int LOOT_PER_PIT = 3;         // bonus loot per built Loot Pit per cycle
    private static final int LOOT_PIT_COST = 40;       // loot to build one Loot Pit
    private static final int BUILD_SCAN_RADIUS = 26;   // how far from the heart a structure may be placed
    private static final int LOOT_CAP = 4000;
    private static final int BOY_LOOT_COST = 8;        // loot to raise one Boy
    private static final int WAR_PARTY_LOOT_COST = 40; // loot to muster and march a war host
    private static final int GROWTH_LOOT_COST = 150;   // loot spent to expand the city a level
    private static final int MIN_BOYZ_TO_MUSTER = 4;   // need a real warband before attacking
    // Population grows SLOWLY and on its own — at most one new Ork every few cycles, never a fountain
    // that refills to a cap from nothing. (200-tick cycles, so 3 = one Ork roughly every 30s.)
    private static final int GROWTH_INTERVAL_CYCLES = 3;

    // The WAAAGH! spread: at a grown global tier, a camp plants ONE daughter camp farther out, so
    // the green tide expands across the world on its own. Bounded to one child per camp.
    private static final int SPREAD_MIN_TIER = 3;
    private static final int SPREAD_CHANCE = 24; // ~1 in 24 per cycle (200 ticks) once eligible

    private BlockPos targetCorePos;
    private OrkClan clan = OrkClan.GOFFS;
    private int campLevel = 1;
    private int growth = 0;
    private int loot = 0;
    private boolean founded = false;
    private int growthCooldown = 0;
    private int waaagh = 0;
    private int warPartiesLaunched = 0;
    private boolean warbossSpawned = false;
    private boolean hasSpread = false;
    // Set while a player-started raid owns this camp; see checkOverrun.
    private boolean underAssault = false;
    // How far this camp's spores reach, in blocks. Published to WorldWarMapData so the flora
    // decorator knows how much ground to turn Ork.
    private int sporeRadius = 0;
    private int tickCounter = 0;

    // Cached populace/garrison/structure counts for the Ork Core GUI (refreshed each economy cycle so
    // the menu never triggers entity/block scans on its own polling).
    private int cachedGrots = 0;
    private int cachedBoyz = 0;
    private int cachedLootPits = 0;

    public OrkCampBlockEntity(BlockPos pos, BlockState state) {
        super(FCRegistry.ORK_CAMP_BLOCK_ENTITY.get(), pos, state);
    }

    public void setTargetCore(BlockPos corePos) {
        this.targetCorePos = corePos == null ? null : corePos.immutable();
        setChanged();
    }

    public void setClan(OrkClan clan) {
        this.clan = clan == null ? OrkClan.GOFFS : clan;
        setChanged();
    }

    public OrkClan getClan() {
        return this.clan;
    }

    public int getWaaagh() {
        return this.waaagh;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OrkCampBlockEntity camp) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        camp.tickCounter++;

        if (camp.tickCounter < 200) {
            return;
        }

        camp.tickCounter = 0;

        // If an Imperial force has overrun the camp and broken its garrison, the camp is razed.
        if (camp.checkOverrun(serverLevel, pos)) {
            return;
        }

        // Put this camp on the world war map (so the Core's strategic map shows it).
        WorldWarMapData.get(serverLevel).recordCamp(pos);

        camp.foundIfNeeded(serverLevel, pos);
        camp.produceLoot(serverLevel, pos);
        camp.growSlowly(serverLevel, pos);
        camp.buildWaaagh(serverLevel, pos);
        camp.tryGrowCamp(serverLevel, pos);
        camp.trySpreadWaaagh(serverLevel, pos);

        // The camp's spore field widens with the WAAAGH!. This is only a number: the actual Ork
        // ground cover is placed by the flora decorator, inside this radius, on chunks that happen
        // to be loaded. Nothing is written to the world here.
        int tier = WaaaghOverlordManager.getTier(serverLevel);
        camp.sporeRadius = OrkSporeManager.grow(tier, camp.sporeRadius);

        // Publish the camp's territorial attributes for the flora decorator. The corruption radius
        // is the one that just grew, so Ork vegetation follows the sculk halo outward by itself.
        WorldWarMapData.get(serverLevel).recordCampInfo(pos, camp.clan, camp.campLevel, camp.sporeRadius);

        camp.setChanged();
    }

    // Once the global WAAAGH! has grown (tier 2+), an established camp eventually plants a single
    // daughter camp farther out that joins the assault on the same city — the green tide spreading
    // across the world on its own. Each camp spreads at most once (hasSpread), so growth is gradual.
    private void trySpreadWaaagh(ServerLevel serverLevel, BlockPos pos) {
        // The fixed test world keeps exactly the seeded Ork cities — no self-spreading daughter camps.
        if (ExampleMod.TEST_FIXED_WORLD) {
            return;
        }

        if (this.hasSpread || this.targetCorePos == null) {
            return;
        }

        if (WaaaghOverlordManager.getTier(serverLevel) < SPREAD_MIN_TIER) {
            return;
        }

        if (serverLevel.random.nextInt(SPREAD_CHANCE) != 0) {
            return;
        }

        BlockPos child = OrkCampManager.seedSpreadCamp(serverLevel, pos, this.targetCorePos);

        if (child != null) {
            this.hasSpread = true;
            setChanged();
        }
    }

    // The settlement's founding inhabitants are placed exactly ONCE (a small starting group), then
    // the city grows on its own. Nothing is ever maintained-to-cap, so Orks never pour in from nothing.
    private void foundIfNeeded(ServerLevel serverLevel, BlockPos pos) {
        if (this.founded) {
            return;
        }
        this.founded = true;
        setChanged();

        int startGrots = this.campLevel <= 1 ? 2 : Math.min(populaceForLevel(this.campLevel), 3);
        int startBoyz = this.campLevel <= 1 ? 4 : Math.min(garrisonForLevel(this.campLevel), 6);

        for (int i = 0; i < startGrots; i++) {
            spawnCampUnit(serverLevel, pos, FCRegistry.GRETCHIN.get());
        }
        for (int i = 0; i < startBoyz; i++) {
            spawnCampUnit(serverLevel, pos, FCRegistry.ORK_BOY.get());
        }
    }

    // The populace scavenges loot — the city's economy, which funds Boyz and war hosts. Built Loot
    // Pits add a steady bonus. This also refreshes the cached counts the Ork Core GUI reads.
    private void produceLoot(ServerLevel serverLevel, BlockPos pos) {
        int grots = countCampOfType(serverLevel, pos, FCRegistry.GRETCHIN.get());
        int pits = countLootPits(serverLevel, pos);

        this.cachedGrots = grots;
        this.cachedBoyz = countCampOfType(serverLevel, pos, FCRegistry.ORK_BOY.get());
        this.cachedLootPits = pits;

        // The single Loot Pit scales with the Ork city level (it levels up instead of multiplying).
        this.loot = Math.min(LOOT_CAP,
                this.loot + grots * LOOT_PER_GROT + pits * LOOT_PER_PIT * Math.max(1, this.campLevel));
        setChanged();
    }

    // Counts the Loot Pits built around this Ork city (the Ork mirror of an Imperial work-site).
    private int countLootPits(ServerLevel serverLevel, BlockPos pos) {
        int count = 0;

        for (BlockPos p : BlockPos.betweenClosed(
                pos.offset(-BUILD_SCAN_RADIUS, -4, -BUILD_SCAN_RADIUS),
                pos.offset(BUILD_SCAN_RADIUS, 8, BUILD_SCAN_RADIUS))) {
            if (serverLevel.getBlockState(p).is(FCRegistry.ORK_LOOT_PIT.get())) {
                count++;
            }
        }

        return count;
    }

    // Self-driven, GRADUAL growth: at most ONE new Ork per interval, up to the city's caps — never a
    // refill-from-nothing. Grots (the workers) grow first; Boyz (warriors) are then raised from loot.
    // When war kills or draws off Orks, the city rebuilds at this same slow pace.
    private void growSlowly(ServerLevel serverLevel, BlockPos pos) {
        if (this.growthCooldown > 0) {
            this.growthCooldown--;
            setChanged();
            return;
        }

        int grots = countCampOfType(serverLevel, pos, FCRegistry.GRETCHIN.get());
        if (grots < populaceForLevel(this.campLevel)) {
            spawnCampUnit(serverLevel, pos, FCRegistry.GRETCHIN.get());
            this.growthCooldown = GROWTH_INTERVAL_CYCLES;
            setChanged();
            return;
        }

        int boyz = countCampOfType(serverLevel, pos, FCRegistry.ORK_BOY.get());
        if (boyz < garrisonForLevel(this.campLevel) && this.loot >= BOY_LOOT_COST) {
            spawnCampUnit(serverLevel, pos, FCRegistry.ORK_BOY.get());
            this.loot -= BOY_LOOT_COST;
            this.growthCooldown = GROWTH_INTERVAL_CYCLES;
            setChanged();
        }
    }

    // The grot populace per city level — the workers that drive the economy.
    private static int populaceForLevel(int campLevel) {
        return switch (campLevel) {
            case 2 -> 4;
            case 3 -> 6;
            case 4 -> 8;
            default -> 3;
        };
    }

    // The standing Boy garrison cap: a small encampment holds a handful (6-8); a full Ork city keeps
    // a horde, up to the 50-warrior cap.
    private static int garrisonForLevel(int campLevel) {
        return switch (campLevel) {
            case 2 -> 8;
            case 3 -> 12;
            case 4 -> 16;
            default -> 4;
        };
    }

    // Seeder hook: raise a freshly planted camp straight to a sizeable Ork city of the given level
    // (fortified structure + bigger garrison), the Ork counterpart of a founded Imperial city.
    public void seedAsCity(ServerLevel serverLevel, int level) {
        this.campLevel = Math.max(1, Math.min(MAX_CAMP_LEVEL, level));
        // A modest starting loot so the city can begin raising Boyz — NOT enough to burst a whole
        // garrison into being. It founds a small group on its first tick, then grows slowly.
        this.loot = 60;
        OrkCampManager.fortifyCamp(serverLevel, this.worldPosition, this.campLevel);
        setChanged();
    }

    // The camp gathers momentum every cycle (faster as the global WAAAGH! tier rises) and, once it
    // has built enough, fortifies into a larger stronghold — the Ork mirror of a city levelling up.
    private void tryGrowCamp(ServerLevel serverLevel, BlockPos pos) {
        if (this.campLevel >= MAX_CAMP_LEVEL) {
            return;
        }

        int tier = WaaaghOverlordManager.getTier(serverLevel);
        this.growth += 1 + tier;

        if (this.growth < GROWTH_TO_NEXT[this.campLevel]) {
            return;
        }

        // A city only expands when it is prospering: a full populace and a loot surplus to spend on
        // the expansion — the Ork mirror of an Imperial city needing its population at cap to grow.
        int grots = countCampOfType(serverLevel, pos, FCRegistry.GRETCHIN.get());
        if (grots < populaceForLevel(this.campLevel) || this.loot < GROWTH_LOOT_COST) {
            return;
        }

        this.loot -= GROWTH_LOOT_COST;
        this.campLevel++;
        this.growth = 0;
        OrkCampManager.fortifyCamp(serverLevel, pos, this.campLevel);
        setChanged();

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                pos,
                Component.translatable("msg.firstcrusade.bcast.camp_grew", this.clan.getDisplayName(), this.campLevel)
        );
    }

    private void buildWaaagh(ServerLevel serverLevel, BlockPos pos) {
        // The global WAAAGH! tier makes every camp gather momentum faster.
        int tier = WaaaghOverlordManager.getTier(serverLevel);
        this.waaagh += WAAAGH_PER_CYCLE + tier * 2;
        setChanged();

        // Once mustered, the host marches only if the city can pay to field it AND actually mobilises
        // a warband. Otherwise it stays ready and waits for the economy/garrison to catch up.
        // Waves are disabled for now (owner request) — the city just grows.
        if (ExampleMod.ORK_WAVES_ENABLED && this.waaagh >= WAAAGH_THRESHOLD && this.loot >= WAR_PARTY_LOOT_COST) {
            if (launchWarParty(serverLevel, pos)) {
                this.loot -= WAR_PARTY_LOOT_COST;
                this.waaagh = 0;
                setChanged();
            }
        }
    }

    // Musters a war host from the city's STANDING Boyz and marches it on the target — it does NOT
    // conjure fresh Orks. Returns true if a host actually set out (so the caller charges the loot).
    private boolean launchWarParty(ServerLevel serverLevel, BlockPos pos) {
        if (this.targetCorePos == null) {
            return false;
        }

        // Don't keep feeding an already-saturated battlefield. Persistent Orks otherwise stack up
        // into an unbeatable swarm; once the cap is reached the city holds its host back.
        if (countNearbyOrks(serverLevel, this.targetCorePos) >= WAR_FIELD_ORK_CAP) {
            return false;
        }

        List<OrkBoyEntity> garrison = serverLevel.getEntitiesOfClass(
                OrkBoyEntity.class,
                garrisonBox(pos),
                ork -> ork.isAlive() && isCampOrk(ork, pos)
        );

        if (garrison.size() < MIN_BOYZ_TO_MUSTER) {
            return false; // not enough of an army raised yet — keep building it from the economy
        }

        // The green tide marching on a city tips the war toward the WAAAGH!.
        WarDominionManager.shift(serverLevel, -2);

        int tier = WaaaghOverlordManager.getTier(serverLevel);
        int wanted = WAR_PARTY_SIZE + tier + this.clan.getBonusBoyz() + (this.campLevel - 1);
        int partySize = Math.min(garrison.size(), wanted);

        for (int i = 0; i < partySize; i++) {
            marchOrkToWar(garrison.get(i));
        }

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.targetCorePos,
                Component.translatable("msg.firstcrusade.bcast.war_party", this.clan.getDisplayName(), this.clan.getTactics())
        );

        this.warPartiesLaunched++;
        setChanged();

        // The greater the global WAAAGH! (overlord tier), the sooner a Warboss rises to lead the
        // assault — the Ork mirror of the Imperial Crusade dispatching heavier reinforcements.
        int requiredWarParties = Math.max(1, WAR_PARTIES_BEFORE_WARBOSS - tier);

        // The Warboss is conjured fresh, so in the fixed test world (nothing appears from nothing)
        // it is suppressed — the war is fought entirely by the city's own mustered Boyz.
        if (!ExampleMod.TEST_FIXED_WORLD
                && !this.warbossSpawned && this.warPartiesLaunched >= requiredWarParties) {
            spawnWarboss(serverLevel, pos);
        }

        return true;
    }

    // A standing Boy leaves the garrison and marches to war: it stops counting toward the city's
    // defence, so the city must raise a replacement from its loot. No new entity is created.
    private void marchOrkToWar(OrkBoyEntity ork) {
        ork.getPersistentData().putBoolean(CAMP_ORK_TAG, false);
        ork.getNavigation().moveTo(
                this.targetCorePos.getX() + 0.5D,
                this.targetCorePos.getY(),
                this.targetCorePos.getZ() + 0.5D,
                1.1D
        );
        ork.setPersistenceRequired();
    }

    // Once the WAAAGH! has built enough momentum, the camp's biggest Ork rises to lead it.
    private void spawnWarboss(ServerLevel serverLevel, BlockPos pos) {
        if (this.targetCorePos == null) {
            return;
        }

        WarbossEntity warboss = FCRegistry.WARBOSS.get().create(serverLevel);

        if (warboss == null) {
            return;
        }

        BlockPos spawnPos = groundPos(serverLevel, pos);

        warboss.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        this.clan.applyTo(warboss);
        warboss.setTargetCore(this.targetCorePos);
        warboss.setHealth(warboss.getMaxHealth());

        serverLevel.addFreshEntity(warboss);

        this.warbossSpawned = true;
        setChanged();

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel,
                this.targetCorePos,
                Component.translatable("msg.firstcrusade.bcast.warboss_risen", this.clan.getDisplayName())
        );
    }

    private static final int OVERRUN_MIN_IMPERIALS = 3;

    /**
     * True (and razes the camp) when an Imperial force has taken it by weight of numbers.
     *
     * <h2>Not while a player-started raid is running</h2>
     *
     * {@link #OVERRUN_MIN_IMPERIALS} exists so a lone wandering Guardsman cannot walk a camp down.
     * That is the right rule for an unattended camp and exactly the wrong one for a raid the player
     * called: a commander who fights alone would be told the camp cannot fall because they are only
     * one Imperial. So during an assault this check stands aside entirely and
     * {@code ImperialAssaultManager} decides — its rule is "the defenders are dead", which a single
     * player can satisfy.
     */
    private boolean checkOverrun(ServerLevel serverLevel, BlockPos pos) {
        if (this.underAssault) {
            return false;
        }

        int imperials = countFaction(serverLevel, pos, FirstCrusadeFaction.IMPERIUM);

        if (imperials < OVERRUN_MIN_IMPERIALS) {
            return false;
        }

        int orks = countFaction(serverLevel, pos, FirstCrusadeFaction.ORKS);
        if (imperials <= orks + 1) {
            return false;
        }

        razeCamp(serverLevel, pos);
        return true;
    }

    /** Removes the camp from the world and the war map, and tips the war toward the Imperium. */
    public void razeCamp(ServerLevel serverLevel, BlockPos pos) {
        serverLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        WorldWarMapData.get(serverLevel).removeCamp(pos);

        OrkRaidManager.notifyNearbyPlayers(
                serverLevel, pos, Component.translatable("msg.firstcrusade.bcast.camp_razed_imperial"));
        WarDominionManager.shift(serverLevel, 6);
    }

    // ===== player-started raid =====

    /**
     * Whether a player's raid currently owns this camp.
     *
     * <p>Persisted, because the raid record is persisted: a restart mid-assault must find the camp
     * still marked, or the camp's own overrun rule would quietly take over a fight the assault
     * manager thinks it is running.
     */
    public boolean isUnderAssault() {
        return this.underAssault;
    }

    public void setUnderAssault(boolean underAssault) {
        this.underAssault = underAssault;
        setChanged();
    }

    /**
     * The Orks that count as this camp's defenders: alive, tagged to this camp, inside its ring.
     *
     * <p>Tagged to <i>this</i> camp is the important half. A Grot wandering past from a neighbouring
     * camp, or an Ork the player spawned themselves, is not a defender and must not keep a raid
     * alive — nor may a marching war party that has already left count as still holding the ground.
     */
    public int countLivingDefenders(ServerLevel serverLevel) {
        BlockPos pos = this.worldPosition;

        return serverLevel.getEntitiesOfClass(
                Mob.class,
                garrisonBox(pos),
                mob -> mob.isAlive()
                        && isCampOrk(mob, pos)
                        && FirstCrusadeFactionManager.getFaction(mob) == FirstCrusadeFaction.ORKS
        ).size();
    }

    /** The camp's living defenders, so a raid can point its soldiers at them. */
    public List<Mob> listLivingDefenders(ServerLevel serverLevel) {
        BlockPos pos = this.worldPosition;

        return serverLevel.getEntitiesOfClass(
                Mob.class,
                garrisonBox(pos),
                mob -> mob.isAlive()
                        && isCampOrk(mob, pos)
                        && FirstCrusadeFactionManager.getFaction(mob) == FirstCrusadeFaction.ORKS
        );
    }

    /** Turns every defender onto whoever just declared the raid. */
    public void alertDefenders(ServerLevel serverLevel, net.minecraft.world.entity.LivingEntity threat) {
        for (Mob defender : listLivingDefenders(serverLevel)) {
            if (FirstCrusadeFactionManager.canAttack(defender, threat)) {
                defender.setTarget(threat);
            }
        }
    }

    // Counts living Orks (any kind) around a point — used to cap the swarm besieging a city.
    private int countNearbyOrks(ServerLevel serverLevel, BlockPos around) {
        AABB box = new AABB(
                around.getX() - WAR_FIELD_RADIUS, around.getY() - 48, around.getZ() - WAR_FIELD_RADIUS,
                around.getX() + WAR_FIELD_RADIUS, around.getY() + 48, around.getZ() + WAR_FIELD_RADIUS);

        return serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, box,
                e -> e.isAlive() && FirstCrusadeFactionManager.getFaction(e) == FirstCrusadeFaction.ORKS
        ).size();
    }

    private int countFaction(ServerLevel serverLevel, BlockPos pos, FirstCrusadeFaction faction) {
        return serverLevel.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                garrisonBox(pos),
                e -> e.isAlive() && FirstCrusadeFactionManager.getFaction(e) == faction
        ).size();
    }

    // Counts living units of one kind (Boyz, Grots, ...) belonging to this camp.
    private int countCampOfType(ServerLevel serverLevel, BlockPos pos, EntityType<?> type) {
        return serverLevel.getEntitiesOfClass(
                Mob.class,
                garrisonBox(pos),
                mob -> mob.isAlive() && mob.getType() == type && isCampOrk(mob, pos)
        ).size();
    }

    // Raises one camp unit (Boy or Grot), tags it to this camp, and stands it near the heart.
    private boolean spawnCampUnit(ServerLevel serverLevel, BlockPos pos, EntityType<? extends Mob> type) {
        Mob ork = type.create(serverLevel);

        if (ork == null) {
            return false;
        }

        BlockPos spawnPos = groundPos(serverLevel, pos.offset(serverLevel.random.nextInt(9) - 4, 0, serverLevel.random.nextInt(9) - 4));

        ork.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F,
                0.0F
        );

        this.clan.applyTo(ork);
        markAsCampOrk(ork, pos);
        ork.setPersistenceRequired();
        serverLevel.addFreshEntity(ork);
        return true;
    }

    private static void markAsCampOrk(Mob mob, BlockPos campPos) {
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(CAMP_ORK_TAG, true);
        data.putLong(CAMP_POS_TAG, campPos.asLong());
    }

    private static boolean isCampOrk(Mob mob, BlockPos campPos) {
        CompoundTag data = mob.getPersistentData();
        return data.getBoolean(CAMP_ORK_TAG) && data.getLong(CAMP_POS_TAG) == campPos.asLong();
    }

    private BlockPos groundPos(ServerLevel serverLevel, BlockPos around) {
        return serverLevel.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around);
    }

    private AABB garrisonBox(BlockPos pos) {
        return new AABB(
                pos.getX() - GARRISON_RADIUS,
                pos.getY() - 32,
                pos.getZ() - GARRISON_RADIUS,
                pos.getX() + GARRISON_RADIUS,
                pos.getY() + 48,
                pos.getZ() + GARRISON_RADIUS
        );
    }

    // ===== Ork Core GUI (simple panel) =====

    public int getCampLevel() {
        return this.campLevel;
    }

    public int getClanOrdinal() {
        return this.clan.ordinal();
    }

    public int getLootValue() {
        return this.loot;
    }

    public int getLootCapValue() {
        return LOOT_CAP;
    }

    public int getLootPitCost() {
        return LOOT_PIT_COST;
    }

    public int getCachedGrots() {
        return this.cachedGrots;
    }

    public int getCachedBoyz() {
        return this.cachedBoyz;
    }

    public int getCachedLootPits() {
        return this.cachedLootPits;
    }

    // Opens the Ork city's command panel (the Ork mirror of the Imperial Command Core screen).
    public void openOrkInterface(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }

        net.minecraftforge.network.NetworkHooks.openScreen(
                serverPlayer,
                new net.minecraft.world.MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("block.firstcrusade.ork_camp");
                    }

                    @Override
                    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                            int containerId, net.minecraft.world.entity.player.Inventory inventory,
                            net.minecraft.world.entity.player.Player p) {
                        return new OrkCampMenu(containerId, inventory, OrkCampBlockEntity.this);
                    }
                },
                this.worldPosition
        );
    }

    // The boss orders a new Loot Pit raised: it spends the city's loot and a Grot crew digs it in at a
    // free spot within the city. The pit then adds to the loot economy (see produceLoot).
    public void buildLootPit(net.minecraft.world.entity.player.Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // One Loot Pit per city — it levels up with the city instead of multiplying.
        if (countLootPits(serverLevel, this.worldPosition) >= 1) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.ork.loot_pit_exists"), true);
            return;
        }

        if (this.loot < LOOT_PIT_COST) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.ork.no_loot"), true);
            return;
        }

        BlockPos spot = findFreeBuildSpot(serverLevel, this.worldPosition);

        if (spot == null) {
            player.displayClientMessage(Component.translatable("msg.firstcrusade.ork.no_space"), true);
            return;
        }

        serverLevel.setBlock(spot, FCRegistry.ORK_LOOT_PIT.get().defaultBlockState(), 3);
        this.loot -= LOOT_PIT_COST;
        this.cachedLootPits++;
        setChanged();

        player.displayClientMessage(Component.translatable("msg.firstcrusade.ork.built_loot_pit"), true);
    }

    // Finds an empty surface spot in a ring around the heart to drop a new Ork structure on.
    private BlockPos findFreeBuildSpot(ServerLevel serverLevel, BlockPos center) {
        for (int radius = 6; radius <= BUILD_SCAN_RADIUS; radius += 3) {
            for (int angle = 0; angle < 360; angle += 30) {
                int x = center.getX() + (int) Math.round(Math.cos(Math.toRadians(angle)) * radius);
                int z = center.getZ() + (int) Math.round(Math.sin(Math.toRadians(angle)) * radius);

                BlockPos surface = groundPos(serverLevel, new BlockPos(x, center.getY(), z));

                if (serverLevel.isEmptyBlock(surface)
                        && serverLevel.isEmptyBlock(surface.above())
                        && !serverLevel.getBlockState(surface.below()).isAir()) {
                    return surface;
                }
            }
        }

        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt("CampLevel", this.campLevel);
        tag.putInt("Growth", this.growth);
        tag.putInt("Loot", this.loot);
        tag.putBoolean("Founded", this.founded);
        tag.putInt("GrowthCooldown", this.growthCooldown);
        tag.putInt("Waaagh", this.waaagh);
        tag.putInt("WarPartiesLaunched", this.warPartiesLaunched);
        tag.putBoolean("WarbossSpawned", this.warbossSpawned);
        tag.putBoolean("HasSpread", this.hasSpread);
        tag.putBoolean("UnderAssault", this.underAssault);
        tag.putInt("SporeRadius", this.sporeRadius);
        tag.putString("Clan", this.clan.name());

        if (this.targetCorePos != null) {
            tag.putLong("TargetCorePos", this.targetCorePos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        this.campLevel = Math.max(1, tag.getInt("CampLevel"));
        this.growth = tag.getInt("Growth");
        this.loot = tag.getInt("Loot");
        this.founded = tag.getBoolean("Founded");
        this.growthCooldown = tag.getInt("GrowthCooldown");
        this.waaagh = tag.getInt("Waaagh");
        this.warPartiesLaunched = tag.getInt("WarPartiesLaunched");
        this.warbossSpawned = tag.getBoolean("WarbossSpawned");
        this.hasSpread = tag.getBoolean("HasSpread");
        this.underAssault = tag.getBoolean("UnderAssault");
        // "CorruptionRadius" is the old key, kept so camps saved before the sculk system was
        // removed keep the reach they had grown.
        this.sporeRadius = tag.contains("SporeRadius")
                ? tag.getInt("SporeRadius")
                : tag.getInt("CorruptionRadius");
        this.clan = OrkClan.fromName(tag.getString("Clan"));

        if (tag.contains("TargetCorePos")) {
            this.targetCorePos = BlockPos.of(tag.getLong("TargetCorePos"));
        } else {
            this.targetCorePos = null;
        }
    }
}
