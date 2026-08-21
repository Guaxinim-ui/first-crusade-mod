package com.example.examplemod.fauna.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * O aviso territorial: olha, rosna, e so ataca se o intruso continuar chegando.
 *
 * <h2>Um animal territorial nao e um animal hostil</h2>
 *
 * A diferenca esta inteira nesta goal. Um mob hostil ve o jogador e vai; um animal territorial da uma
 * chance, e a chance e o que faz o encontro ter uma decisao dentro dele. O jogador que recua nao
 * briga; o que insiste briga tendo escolhido brigar.
 *
 * <p>A implementacao e a mais barata possivel: um aviso, e depois uma comparacao entre a distancia de
 * agora e a distancia de quando o aviso saiu. Se encurtou mais que {@link #approachMargin}, o intruso
 * decidiu. Nao ha memoria de rota, nao ha vetor de aproximacao, nao ha timer por jogador.
 *
 * <h2>Nao persegue por centenas de blocos</h2>
 *
 * O briefing pede isso explicitamente. Quem cumpre nao e esta goal: e o {@code FOLLOW_RANGE} baixo
 * das especies territoriais mais {@code LightweightReturnToBaseGoal} onde ha toca. Vale escrever aqui
 * porque a tentacao natural e resolver perseguicao dentro da goal de agressao, e ali ela sempre vira
 * um segundo pathfinder.
 */
public class FaunaTerritorialGoal extends Goal {

    private static final int CHECK_INTERVAL = 20;

    private final FaunaEntity animal;
    private final FaunaAbility warning;
    private final double warnDistance;
    private final double approachMargin;

    @Nullable
    private Player intruder;

    private double distanceAtWarning;
    private int warnedAt = -1;
    private int nextCheck;

    /**
     * @param warnDistance   distancia em que o animal considera o territorio invadido
     * @param approachMargin quantos blocos de aproximacao depois do aviso contam como decisao
     */
    public FaunaTerritorialGoal(FaunaEntity animal, FaunaAbility warning,
                                double warnDistance, double approachMargin) {
        this.animal = animal;
        this.warning = warning;
        this.warnDistance = warnDistance;
        this.approachMargin = approachMargin;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.animal.getTarget() != null || this.animal.isUsingAbility()) {
            return false;
        }

        if (this.animal.tickCount < this.nextCheck) {
            return false;
        }
        this.nextCheck = this.animal.tickCount + CHECK_INTERVAL;

        if (!this.animal.canUseAbility()) {
            return false;
        }

        Player found = this.animal.level().getNearestPlayer(this.animal, this.warnDistance);
        if (found == null || found.isCreative() || found.isSpectator()) {
            return false;
        }

        this.intruder = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        Player target = this.intruder;
        if (target == null || !target.isAlive() || this.animal.getTarget() != null) {
            return false;
        }

        // Enquanto o aviso esta tocando, e enquanto o intruso segue no territorio depois dele.
        return this.animal.isUsingAbility()
                || this.animal.distanceTo(target) <= this.warnDistance + 4.0D;
    }

    @Override
    public void start() {
        this.animal.getNavigation().stop();
        this.animal.startAbility(this.warning);

        Player target = this.intruder;
        if (target != null) {
            this.distanceAtWarning = this.animal.distanceTo(target);
            this.warnedAt = this.animal.tickCount;
        }
    }

    @Override
    public void stop() {
        this.intruder = null;
        this.warnedAt = -1;
    }

    @Override
    public void tick() {
        Player target = this.intruder;
        if (target == null) {
            return;
        }

        this.animal.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.warnedAt < 0 || this.animal.isUsingAbility()) {
            return;
        }

        if (this.distanceAtWarning - this.animal.distanceTo(target) >= this.approachMargin) {
            this.animal.setTarget(target);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
