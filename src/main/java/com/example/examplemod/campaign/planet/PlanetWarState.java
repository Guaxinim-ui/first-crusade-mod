package com.example.examplemod.campaign.planet;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.example.examplemod.campaign.CampaignFront;
import com.example.examplemod.campaign.sector.SectorType;
import com.example.examplemod.campaign.sector.StrategicSector;
import com.example.examplemod.campaign.war.WarFaction;
import com.example.examplemod.campaign.war.WarIntensity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The war on one front: who holds how much of it, how hard it is being fought, and what the Crusade
 * is trying to do there.
 *
 * <h2>Control is computed, never accumulated</h2>
 *
 * The percentages are recomputed from the front's sectors on every strategic pass. They are stored
 * only so a front nobody has loaded still has an answer for the War Table. This matters: an
 * accumulated score drifts — every source that nudges it has to be balanced against every other, and
 * a single mis-signed event slowly poisons the number until the map says the Imperium owns a planet
 * it lost. Deriving it means the map cannot disagree with the sectors, because it is the sectors.
 *
 * <p>This is the same correction the global {@code WarDominion} needs and now gets through
 * {@link com.example.examplemod.campaign.war.CrusadeScore}: one accumulated number for the whole
 * galaxy was both the wrong granularity and the wrong mechanism.
 *
 * <h2>Necron awakening</h2>
 *
 * {@link #necronAwakening} is carried by every front but only means anything on a tomb world. It is
 * a number and a set of thresholds, with nothing behind it yet: no Necron entity exists, and this
 * class deliberately does not spawn one. What it does is make the architecture the roadmap asks for
 * real — a tomb world already has an awakening level that rises, is persisted, is visible in the
 * debug commands and gates its own stages — so the faction can be dropped into a system that is
 * already keeping its state, instead of that state being invented alongside it.
 */
public class PlanetWarState {

    /** Control at or above this share makes a front that side's. */
    private static final int CONTROL_MAJORITY = 60;

    /** Control at or above this share, with no enemy seat left, conquers the front. */
    private static final int CONTROL_DECISIVE = 90;

    private final ResourceLocation frontId;

    private PlanetCampaignState state = PlanetCampaignState.AVAILABLE;
    private WarIntensity intensity = WarIntensity.DORMANT;

    private int imperialControl;
    private int orkControl;
    private int necronControl;
    private int contestedControl = 100;

    /** 0-100. Only a tomb world raises it; see the class note. */
    private int necronAwakening;

    /**
     * How much WAAAGH! this front's Ork camps have accumulated toward their next offensive.
     *
     * <p>Per front rather than per camp: a planet with eight camps should launch sooner and harder
     * than one with two, and that is a property of the planet, not of any one camp. It also keeps
     * the whole build-up to one integer instead of a state machine per settlement.
     */
    private int waaaghBuildUp;

    /** True once the front has warned about the offensive it is building. Reset on launch. */
    private boolean raidWarningSent;

    /** Where the front's sectors were laid out around. Zero until the front is activated. */
    private BlockPos anchor = BlockPos.ZERO;

    /** True once the sector layout has been materialised for this front. */
    private boolean sectorsLaidOut;

    /**
     * True once the Crusade has ever held the majority here. It is what separates a world that was
     * {@link PlanetCampaignState#LOST} from one that was always the enemy's.
     */
    private boolean everImperialHeld;

    /** The current objective's verb key, or empty for a front with nothing asked of it. */
    private String objectiveKey = "";

    /** What that verb is aimed at, or null for an objective with no target ("hold the line"). */
    @Nullable
    private SectorType objectiveTarget;

    /** Plain text of the last thing worth reporting here. Shown by {@code /fcstrategy planet status}. */
    private String lastEvent = "";

    private long lastEventTime;

    public PlanetWarState(ResourceLocation frontId) {
        this.frontId = frontId;
    }

    // ====================================================================================
    // Reading
    // ====================================================================================

    public ResourceLocation frontId() {
        return this.frontId;
    }

    public PlanetCampaignState state() {
        return this.state;
    }

    public WarIntensity intensity() {
        return this.intensity;
    }

    public int imperialControl() {
        return this.imperialControl;
    }

    public int orkControl() {
        return this.orkControl;
    }

    public int necronControl() {
        return this.necronControl;
    }

    public int contestedControl() {
        return this.contestedControl;
    }

    /** The combined share held by powers at war with the Imperium. */
    public int enemyControl() {
        return this.orkControl + this.necronControl;
    }

    public int necronAwakening() {
        return this.necronAwakening;
    }

    public BlockPos anchor() {
        return this.anchor;
    }

    public boolean sectorsLaidOut() {
        return this.sectorsLaidOut;
    }

    public String objectiveKey() {
        return this.objectiveKey;
    }

    public String lastEvent() {
        return this.lastEvent;
    }

    public long lastEventTime() {
        return this.lastEventTime;
    }

    // ====================================================================================
    // Writing
    // ====================================================================================

    public void setAnchor(BlockPos value) {
        this.anchor = value == null ? BlockPos.ZERO : value.immutable();
    }

    public void markSectorsLaidOut() {
        this.sectorsLaidOut = true;
    }

    public void setObjectiveKey(String key) {
        this.objectiveKey = key == null ? "" : key;
    }

    public void setObjectiveTarget(@Nullable SectorType target) {
        this.objectiveTarget = target;
    }

    @Nullable
    public SectorType objectiveTarget() {
        return this.objectiveTarget;
    }

    public int waaaghBuildUp() {
        return this.waaaghBuildUp;
    }

    /**
     * Adds to the front's WAAAGH! pool.
     *
     * @param ceiling hard cap, so a planet nobody visits for a week cannot bank an offensive that
     *                arrives as an unanswerable wall the moment somebody lands on it
     * @return the pool after the addition
     */
    public int addWaaaghBuildUp(int amount, int ceiling) {
        this.waaaghBuildUp = Math.min(Math.max(0, ceiling), this.waaaghBuildUp + Math.max(0, amount));
        return this.waaaghBuildUp;
    }

    /** Spends the pool on a launch, and re-arms the warning for the next build-up. */
    public void spendWaaaghBuildUp(int amount) {
        this.waaaghBuildUp = Math.max(0, this.waaaghBuildUp - Math.max(0, amount));
        this.raidWarningSent = false;
    }

    /**
     * Records that the front should warn about an offensive, and answers whether this is the first
     * time it has been asked since the last launch.
     *
     * <p>The latch is why this returns a boolean rather than the caller testing a threshold: a
     * threshold is true on every pass once crossed, so warning off it directly would broadcast the
     * same line every ten seconds until the raid actually arrived.
     */
    public boolean notePreparationWarning(boolean shouldWarn) {
        if (!shouldWarn || this.raidWarningSent) {
            return false;
        }

        this.raidWarningSent = true;
        return true;
    }

    public void recordEvent(String text, long gameTime) {
        this.lastEvent = text == null ? "" : text;
        this.lastEventTime = gameTime;
    }

    /**
     * @return the previous state if this changed it, otherwise null
     */
    public PlanetCampaignState setState(PlanetCampaignState next) {
        if (next == null || next == this.state) {
            return null;
        }

        PlanetCampaignState previous = this.state;
        this.state = next;
        return previous;
    }

    /**
     * Raises the tomb world's awakening and reports every threshold it crossed.
     *
     * <p>The stages are returned rather than acted on, because what happens at each one is not this
     * class's business — and, today, nothing happens at all. See the class note.
     */
    public List<NecronStage> raiseNecronAwakening(int amount) {
        if (amount <= 0) {
            return List.of();
        }

        int before = this.necronAwakening;
        this.necronAwakening = Math.min(100, this.necronAwakening + amount);

        List<NecronStage> crossed = new ArrayList<>();

        for (NecronStage stage : NecronStage.values()) {
            if (before < stage.threshold() && this.necronAwakening >= stage.threshold()) {
                crossed.add(stage);
            }
        }

        return crossed;
    }

    /** The stages a tomb world wakes through. Nothing spawns them yet — architecture only. */
    public enum NecronStage {
        SILENT(0),
        SCARABS(25),
        WARRIORS(50),
        TOMB_DEFENCES(75),
        OVERLORD(100);

        private final int threshold;

        NecronStage(int threshold) {
            this.threshold = threshold;
        }

        public int threshold() {
            return this.threshold;
        }
    }

    // ====================================================================================
    // Recomputation
    // ====================================================================================

    /**
     * Rebuilds the control shares and the intensity from this front's sectors.
     *
     * <p>A pure function of the sectors it is handed, so a front is never "slightly wrong" — it is
     * either recomputed or it is showing what it last recomputed to.
     *
     * @return the previous campaign state if the recomputation changed it, otherwise null
     */
    public PlanetCampaignState recompute(List<StrategicSector> sectors, CampaignFront front) {
        if (sectors.isEmpty()) {
            this.imperialControl = 0;
            this.orkControl = 0;
            this.necronControl = 0;
            this.contestedControl = 100;
            this.intensity = WarIntensity.DORMANT;
            return null;
        }

        int total = 0;
        int imperial = 0;
        int ork = 0;
        int necron = 0;
        int disputed = 0;

        boolean enemySeatStanding = false;

        for (StrategicSector sector : sectors) {
            int weight = Math.max(1, sector.importance());
            total += weight;

            // A disputed sector counts toward nobody. That is the "contested" slice, and it is why
            // the three shares do not have to add to a hundred on their own.
            if (sector.isDisputed()) {
                disputed++;
                continue;
            }

            switch (sector.owner()) {
                case IMPERIUM -> imperial += weight;
                case ORKS -> ork += weight;
                case NECRONS -> necron += weight;
                default -> {
                    // Neutral ground belongs to the contested remainder.
                }
            }

            if (sector.type().isEnemySeat() && sector.owner().isEnemyOfImperium()) {
                enemySeatStanding = true;
            }
        }

        this.imperialControl = percent(imperial, total);
        this.orkControl = percent(ork, total);
        this.necronControl = percent(necron, total);

        // Whatever the three shares did not claim. Computed as the remainder rather than counted
        // separately so the four always add to exactly 100 and the War Table never shows 99%.
        this.contestedControl = Math.max(0,
                100 - this.imperialControl - this.orkControl - this.necronControl);

        double disputedFraction = (double) disputed / sectors.size();
        double balance = balanceOf(this.imperialControl, enemyControl());
        this.intensity = WarIntensity.of(disputedFraction, balance);

        // Recorded before the state is derived, so a front that reaches a majority this very pass
        // is already eligible to be "lost" on a later one.
        if (this.imperialControl >= CONTROL_MAJORITY) {
            this.everImperialHeld = true;
        }

        return setState(deriveState(enemySeatStanding, front));
    }

    /**
     * How evenly two shares are split: 1.0 when they are equal, 0.0 when one holds everything.
     * Two sides that both hold nothing (an entirely neutral front) is not a balanced war, it is an
     * empty one, so it answers 0.
     */
    private static double balanceOf(int imperial, int enemy) {
        int sum = imperial + enemy;
        return sum <= 0 ? 0.0D : 1.0D - (double) Math.abs(imperial - enemy) / sum;
    }

    private PlanetCampaignState deriveState(boolean enemySeatStanding, CampaignFront front) {
        if (!front.unlocked()) {
            return PlanetCampaignState.LOCKED;
        }

        if (!this.sectorsLaidOut) {
            return PlanetCampaignState.AVAILABLE;
        }

        int enemy = enemyControl();

        // Conquest asks for two things, and the seat is the one that matters. Holding almost all of
        // Ork World while the Warboss is still in his stronghold is a stalemate, not a victory.
        if (this.imperialControl >= CONTROL_DECISIVE && !enemySeatStanding) {
            return PlanetCampaignState.CONQUERED;
        }

        // LOST means the Crusade held this world and does not any more. A world that was enemy
        // ground the first time anyone looked at it was never lost — it is simply theirs. Without
        // this distinction Ork World and the tomb world both report LOST the instant they are laid
        // out, before a shot has been fired on either, which reads as a defeat the player caused.
        if (enemy >= CONTROL_DECISIVE) {
            return this.everImperialHeld ? PlanetCampaignState.LOST : PlanetCampaignState.ENEMY_CONTROL;
        }

        if (this.imperialControl >= CONTROL_MAJORITY) {
            return PlanetCampaignState.IMPERIAL_CONTROL;
        }

        if (enemy >= CONTROL_MAJORITY) {
            return PlanetCampaignState.ENEMY_CONTROL;
        }

        // Both sides hold real ground: a front line exists.
        if (this.imperialControl > 0 && enemy > 0) {
            return PlanetCampaignState.CONTESTED;
        }

        return PlanetCampaignState.ACTIVE;
    }

    private static int percent(int part, int total) {
        return total <= 0 ? 0 : (int) Math.round(100.0D * part / total);
    }

    /**
     * The faction that would be dislodged by taking this front, for the objective line.
     * {@link WarFaction#NEUTRAL} when nobody hostile holds anything.
     */
    public WarFaction dominantEnemy() {
        if (this.necronControl > this.orkControl) {
            return WarFaction.NECRONS;
        }

        return this.orkControl > 0 ? WarFaction.ORKS : WarFaction.NEUTRAL;
    }

    /**
     * Picks the front's current objective: retake the most valuable enemy-held sector, or hold the
     * line when there is nothing to retake.
     *
     * <p>The verb and the target are stored separately, and the objective is assembled at display
     * time. Baking them together — {@code objective.capture.manufactorum} — would mean one
     * translation key per verb per sector type, seventy strings saying the same two things, and a
     * missing key every time a sector type is added.
     */
    public void refreshObjective(List<StrategicSector> sectors) {
        StrategicSector best = primaryTarget(sectors);

        if (best == null) {
            setObjectiveKey(this.imperialControl > 0 ? "objective.firstcrusade.hold" : "");
            setObjectiveTarget(null);
            return;
        }

        // The seat of enemy power reads as a different order from a factory, so it gets its own verb.
        setObjectiveKey(best.type().isEnemySeat()
                ? "objective.firstcrusade.destroy"
                : "objective.firstcrusade.capture");

        setObjectiveTarget(best.type());
    }

    /** The objective as it is shown: the verb with the target's name in it. */
    public Component objective() {
        if (this.objectiveKey.isEmpty()) {
            return Component.empty();
        }

        return this.objectiveTarget == null
                ? Component.translatable(this.objectiveKey)
                : Component.translatable(this.objectiveKey, this.objectiveTarget.displayName());
    }

    /** The most valuable sector an enemy still holds, or null. Used by the objective and the raids. */
    public static StrategicSector primaryTarget(List<StrategicSector> sectors) {
        StrategicSector best = null;

        for (StrategicSector sector : sectors) {
            if (!sector.owner().isEnemyOfImperium()) {
                continue;
            }

            if (best == null || sector.importance() > best.importance()) {
                best = sector;
            }
        }

        return best;
    }

    /** True when every seat of enemy power on this front has fallen. */
    public static boolean enemySeatsCleared(List<StrategicSector> sectors) {
        for (StrategicSector sector : sectors) {
            if (sector.type().isEnemySeat() && sector.owner().isEnemyOfImperium()) {
                return false;
            }
        }

        return true;
    }

    /** Whether this front has a sector of the given type still held by an enemy. */
    public static boolean hasEnemyHeld(List<StrategicSector> sectors, SectorType type) {
        for (StrategicSector sector : sectors) {
            if (sector.type() == type && sector.owner().isEnemyOfImperium()) {
                return true;
            }
        }

        return false;
    }

    // ====================================================================================
    // Persistence
    // ====================================================================================

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Front", this.frontId.toString());
        tag.putString("State", this.state.name());
        tag.putString("Intensity", this.intensity.name());
        tag.putInt("Imperial", this.imperialControl);
        tag.putInt("Ork", this.orkControl);
        tag.putInt("Necron", this.necronControl);
        tag.putInt("Contested", this.contestedControl);
        tag.putInt("Awakening", this.necronAwakening);
        tag.putInt("Waaagh", this.waaaghBuildUp);
        tag.putBoolean("RaidWarned", this.raidWarningSent);
        tag.putLong("Anchor", this.anchor.asLong());
        tag.putBoolean("LaidOut", this.sectorsLaidOut);
        tag.putBoolean("EverImperial", this.everImperialHeld);
        tag.putString("Objective", this.objectiveKey);
        tag.putString("ObjectiveTarget", this.objectiveTarget == null ? "" : this.objectiveTarget.name());
        tag.putString("LastEvent", this.lastEvent);
        tag.putLong("LastEventTime", this.lastEventTime);
        return tag;
    }

    /**
     * Reads a persisted front. Every field has a default, so a tag written by an older build — or one
     * missing a field entirely — loads as a front at the start of its campaign rather than throwing.
     */
    public static PlanetWarState load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Front"));

        if (id == null) {
            return null;
        }

        PlanetWarState state = new PlanetWarState(id);

        state.state = PlanetCampaignState.fromName(tag.getString("State"));
        state.intensity = WarIntensity.fromName(tag.getString("Intensity"));
        state.imperialControl = tag.getInt("Imperial");
        state.orkControl = tag.getInt("Ork");
        state.necronControl = tag.getInt("Necron");
        state.contestedControl = tag.contains("Contested") ? tag.getInt("Contested") : 100;
        state.necronAwakening = tag.getInt("Awakening");
        state.waaaghBuildUp = tag.getInt("Waaagh");
        state.raidWarningSent = tag.getBoolean("RaidWarned");
        state.anchor = BlockPos.of(tag.getLong("Anchor"));
        state.sectorsLaidOut = tag.getBoolean("LaidOut");

        // A tag written before this field existed: infer it from the state it was saved in, so a
        // world the Crusade already held does not forget that it did.
        state.everImperialHeld = tag.contains("EverImperial")
                ? tag.getBoolean("EverImperial")
                : state.state.isImperialHeld();

        state.objectiveKey = tag.getString("Objective");

        String target = tag.getString("ObjectiveTarget");
        state.objectiveTarget = target.isEmpty() ? null : SectorType.fromName(target);

        state.lastEvent = tag.getString("LastEvent");
        state.lastEventTime = tag.getLong("LastEventTime");

        return state;
    }
}
