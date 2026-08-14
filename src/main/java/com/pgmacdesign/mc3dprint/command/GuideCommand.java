package com.pgmacdesign.mc3dprint.command;

import com.pgmacdesign.mc3dprint.integration.patchouli.GuidebookAutoGive;
import com.pgmacdesign.mc3dprint.integration.patchouli.PatchouliCompat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * {@code /mc3dprint guide [targets]}: hand out the Fabricator's Handbook.
 *
 * <p>The Handbook is a Patchouli book, so the item id is {@code patchouli:guide_book} and a
 * plain {@code /give} of it produces an unbound "Invalid book: no ID defined" copy — the book
 * a stack opens lives in the {@code patchouli:book} tag, which {@code /give} can't
 * conveniently set. Combined with a one-per-player auto-give and no crafting recipe, a player
 * who lost their book had no way back. This command is that way back.
 *
 * <p>Self-service by design (no permission gate): the book is pure documentation and the mod
 * already hands it out for free. Giving it to OTHER players is gated at the usual level 2.
 */
public final class GuideCommand {

    private GuideCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mc3dprint")
                .then(Commands.literal("guide")
                        .executes(context -> giveSelf(context.getSource()))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> giveTo(context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"))))));
    }

    private static int giveSelf(CommandSourceStack source) {
        if (!available(source)) {
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mc3dprint.guide.needs_player"));
            return 0;
        }
        if (!GuidebookAutoGive.grant(player)) {
            source.sendFailure(Component.translatable("command.mc3dprint.guide.unavailable"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.guide.given"), false);
        return 1;
    }

    private static int giveTo(CommandSourceStack source, Collection<ServerPlayer> targets) {
        if (!available(source)) {
            return 0;
        }
        int given = 0;
        for (ServerPlayer target : targets) {
            if (GuidebookAutoGive.grant(target)) {
                target.sendSystemMessage(Component.translatable("message.mc3dprint.book_given"));
                given++;
            }
        }
        if (given == 0) {
            source.sendFailure(Component.translatable("command.mc3dprint.guide.unavailable"));
            return 0;
        }
        int count = given;
        source.sendSuccess(() -> Component.translatable("command.mc3dprint.guide.given_others", count),
                true);
        return count;
    }

    /** Patchouli is a soft dep: without it there is no book to hand out at all. */
    private static boolean available(CommandSourceStack source) {
        if (PatchouliCompat.isLoaded()) {
            return true;
        }
        source.sendFailure(Component.translatable("command.mc3dprint.guide.no_patchouli"));
        return false;
    }
}
