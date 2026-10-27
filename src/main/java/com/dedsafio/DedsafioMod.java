package com.dedsafio;

import com.dedsafio.challenge.ChallengeManager;
import com.dedsafio.command.DedsafioCommands;
import com.dedsafio.config.DedsafioConfig;
import com.dedsafio.net.RouletteSpinPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class DedsafioMod implements ModInitializer {
    public static final String MOD_ID = "dedsafio";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChallengeManager manager;

    public static ChallengeManager manager() {
        return manager;
    }

    @Override
    public void onInitialize() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("dedsafio.json");
        DedsafioConfig config = DedsafioConfig.loadOrCreate(configPath);
        manager = new ChallengeManager(config, configPath);

        // El paquete de la ruleta (servidor -> cliente) debe registrarse en ambos lados.
        PayloadTypeRegistry.playS2C().register(RouletteSpinPayload.ID, RouletteSpinPayload.CODEC);

        ServerLifecycleEvents.SERVER_STARTED.register(manager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(manager::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(manager::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DedsafioCommands.register(dispatcher, manager));

        LOGGER.info("DEDsafío cargado: {} días de {} s, {} categorías.",
                config.totalDias, config.duracionDiaSegundos, config.categorias.size());
    }
}
