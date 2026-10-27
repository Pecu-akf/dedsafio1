package com.dedsafio.net;

import com.dedsafio.DedsafioMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Servidor -> cliente. El servidor ya decidió el resultado; el cliente solo lo anima.
 * Con cancel = true el cliente borra la ruleta que esté mostrando.
 */
public record RouletteSpinPayload(
        boolean cancel,
        List<Segment> segments,
        int winnerIndex,
        float offset,
        int spinTicks,
        String categoryName,
        int categoryColor,
        String eventName,
        String eventDesc,
        int durationSeconds
) implements CustomPayload {

    public record Segment(String name, int color) {
    }

    public static final CustomPayload.Id<RouletteSpinPayload> ID =
            new CustomPayload.Id<>(Identifier.of(DedsafioMod.MOD_ID, "ruleta_giro"));

    public static final PacketCodec<RegistryByteBuf, RouletteSpinPayload> CODEC =
            PacketCodec.of(RouletteSpinPayload::write, RouletteSpinPayload::read);

    public static RouletteSpinPayload cancelado() {
        return new RouletteSpinPayload(true, List.of(), 0, 0f, 0, "", 0xFFFFFF, "", "", 0);
    }

    private void write(RegistryByteBuf buf) {
        buf.writeBoolean(cancel);
        buf.writeVarInt(segments.size());
        for (Segment s : segments) {
            buf.writeString(s.name());
            buf.writeInt(s.color());
        }
        buf.writeVarInt(winnerIndex);
        buf.writeFloat(offset);
        buf.writeVarInt(spinTicks);
        buf.writeString(categoryName);
        buf.writeInt(categoryColor);
        buf.writeString(eventName);
        buf.writeString(eventDesc);
        buf.writeVarInt(durationSeconds);
    }

    private static RouletteSpinPayload read(RegistryByteBuf buf) {
        boolean cancel = buf.readBoolean();
        int n = buf.readVarInt();
        List<Segment> segs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buf.readString();
            int color = buf.readInt();
            segs.add(new Segment(name, color));
        }
        int winner = buf.readVarInt();
        float offset = buf.readFloat();
        int spinTicks = buf.readVarInt();
        String catName = buf.readString();
        int catColor = buf.readInt();
        String evName = buf.readString();
        String evDesc = buf.readString();
        int dur = buf.readVarInt();
        return new RouletteSpinPayload(cancel, segs, winner, offset, spinTicks, catName, catColor, evName, evDesc, dur);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
