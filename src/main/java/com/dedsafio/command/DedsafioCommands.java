package com.dedsafio.command;

import com.dedsafio.challenge.ChallengeManager;
import com.dedsafio.challenge.ChallengeState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** /dedsafio ... (solo operadores, nivel 2). */
public final class DedsafioCommands {

    private DedsafioCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, ChallengeManager mgr) {
        dispatcher.register(CommandManager.literal("dedsafio")
                .requires(src -> src.hasPermissionLevel(2))

                .then(CommandManager.literal("iniciar")
                        .executes(c -> accion(c, mgr.iniciar(c.getSource().getServer()), "Desafío iniciado.")))
                .then(CommandManager.literal("pausar")
                        .executes(c -> accion(c, mgr.pausar(c.getSource().getServer()), "Desafío pausado.")))
                .then(CommandManager.literal("reanudar")
                        .executes(c -> accion(c, mgr.reanudar(c.getSource().getServer()), "Desafío reanudado.")))
                .then(CommandManager.literal("finalizar")
                        .executes(c -> accion(c, mgr.finalizar(c.getSource().getServer()), "Desafío finalizado.")))

                .then(CommandManager.literal("dia")
                        .executes(c -> info(c, mgr.describirDia()))
                        .then(CommandManager.literal("fijar")
                                .then(CommandManager.argument("dia", IntegerArgumentType.integer(1))
                                        .executes(c -> accion(c,
                                                mgr.fijarDia(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "dia")),
                                                "Día actualizado."))))
                        .then(CommandManager.literal("sumar")
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(1))
                                        .executes(c -> accion(c,
                                                mgr.cambiarDias(c.getSource().getServer(), IntegerArgumentType.getInteger(c, "n")),
                                                "Día actualizado."))))
                        .then(CommandManager.literal("restar")
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(1))
                                        .executes(c -> accion(c,
                                                mgr.cambiarDias(c.getSource().getServer(), -IntegerArgumentType.getInteger(c, "n")),
                                                "Día actualizado.")))))

                .then(CommandManager.literal("ruleta")
                        .executes(c -> accion(c, mgr.roulette().girar(c.getSource().getServer(), mgr), null)))

                .then(CommandManager.literal("evento")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(mgr.config().idsEventos(), builder))
                                .executes(c -> accion(c,
                                        mgr.activarEventoPorId(c.getSource().getServer(), StringArgumentType.getString(c, "id")),
                                        null))))

                .then(CommandManager.literal("cancelar")
                        .executes(c -> accion(c, mgr.cancelar(c.getSource().getServer()), null)))

                .then(CommandManager.literal("estado")
                        .executes(c -> info(c, mgr.resumenEstado())))

                .then(CommandManager.literal("historial")
                        .executes(c -> historial(c, mgr)))

                .then(CommandManager.literal("recargar")
                        .executes(c -> accion(c, mgr.recargarConfig(), "Configuración recargada.")))
        );
    }

    private static int accion(CommandContext<ServerCommandSource> c, String error, String ok) {
        if (error != null) {
            c.getSource().sendError(Text.literal(error));
            return 0;
        }
        if (ok != null) {
            c.getSource().sendFeedback(() -> Text.literal(ok).formatted(Formatting.GREEN), false);
        }
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> c, String mensaje) {
        c.getSource().sendFeedback(() -> Text.literal(mensaje), false);
        return 1;
    }

    private static int historial(CommandContext<ServerCommandSource> c, ChallengeManager mgr) {
        List<ChallengeState.HistorialEntrada> h = mgr.state().historial;
        if (h.isEmpty()) {
            return info(c, "Todavía no ha salido ningún evento.");
        }
        int desde = Math.max(0, h.size() - 10);
        for (int i = desde; i < h.size(); i++) {
            ChallengeState.HistorialEntrada e = h.get(i);
            String linea = "Día " + e.dia + " · " + e.categoriaNombre + " · " + e.eventoNombre;
            c.getSource().sendFeedback(() -> Text.literal(linea), false);
        }
        return 1;
    }
}
