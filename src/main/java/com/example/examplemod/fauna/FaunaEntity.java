package com.example.examplemod.fauna;

import javax.annotation.Nullable;

import com.example.examplemod.animal.FCAnimalEntity;
import com.example.examplemod.fauna.effect.FaunaVisualEffects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A base da fauna do First Crusade: o que "ser um bicho com habilidade especial" custa, escrito uma
 * vez.
 *
 * <p>Herda de {@link FCAnimalEntity} de proposito, inclusive nos predadores. A base da Fase E e quem
 * carrega o teto de populacao e o alarme que se espalha por evento em vez de por varredura — um
 * Catachan Devil precisa das duas coisas tanto quanto um Grox precisa. Ser {@code Animal} nao
 * significa ser passivo; significa entrar na categoria de spawn que tem limite.
 *
 * <h2>Habilidade especial e uma maquina de estados de tres fases</h2>
 *
 * Preparacao, ativa, descanso — ver {@link FaunaAbility}. Uma habilidade por vez, sempre decidida no
 * servidor, e o cliente sabe da existencia dela por dois caminhos: a animacao (disparada por
 * {@code triggerAnim}, que o GeckoLib sincroniza) e as particulas (pacote do servidor). Nenhuma
 * decisao de dano acontece no cliente.
 *
 * <h2>A regra de proximidade, que e o que torna isto barato</h2>
 *
 * {@link #abilitiesAwake()} e a porta de todo comportamento especial: nenhum predador de emboscada
 * executa logica de emboscada sem alvo por perto. O briefing pede 20-40 blocos; o numero fica em
 * {@link #ABILITY_RADIUS}, e a consulta e a lista de jogadores do nivel (curta, sem alocar iterador
 * de AABB) e nao uma busca de entidades. Bicho em chunk carregado sem ninguem em volta paga um
 * comparativo de inteiro por tick e mais nada.
 *
 * <h2>Persistencia</h2>
 *
 * {@link #isFromStructure()} marca o bicho que nasceu numa toca, num rancho ou num ninho. Esse nao
 * despawna e nao entra na conta de "fauna comum" — o Ambull do Ambull Burrow tem de estar la quando
 * o jogador voltar, senao a estrutura conta uma historia que o mundo desmente.
 */
public abstract class FaunaEntity extends FCAnimalEntity implements GeoEntity {

    /**
     * Distancia em que a fauna acorda para habilidades especiais, em blocos.
     *
     * <p>O teto do briefing (20-40). Trinta e dois nao e gosto: e o mesmo alcance em que o vanilla
     * para de entregar pacote de particula, entao acima disso o bicho gastaria logica produzindo
     * efeito que ninguem receberia.
     */
    public static final double ABILITY_RADIUS = 32.0D;

    /** Enterrado: o cliente precisa saber para nao desenhar o modelo. */
    private static final EntityDataAccessor<Boolean> BURROWED =
            SynchedEntityData.defineId(FaunaEntity.class, EntityDataSerializers.BOOLEAN);

    /** A habilidade em curso, ou null. Runtime — nao sobrevive ao reload, de proposito. */
    @Nullable
    private FaunaAbility ability;

    /** Ticks decorridos desde o inicio da habilidade em curso. */
    private int abilityTicks;

    /** Ticks até poder usar outra habilidade. Este SIM é salvo. */
    private int abilityCooldown;

    /** Nasceu de uma estrutura da fauna. Salvo: decide despawn e pertencimento. */
    private boolean fromStructure;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected FaunaEntity(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BURROWED, false);
    }

    // ============================================================ proximidade

    /**
     * Se vale a pena rodar logica especial agora.
     *
     * <p>Um jogador dentro de {@link #ABILITY_RADIUS} e a unica condicao. Nao inclui "tem alvo",
     * porque exibicao territorial e emboscada acontecem justamente antes de haver alvo.
     */
    protected boolean abilitiesAwake() {
        return this.level().getNearestPlayer(this, ABILITY_RADIUS) != null;
    }

    /** O jogador mais proximo dentro do raio de habilidade, ou null. */
    @Nullable
    protected Player nearestWatcher() {
        return this.level().getNearestPlayer(this, ABILITY_RADIUS);
    }

    // ============================================================ habilidades

    /** Se a habilidade dada pode comecar agora: nada em curso e o descanso terminou. */
    public boolean canUseAbility() {
        return this.ability == null && this.abilityCooldown <= 0 && !this.isBurrowed();
    }

    /** True enquanto qualquer habilidade estiver em curso. */
    public boolean isUsingAbility() {
        return this.ability != null;
    }

    /** A habilidade em curso, ou null. */
    @Nullable
    public FaunaAbility currentAbility() {
        return this.ability;
    }

    /** True quando a habilidade em curso passou da preparacao e esta na fase que causa efeito. */
    public boolean isAbilityActive() {
        return this.ability != null && this.abilityTicks >= this.ability.windup();
    }

    /** Ticks decorridos na habilidade em curso. */
    public int abilityTicks() {
        return this.abilityTicks;
    }

    /**
     * Comeca uma habilidade e dispara a animacao dela.
     *
     * <p>So no servidor: e o servidor que decide, e {@code triggerAnim} viaja daqui para os clientes
     * que enxergam a entidade. Chamar no cliente tocaria a animacao numa maquina e nao na outra.
     */
    public boolean startAbility(FaunaAbility candidate) {
        if (this.level().isClientSide || !canUseAbility()) {
            return false;
        }

        this.ability = candidate;
        this.abilityTicks = 0;
        this.triggerAnim("ability", candidate.animation());
        onAbilityStart(candidate);
        return true;
    }

    /** Interrompe a habilidade em curso e arma o descanso dela. */
    protected void cancelAbility() {
        if (this.ability == null) {
            return;
        }

        FaunaAbility finished = this.ability;
        this.ability = null;
        this.abilityTicks = 0;
        this.abilityCooldown = finished.cooldown();
        onAbilityEnd(finished);
    }

    /** Gancho: a habilidade acabou de comecar (a preparacao). */
    protected void onAbilityStart(FaunaAbility started) {
    }

    /** Gancho: o primeiro tick da fase ativa — e aqui que golpe e dano pertencem. */
    protected void onAbilityStrike(FaunaAbility active) {
    }

    /** Gancho: cada tick da fase ativa, para efeito continuo (nuvem toxica, corrida). */
    protected void onAbilityTick(FaunaAbility active, int tickInPhase) {
    }

    /** Gancho: a habilidade terminou ou foi interrompida. */
    protected void onAbilityEnd(FaunaAbility finished) {
    }

    /** Gancho: uma decisao por tick, so quando ha alguem por perto. Escolha de habilidade vai aqui. */
    protected void awakeServerTick() {
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.abilityCooldown > 0) {
            this.abilityCooldown--;
        }

        if (this.ability != null) {
            tickAbility();
            return;                     // habilidade em curso manda; nada mais decide neste tick
        }

        if (abilitiesAwake()) {
            awakeServerTick();
        }
    }

    private void tickAbility() {
        FaunaAbility running = this.ability;
        int windup = running.windup();

        if (this.abilityTicks == windup) {
            onAbilityStrike(running);
        } else if (this.abilityTicks > windup) {
            onAbilityTick(running, this.abilityTicks - windup);
        }

        this.abilityTicks++;

        if (this.abilityTicks >= running.duration()) {
            cancelAbility();
        }
    }

    /** Arma o descanso sem ter usado habilidade — para acoes que so gastam o tempo. */
    protected void setAbilityCooldown(int ticks) {
        this.abilityCooldown = Math.max(this.abilityCooldown, ticks);
    }

    // ============================================================ enterrado

    /** True enquanto o bicho esta sob o chao: invisivel, e sem colisao de ataque. */
    public boolean isBurrowed() {
        return this.entityData.get(BURROWED);
    }

    public void setBurrowed(boolean burrowed) {
        this.entityData.set(BURROWED, burrowed);
    }

    /**
     * Enterrado nao apanha e nao empurra.
     *
     * <p>Sem isto o jogador acerta um bicho que nao esta desenhado, o que le como bug e nao como
     * emboscada. E o {@code isInvulnerableTo} vanilla nao serve: ele tambem bloquearia dano de
     * ambiente que deveria continuar valendo.
     */
    @Override
    public boolean isPushable() {
        return !isBurrowed() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (isBurrowed()) {
            return;
        }
        super.pushEntities();
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isBurrowed() && super.canBeCollidedWith();
    }

    // ============================================================ estruturas

    /** True se este bicho nasceu numa estrutura da fauna e pertence a ela. */
    public boolean isFromStructure() {
        return this.fromStructure;
    }

    /**
     * Marca o bicho como morador de uma estrutura: persistente, e nunca despawna.
     *
     * <p>Chamado pela feature que criou a estrutura, no momento da geracao do chunk.
     */
    public void markFromStructure() {
        this.fromStructure = true;
        this.setPersistenceRequired();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        // A fauna da Fase E ja nao despawnava; isto so torna a razao explicita para a fauna nova,
        // onde ha duas razoes diferentes para ficar.
        return false;
    }

    // ============================================================ efeitos

    /**
     * Poeira circular nos pes, do tamanho pedido. Servidor.
     *
     * <p>Atalho para as habilidades: passa pelo orcamento de particula de servidor, entao o dono
     * consegue baixar a fauna inteira num dial sem tocar em nenhuma especie.
     */
    protected void dustRing(double radius, int density) {
        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.dustRing(server, this, radius, density);
        }
    }

    /** Tremor de tela para quem estiver perto o suficiente para sentir. Servidor. */
    protected void tremor(float magnitude, int ticks, double radius) {
        if (this.level() instanceof ServerLevel server) {
            FaunaVisualEffects.tremor(server, this.getX(), this.getY(), this.getZ(),
                    magnitude, ticks, radius);
        }
    }

    // ============================================================ combate

    /**
     * Empurrao numa direcao horizontal, com a mesma conta que o vanilla usa no escudo.
     *
     * <p>Aplicado no servidor e propagado pelo movimento normal da entidade — mexer em {@code
     * setDeltaMovement} de um jogador exige {@code hurtMarked}, senao o servidor sobrescreve no
     * proximo tick de posicao.
     */
    protected static void knockBack(Entity victim, Entity source, double strength, double lift) {
        double dx = victim.getX() - source.getX();
        double dz = victim.getZ() - source.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length < 1.0E-4D) {
            // Exatamente em cima um do outro: sem direcao para empurrar, e a divisao abaixo daria
            // infinito. Um vetor sorteado resolve, e o caso e raro o bastante para nao importar
            // qual direcao sai.
            dx = victim.level().random.nextDouble() - 0.5D;
            dz = victim.level().random.nextDouble() - 0.5D;
            length = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4D);
        }

        victim.push(dx / length * strength, lift, dz / length * strength);
        victim.hurtMarked = true;
    }

    /** Alvos validos de habilidade em area: nada da propria especie, nada morto. */
    protected boolean isAreaTarget(LivingEntity candidate) {
        return candidate != this && candidate.isAlive()
                && !candidate.getType().equals(this.getType());
    }

    // ============================================================ save

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AbilityCooldown", this.abilityCooldown);
        tag.putBoolean("FromStructure", this.fromStructure);
        tag.putBoolean("Burrowed", isBurrowed());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.abilityCooldown = tag.getInt("AbilityCooldown");
        this.fromStructure = tag.getBoolean("FromStructure");
        setBurrowed(tag.getBoolean("Burrowed"));
    }

    // ============================================================ GeckoLib

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
