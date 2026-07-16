package com.example.examplemod.hive;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Bloco técnico de marcador (spec §6/§13). Existe apenas durante a AUTORIA de módulos:
 * visível como cubo-arame colorido com código de duas letras, sem colisão, quebra
 * instantânea. Ao colocar um módulo via /fchive (ou pela geração da cidade), o
 * {@link HiveMarkerProcessor} converte cada marcador em ar e captura sua posição —
 * ele nunca aparece no mundo final.
 */
public class HiveMarkerBlock extends Block {

    private final HiveMarkers.MarkerType type;

    public HiveMarkerBlock(HiveMarkers.MarkerType type, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
    }

    public HiveMarkers.MarkerType markerType() {
        return type;
    }
}
