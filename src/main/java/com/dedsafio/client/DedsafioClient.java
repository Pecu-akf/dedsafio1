package com.dedsafio.client;

import com.dedsafio.net.RouletteSpinPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class DedsafioClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Los manejadores de paquetes de Fabric API ya corren en el hilo del cliente.
        ClientPlayNetworking.registerGlobalReceiver(RouletteSpinPayload.ID,
                (payload, context) -> RouletteHud.mostrar(payload));

        // La ruleta se dibuja como parte del HUD: no abre ninguna pantalla ni bloquea el movimiento.
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> RouletteHud.dibujar(drawContext));
    }
}
