package com.example.examplemod.hive;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Converte blocos {@link HiveMarkerBlock} em AR no momento da colocação do template e
 * envia (tipo, posição no mundo) para {@link HiveMarkers}. Como retorna um
 * StructureBlockInfo de ar (em vez de null), a posição continua sendo escrita — o módulo
 * "cava" o espaço do marcador normalmente, sem deixar lixo do terreno anterior.
 */
public class HiveMarkerProcessor extends StructureProcessor {

    public static final HiveMarkerProcessor INSTANCE = new HiveMarkerProcessor();
    public static final Codec<HiveMarkerProcessor> CODEC = Codec.unit(() -> INSTANCE);

    private HiveMarkerProcessor() {
    }

    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos structureOrigin,
                                                             BlockPos referencePos,
                                                             StructureTemplate.StructureBlockInfo rawInfo,
                                                             StructureTemplate.StructureBlockInfo transformedInfo,
                                                             StructurePlaceSettings settings) {
        if (transformedInfo.state().getBlock() instanceof HiveMarkerBlock marker) {
            HiveMarkers.capture(marker.markerType(), transformedInfo.pos());
            return new StructureTemplate.StructureBlockInfo(
                    transformedInfo.pos(), Blocks.AIR.defaultBlockState(), null);
        }
        return transformedInfo;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return HiveProcessors.HIVE_MARKER.get();
    }
}
