package com.pgmacdesign.mc3dprint.network;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> server: retitle a catalogued blueprint from the Blueprint Repository GUI.
 *
 * <p>Scanned discs are auto-named "Scan @ x,y,z", which stops telling them apart the moment
 * a library holds more than one. The server re-validates everything (menu open, entry exists,
 * not official, name sane) — the packet is a request, not an instruction.
 */
public record RepositoryRenamePacket(UUID id, String name) implements CustomPacketPayload {

    public static final Type<RepositoryRenamePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MC3DPrint.MOD_ID, "repository_rename"));

    /** Hard cap on the wire, well above {@link #MAX_NAME_LENGTH}, so a hostile packet can't
     *  make the reader allocate a huge string before validation gets a look at it. */
    private static final int MAX_WIRE_LENGTH = 256;

    /** What a player may actually set. Long enough for "Chicken farm house", short enough to
     *  fit the repository row and a disc tooltip without truncation. */
    public static final int MAX_NAME_LENGTH = 48;

    public static final StreamCodec<RegistryFriendlyByteBuf, RepositoryRenamePacket> STREAM_CODEC =
            StreamCodec.ofMember(RepositoryRenamePacket::write, RepositoryRenamePacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(id);
        buf.writeUtf(name, MAX_WIRE_LENGTH);
    }

    private static RepositoryRenamePacket read(RegistryFriendlyByteBuf buf) {
        return new RepositoryRenamePacket(buf.readUUID(), buf.readUtf(MAX_WIRE_LENGTH));
    }

    /**
     * The name as it may be stored: control characters and section signs stripped (no chat
     * formatting injection, no newlines in a one-line row), collapsed whitespace, trimmed and
     * length-capped. Empty means "reject" — a blank title would make the row unreadable.
     */
    public static String sanitize(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                continue; // formatting marker: drop outright, it separates nothing
            }
            // Control characters become a SPACE, not nothing: a pasted newline sits between
            // two words, and deleting it would glue them ("one\ntwo" -> "onetwo").
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        String cleaned = out.toString().replaceAll("\\s+", " ").trim();
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
