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
    }
}
