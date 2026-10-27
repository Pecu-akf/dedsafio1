package com.dedsafio.client;

import com.dedsafio.net.RouletteSpinPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Matrix4f;

/** Ruleta animada en la parte alta de la pantalla. Solo anima lo que el servidor ya decidió. */
public final class RouletteHud {
    private static final int VUELTAS_EXTRA = 5;
    private static final long MS_RESULTADO = 6000;
    private static final int RADIO = 46;

    private static RouletteSpinPayload actual;
    private static long inicioMs;

    private RouletteHud() {
    }

    public static void mostrar(RouletteSpinPayload payload) {
        if (payload.cancel() || payload.segments().isEmpty()) {
            actual = null;
            return;
        }
        actual = payload;
        inicioMs = System.currentTimeMillis();
    }

    public static void dibujar(DrawContext ctx) {
        RouletteSpinPayload p = actual;
        if (p == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        long spinMs = Math.max(1, p.spinTicks() * 50L);
        long transcurrido = System.currentTimeMillis() - inicioMs;
        if (transcurrido > spinMs + MS_RESULTADO) {
            actual = null;
            return;
        }
        if (mc.options.hudHidden) {
            return;
        }

        int n = p.segments().size();
        float sector = 360f / n;

        // Ease-out: gira rápido y frena al final. El giro final deja el segmento ganador bajo el puntero.
        float t = Math.min(1f, transcurrido / (float) spinMs);
        float suave = 1f - (float) Math.pow(1f - t, 3);
        float rotFinal = VUELTAS_EXTRA * 360f - (p.winnerIndex() + 0.5f + p.offset()) * sector;
        float rot = rotFinal * suave;

        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = 14 + RADIO + 12;

        // Primero se vuelca lo que el HUD tenga pendiente para que el orden de dibujo sea correcto.
        ctx.draw();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();

        abanico(m, cx, cy, RADIO + 3, 0f, 360f, 0xF0101010, 48);
        for (int i = 0; i < n; i++) {
            int rgb = p.segments().get(i).color();
            abanico(m, cx, cy, RADIO, rot + i * sector, rot + (i + 1) * sector, 0xFF000000 | rgb, 12);
        }
        puntero(m, cx, cy - RADIO);

        RenderSystem.enableCull();

        TextRenderer tr = mc.textRenderer;
        int y = cy + RADIO + 10;
        if (transcurrido < spinMs) {
            ctx.drawCenteredTextWithShadow(tr, Text.literal("Girando la ruleta...").formatted(Formatting.WHITE), cx, y, 0xFFFFFF);
        } else {
            ctx.fill(cx - 135, y - 4, cx + 135, y + 44, 0x99000000);
            Text cat = Text.literal(p.categoryName()).styled(s -> s.withColor(p.categoryColor()).withBold(true));
            Text ev = Text.literal(p.eventName()).formatted(Formatting.WHITE, Formatting.BOLD);
            String desc = tr.trimToWidth(p.eventDesc(), 256);
            Text dur = Text.literal("Duración: " + formatear(p.durationSeconds())).formatted(Formatting.GRAY);
            ctx.drawCenteredTextWithShadow(tr, cat, cx, y, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, ev, cx, y + 11, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(desc).formatted(Formatting.GRAY), cx, y + 22, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(tr, dur, cx, y + 33, 0xFFFFFF);
        }
    }

    /** Sector circular. Los ángulos van en grados, en sentido horario, con 0° arriba. */
    private static void abanico(Matrix4f m, float cx, float cy, float radio, float desde, float hasta, int argb, int pasos) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m, cx, cy, 0f).color(r, g, b, a);
        for (int i = 0; i <= pasos; i++) {
            double ang = Math.toRadians(desde + (hasta - desde) * i / pasos);
            float x = cx + radio * (float) Math.sin(ang);
            float y = cy - radio * (float) Math.cos(ang);
            buf.vertex(m, x, y, 0f).color(r, g, b, a);
        }
        net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    /** Triángulo blanco fijo que señala el segmento ganador. */
    private static void puntero(Matrix4f m, float x, float yBorde) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m, x - 6f, yBorde - 10f, 0f).color(255, 255, 255, 255);
        buf.vertex(m, x + 6f, yBorde - 10f, 0f).color(255, 255, 255, 255);
        buf.vertex(m, x, yBorde + 5f, 0f).color(255, 255, 255, 255);
        BufferRenderer.drawWithGlobalProgram(buf.end());
    }

    private static String formatear(int segundos) {
        return String.format("%d:%02d", segundos / 60, segundos % 60);
    }
}
