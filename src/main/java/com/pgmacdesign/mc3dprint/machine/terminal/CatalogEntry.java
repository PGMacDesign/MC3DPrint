package com.pgmacdesign.mc3dprint.machine.terminal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One row of the terminal's catalog: an item, what it costs, and whether it can be ordered right
 * now.
 *
 * <p>Unprintable rows are kept and greyed rather than filtered out. A catalog that silently omits
 * everything you cannot afford or cannot reach yet teaches the player nothing; one that shows the
 * row with "Tier 6" or "wind-only" against it doubles as documentation of the economy, which is
 * where most of this mod's rules actually live.
 *
 * @param item         the orderable item
 * @param fuCost       cost in tier units at {@link #tier}, or 0 when it has no price
 * @param tier         the tier the cost is denominated in, 0 when unpriced
 * @param verdict      why it cannot be ordered, or {@link PrintEligibility.Verdict#OK}
 * @param affordable   whether the network currently holds enough filament of that tier
 */
public record CatalogEntry(Item item, int fuCost, int tier,
                           PrintEligibility.Verdict verdict, boolean affordable) {

    public boolean orderable() {
        return verdict.printable() && affordable;
    }

    public ItemStack stack() {
        return new ItemStack(item);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item));
        buf.writeVarInt(fuCost);
        buf.writeByte(tier);
        buf.writeByte(verdict.ordinal());
        buf.writeBoolean(affordable);
    }

    public static CatalogEntry read(RegistryFriendlyByteBuf buf) {
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(buf.readVarInt());
        int fu = buf.readVarInt();
        int tier = buf.readByte();
        int ordinal = buf.readByte();
        // Clamp rather than index blindly: the ordinal arrives over the wire, and a client on a
        // mismatched build would otherwise throw out of the decoder and drop the whole GUI.
        PrintEligibility.Verdict[] all = PrintEligibility.Verdict.values();
        PrintEligibility.Verdict verdict = ordinal >= 0 && ordinal < all.length
                ? all[ordinal]
                : PrintEligibility.Verdict.UNVALUED;
        return new CatalogEntry(item, fu, tier, verdict, buf.readBoolean());
    }
}
