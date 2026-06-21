package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.blueprint.CuratedBlueprints;
import com.pgmacdesign.mc3dprint.config.MC3DPrintConfig;
import com.pgmacdesign.mc3dprint.item.BlueprintDiscItem;
import com.pgmacdesign.mc3dprint.machine.multiblock.MultiblockPattern;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final String AE2_MOD_ID = "ae2";

    // Farm / functional-contraption builds are emitted LAST so they sit at the
    // bottom of the creative tab — these are the discs that get iterated on and
    // re-tested most, so keeping them grouped at the end makes them easy to find.
    private static final java.util.Set<String> FARM_BUILDS = java.util.Set.of(
            "iron_farm", "mob_xp_tower", "sugarcane_farm_auto", "pumpkin_melon_farm",
            "cactus_farm", "bamboo_farm", "kelp_farm", "villager_trading_hall",
            "animal_pen", "fishery_pond", "tree_farm",
            "mushroom_farm", "nether_wart_farm", "bee_apiary", "super_smelter",
            "small_farm");

    // ── Creative-tab disc visibility ────────────────────────────────────────────
    // Two tiers of visibility for the curated Blueprint Discs, switched by the
    // config flag `allowAllDiscsInCreative` (config/mc3dprint-common.toml, [general]):
    //
    //   • FULL set  = CuratedBlueprints.CURATED_NAMES — literally every build we ship.
    //     A blueprint MUST be registered there to exist at all, so it is already the
    //     canonical "everything we build" allowlist; there is deliberately no second
    //     copy to keep in sync. EVERY NEW BLUEPRINT lands here automatically when you
    //     add it to CURATED_NAMES — nothing extra to do for full-creative visibility.
    //
    //   • LAUNCH set = CREATIVE_LAUNCH_DISCS (below) — the small, hand-picked set of
    //     discs we want visible in creative for the public release, so survival players
    //     still discover the rest as world loot. This list is INTENTIONALLY curated:
    //     new blueprints do NOT belong here automatically — add a name ONLY when we
    //     deliberately choose to promote that build into the launch view.
    //
    // allowAllDiscsInCreative = true  → emit the FULL set (default; dev/testing/creative)
    // allowAllDiscsInCreative = false → emit only the LAUNCH set (curated public release)
    //
    // Either way this is cosmetic: it changes only what's grabbable in the creative
    // menu, never what can be found as world loot. See the in-game guide (FAQ → "How do
    // I get blueprints?") for the player-facing explanation of the toggle.
    private static final java.util.Set<String> CREATIVE_LAUNCH_DISCS = java.util.Set.of(
            "small_cottage", "windmill", "castle_keep");

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MC3DPrint.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MC3DPRINT_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mc3dprint"))
                    .icon(() -> new ItemStack(ModItems.TIER1_PRINTER.get()))
                    .displayItems((parameters, output) -> {
                        // T8 content only shows with Draconic Evolution; converter only with AE2
                        boolean draconic = ModList.get().isLoaded(MultiblockPattern.DRACONIC_MOD_ID);
                        boolean ae2 = ModList.get().isLoaded(AE2_MOD_ID);

                        ModItems.PRINTERS.forEach(printer -> output.accept(printer.get()));
                        for (int i = 0; i < ModItems.FABRICATORS.size(); i++) {
                            if (draconic || i + 5 < 8) {
                                output.accept(ModItems.FABRICATORS.get(i).get());
                            }
                        }
                        output.accept(ModItems.PRINTER_CASING.get());
                        ModItems.WINDERS.forEach(winder -> output.accept(winder.get()));
                        output.accept(ModItems.CLOCK_GENERATOR.get());
                        output.accept(ModItems.REDSTONE_CLOCK.get());
                        if (ae2) {
                            output.accept(ModItems.FILAMENT_CONVERTER.get());
                        }
                        output.accept(ModItems.REMOTE_TERMINAL.get());
                        output.accept(ModItems.FILAMENT_RACK.get());
                        output.accept(ModItems.MC3DCABLE.get());
                        output.accept(ModItems.BLUEPRINT_REPOSITORY.get());
                        output.accept(ModItems.BLANK_BLUEPRINT_DISC.get());
                        output.accept(ModItems.BLUEPRINT_DISC.get());
                        output.accept(ModItems.SCANNER.get());
                        for (int i = 0; i < ModItems.SPOOLS.size(); i++) {
                            if (draconic || i + 1 < 8) {
                                output.accept(ModItems.SPOOLS.get(i).get());
                            }
                        }
                        output.accept(ModItems.EXTRUDIUM_ORE.get());
                        output.accept(ModItems.EXTRUDIUM_CRYSTAL.get());
                        output.accept(ModItems.SPEED_UPGRADE.get());
                        output.accept(ModItems.EFFICIENCY_UPGRADE.get());
                        output.accept(ModItems.RF_EFFICIENCY_UPGRADE.get());
                        output.accept(ModItems.BUFFER_UPGRADE.get());
                        // Resins: the crafting base, then every effect×tier variant.
                        output.accept(ModItems.RESIN_BASE.get());
                        ModItems.RESINS.forEach(resin -> output.accept(resin.get()));
                        output.accept(ModItems.CREATIVE_ENERGY_SOURCE.get());
                        output.accept(ModItems.CREATIVE_SPOOL.get());

                        // Curated blueprint discs — grab the bundled village-style
                        // builds directly in creative (survival finds them in loot).
                        // Non-farm builds first, then the FARM_BUILDS pushed to the
                        // bottom of the tab so the often-tested farms are easy to find.
                        // The set shown depends on the allowAllDiscsInCreative flag (see
                        // the CREATIVE_LAUNCH_DISCS note above): full set vs. launch set.
                        boolean allDiscs = MC3DPrintConfig.ALLOW_ALL_DISCS_IN_CREATIVE.get();
                        java.util.function.Predicate<String> creativeVisible =
                                name -> allDiscs || CREATIVE_LAUNCH_DISCS.contains(name);
                        java.util.function.Consumer<String> emitDisc = name ->
                                CuratedBlueprints.loadBundled(name).ifPresent(bp -> {
                                    ItemStack disc = new ItemStack(ModItems.BLUEPRINT_DISC.get());
                                    BlueprintDiscItem.writeBlueprint(disc,
                                            CuratedBlueprints.uuidFor(MC3DPrint.MOD_ID, name), bp);
                                    output.accept(disc);
                                });
                        for (String name : CuratedBlueprints.CURATED_NAMES) {
                            if (!FARM_BUILDS.contains(name) && creativeVisible.test(name)) {
                                emitDisc.accept(name);
                            }
                        }
                        for (String name : CuratedBlueprints.CURATED_NAMES) {
                            if (FARM_BUILDS.contains(name) && creativeVisible.test(name)) {
                                emitDisc.accept(name);
                            }
                        }

                        // The in-game guidebook — only when Patchouli is installed.
                        if (ModList.get().isLoaded("patchouli")) {
                            net.minecraft.world.item.Item guideBook =
                                    net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                            new net.minecraft.resources.ResourceLocation("patchouli", "guide_book"));
                            if (guideBook != null) {
                                ItemStack book = new ItemStack(guideBook);
                                book.getOrCreateTag().putString("patchouli:book", "mc3dprint:guide");
                                output.accept(book);
                            }
                        }
                    })
                    .build());

    private ModCreativeTabs() {}
}
