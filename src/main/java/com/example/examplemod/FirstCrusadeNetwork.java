package com.example.examplemod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's networking: the {@link SimpleChannel} and all packet registrations, extracted from
 * {@link ExampleMod}. Call {@link #register()} once during common setup.
 */
public final class FirstCrusadeNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExampleMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private FirstCrusadeNetwork() {
    }

    // Registers every packet on the channel. Called from ExampleMod's common setup (enqueued).
    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                ImperialCommandCoreActionPacket.class,
                ImperialCommandCoreActionPacket::encode,
                ImperialCommandCoreActionPacket::decode,
                ImperialCommandCoreActionPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                OpenFactionSelectPacket.class,
                OpenFactionSelectPacket::encode,
                OpenFactionSelectPacket::decode,
                OpenFactionSelectPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                SelectFactionPacket.class,
                SelectFactionPacket::encode,
                SelectFactionPacket::decode,
                SelectFactionPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                StrategiumActionPacket.class,
                StrategiumActionPacket::encode,
                StrategiumActionPacket::decode,
                StrategiumActionPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                OrkCampActionPacket.class,
                OrkCampActionPacket::encode,
                OrkCampActionPacket::decode,
                OrkCampActionPacket::handle
        );

        // The raid key (R). An empty packet on purpose: which camp, which base, how many soldiers
        // and where they land are all decided on the server, so the press carries nothing to forge.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.assault.ImperialRaidKeyPacket.class,
                com.example.examplemod.assault.ImperialRaidKeyPacket::encode,
                com.example.examplemod.assault.ImperialRaidKeyPacket::decode,
                com.example.examplemod.assault.ImperialRaidKeyPacket::handle
        );

        // Terminal de navegacao planetaria. Um pacote so, cliente -> servidor: o pedido de
        // viagem. O caminho de volta (estado dos planetas) viaja no buffer do menu quando a tela
        // abre, entao nao existe pacote de sincronizacao para manter nem trafego por tick.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.planet.PlanetTravelRequestPacket.class,
                com.example.examplemod.planet.PlanetTravelRequestPacket::encode,
                com.example.examplemod.planet.PlanetTravelRequestPacket::decode,
                com.example.examplemod.planet.PlanetTravelRequestPacket::handle
        );

        // Imperial progression. Three packets: one verb from the client (buy a rank, start a
        // surgery, ascend, use an ability), one private snapshot back to its owner, and one public
        // stage broadcast — the player's body, which every client in render distance must have or
        // it will simulate a giant with a human hitbox. The tree itself never travels; both sides
        // read it from PlayerProgressionTree, which is code, not data.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.progression.ProgressionActionPacket.class,
                com.example.examplemod.progression.ProgressionActionPacket::encode,
                com.example.examplemod.progression.ProgressionActionPacket::decode,
                com.example.examplemod.progression.ProgressionActionPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.progression.SyncPlayerProgressionPacket.class,
                com.example.examplemod.progression.SyncPlayerProgressionPacket::encode,
                com.example.examplemod.progression.SyncPlayerProgressionPacket::decode,
                com.example.examplemod.progression.SyncPlayerProgressionPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.progression.SyncPlayerStagePacket.class,
                com.example.examplemod.progression.SyncPlayerStagePacket::encode,
                com.example.examplemod.progression.SyncPlayerStagePacket::decode,
                com.example.examplemod.progression.SyncPlayerStagePacket::handle
        );

        // The Ork Fury bar, on its own. Fury moves on every blow in either direction, and routing it
        // through the profile packet meant sending both trees, every tally and a body broadcast to
        // shift a number between 0 and 100. Two fields, to one player, throttled on top.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.progression.ork.SyncOrkFuryPacket.class,
                com.example.examplemod.progression.ork.SyncOrkFuryPacket::encode,
                com.example.examplemod.progression.ork.SyncOrkFuryPacket::decode,
                com.example.examplemod.progression.ork.SyncOrkFuryPacket::handle
        );

        // The Crusade panel: one ask from the client when a roster tab opens, one answer back. The
        // roster is a list of names, which the Core menu's ContainerData cannot carry at all, and
        // pulling it on demand means a player who never opens the tab never pays for it.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.crusade.CrusadePanelRequestPacket.class,
                com.example.examplemod.crusade.CrusadePanelRequestPacket::encode,
                com.example.examplemod.crusade.CrusadePanelRequestPacket::decode,
                com.example.examplemod.crusade.CrusadePanelRequestPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.crusade.CrusadePanelPacket.class,
                com.example.examplemod.crusade.CrusadePanelPacket::encode,
                com.example.examplemod.crusade.CrusadePanelPacket::decode,
                com.example.examplemod.crusade.CrusadePanelPacket::handle
        );

        // Tremor de tela da fauna. Servidor -> um jogador, uma vez por evento (pisao, carga,
        // emergencia), nunca por tick. A magnitude ja vem atenuada pela distancia: quem decide quem
        // sente e com que forca e o servidor, e o cliente so aplica no angulo da camera.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.fauna.effect.FaunaTremorPacket.class,
                com.example.examplemod.fauna.effect.FaunaTremorPacket::encode,
                com.example.examplemod.fauna.effect.FaunaTremorPacket::decode,
                com.example.examplemod.fauna.effect.FaunaTremorPacket::handle
        );

        // Mesa de Guerra: pedido (cliente -> servidor) e retrato (servidor -> cliente).
        // Registrados no FIM de propósito: o id de um pacote é a posição dele nesta lista, e
        // inserir no meio renumeraria todos os que vêm depois.
        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.campaign.wartable.WarTableRequestPacket.class,
                com.example.examplemod.campaign.wartable.WarTableRequestPacket::encode,
                com.example.examplemod.campaign.wartable.WarTableRequestPacket::decode,
                com.example.examplemod.campaign.wartable.WarTableRequestPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.campaign.wartable.WarTableSnapshotPacket.class,
                com.example.examplemod.campaign.wartable.WarTableSnapshotPacket::encode,
                com.example.examplemod.campaign.wartable.WarTableSnapshotPacket::decode,
                com.example.examplemod.campaign.wartable.WarTableSnapshotPacket::handle
        );

        CHANNEL.registerMessage(
                packetId++,
                com.example.examplemod.campaign.wartable.WarTableOrderPacket.class,
                com.example.examplemod.campaign.wartable.WarTableOrderPacket::encode,
                com.example.examplemod.campaign.wartable.WarTableOrderPacket::decode,
                com.example.examplemod.campaign.wartable.WarTableOrderPacket::handle
        );

        // Fails loudly at load if the tree is not the shape the design promises.
        com.example.examplemod.progression.PlayerProgressionTree.validate();
        com.example.examplemod.progression.ork.PlayerOrkProgressionTree.validate();
    }
}
