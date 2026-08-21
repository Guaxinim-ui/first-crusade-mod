package com.example.examplemod.fauna.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.example.examplemod.fauna.FaunaAbility;
import com.example.examplemod.fauna.FaunaEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * O jorro toxico do Catachan Barking Toad, em duas habilidades encadeadas.
 *
 * <h2>A carga e a jogabilidade inteira</h2>
 *
 * O sapo nao caca. Ele fica onde esta, e quem se aproxima demais recebe alguns segundos de aviso —
 * garganta inflando, particula verde, som grave — antes da descarga. Esses segundos SAO a habilidade:
 * sem eles seria dano em area sem contra-jogo, e o encontro passaria de "criatura extremamente
 * perigosa" para "armadilha injusta". Com eles, morrer para um Barking Toad e sempre ter ignorado um
 * aviso.
 *
 * <p>A goal encadeia {@code toxic_burst_charge} e {@code toxic_burst} como duas habilidades separadas
 * em vez de uma so com preparacao longa. A razao e concreta: assim a carga pode ser <b>abortada</b> se
 * o jogador recuar durante ela, e recuar tem de funcionar, senao o aviso e decorativo.
 */
public class FaunaToxicBurstGoal extends Goal {

    private static final int CHECK_INTERVAL = 10;

    private final FaunaEntity animal;
    private final FaunaAbility charge;
    private final FaunaAbility burst;
    private final double triggerDistance;
    private final double abortDistance;

    @Nullable
    private Player threat;

    private boolean charging;

    /**
     * @param triggerDistance distancia em que a carga comeca
     * @param abortDistance   distancia em que o sapo desiste — maior que a de gatilho, senao a carga
     *                        cancelaria e reiniciaria em loop na fronteira
     */
    public FaunaToxicBurstGoal(FaunaEntity animal, FaunaAbility charge, FaunaAbility burst,
                               double triggerDistance, double abortDistance) {
        this.animal = animal;
        this.charge = charge;
        this.burst = burst;
        this.triggerDistance = triggerDistance;
        this.abortDistance = abortDistance;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private int nextCheck;

    @Override
    public boolean canUse() {
        if (this.animal.isUsingAbility()) {
            return false;
        }

        if (this.animal.tickCount < this.nextCheck) {
            return false;
        }
        this.nextCheck = this.animal.tickCount + CHECK_INTERVAL;

        if (!this.animal.canUseAbility()) {
            return false;
        }

        Player found = this.animal.level().getNearestPlayer(this.animal, this.triggerDistance);
        if (found == null || found.isCreative() || found.isSpectator()) {
            return false;
        }

        this.threat = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        Player target = this.threat;
        if (target == null || !target.isAlive()) {
            return false;
        }

        if (this.animal.isUsingAbility()) {
            return true;
        }

        // Entre a carga e a descarga: continua so se o jogador nao recuou.
        return this.charging && this.animal.distanceTo(target) <= this.abortDistance;
    }

    @Override
    public void start() {
        this.animal.getNavigation().stop();
        this.charging = this.animal.startAbility(this.charge);
    }

    @Override
    public void stop() {
        this.charging = false;
        this.threat = null;
    }

    @Override
    public void tick() {
        Player target = this.threat;
        if (target == null) {
            return;
        }

        this.animal.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.animal.isUsingAbility()) {
            return;
        }

        if (this.charging) {
            this.charging = false;
            this.animal.startAbility(this.burst);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
