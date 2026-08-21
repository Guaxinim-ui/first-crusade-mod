package com.example.examplemod.fauna.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * A emboscada: enquanto enterrado, andar sob o chao ate o alvo e emergir colado nele.
 *
 * <h2>A regra de proximidade e o coracao desta classe</h2>
 *
 * Nada aqui roda a menos que haja um jogador dentro de {@link FaunaEntity#ABILITY_RADIUS}. Nao ha
 * varredura de entidades: {@code getNearestPlayer} percorre a lista de jogadores do nivel, que num
 * servidor tem meia duzia de elementos, e a chamada acontece a cada {@link #HUNT_INTERVAL} ticks e
 * nao por tick. Um Duneskuttler enterrado num chunk carregado sem ninguem em volta custa um
 * comparativo de inteiro por segundo — que era exatamente o requisito do briefing sobre "centenas de
 * animais ativos em chunks abandonados".
 *
 * <h2>Duas distancias, nao uma</h2>
 *
 * <ul>
 *   <li><b>Caca</b> ({@link FaunaEntity#ABILITY_RADIUS}) — o bicho comeca a se deslocar sob o chao.
 *       Ainda invisivel; o jogador ve poeira andando na direcao dele, o que e a pista ambiental.</li>
 *   <li><b>Emergencia</b> ({@link #emergeDistance}) — perto o bastante para a explosao de areia
 *       valer. Emergir a vinte blocos daria ao jogador tempo de sobra e desperdicaria o susto.</li>
 * </ul>
 */
public class FaunaAmbushGoal extends Goal {

    /** Cadencia da decisao de caca, em ticks. */
    private static final int HUNT_INTERVAL = 20;

    private final FaunaEntity animal;
    private final FaunaAbility emerge;
    private final double emergeDistance;
    private final double stalkSpeed;

    @Nullable
    private Player prey;

    private int nextHunt;

    public FaunaAmbushGoal(FaunaEntity animal, FaunaAbility emerge, double emergeDistance,
                           double stalkSpeed) {
        this.animal = animal;
        this.emerge = emerge;
        this.emergeDistance = emergeDistance;
        this.stalkSpeed = stalkSpeed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.animal.isBurrowed() || this.animal.isUsingAbility()) {
            return false;
        }

        if (this.animal.tickCount < this.nextHunt) {
            return false;
        }
        this.nextHunt = this.animal.tickCount + HUNT_INTERVAL;

        Player found = this.animal.level().getNearestPlayer(this.animal, FaunaEntity.ABILITY_RADIUS);
        if (found == null || !found.isAlive() || found.isCreative() || found.isSpectator()) {
            return false;
        }

        this.prey = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        Player target = this.prey;
        if (target == null || !target.isAlive() || !this.animal.isBurrowed()) {
            return false;
        }

        return this.animal.distanceToSqr(target)
                <= FaunaEntity.ABILITY_RADIUS * FaunaEntity.ABILITY_RADIUS;
    }

    @Override
    public void stop() {
        this.prey = null;
        this.animal.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player target = this.prey;
        if (target == null) {
            return;
        }

        double distanceSq = this.animal.distanceToSqr(target);

        if (distanceSq <= this.emergeDistance * this.emergeDistance) {
            this.animal.getNavigation().stop();
            this.animal.setTarget(target);
            this.animal.startAbility(this.emerge);
            return;
        }

        // O deslocamento sob o chao usa a navegacao normal. O "sob o chao" e visual: o modelo esta
        // escondido e a poeira sai dos pes. Um segundo sistema de movimento subterraneo custaria um
        // pathfinder proprio para ganhar nada que a camera consiga ver.
        if (this.animal.getNavigation().isDone()) {
            this.animal.getNavigation().moveTo(target, this.stalkSpeed);
        }

        if (this.animal.level() instanceof ServerLevel server
                && this.animal.tickCount % 4 == 0) {
            com.example.examplemod.fauna.effect.FaunaVisualEffects.burrowTrail(server, this.animal);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
