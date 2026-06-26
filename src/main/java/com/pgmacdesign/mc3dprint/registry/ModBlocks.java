package com.pgmacdesign.mc3dprint.registry;

import com.pgmacdesign.mc3dprint.MC3DPrint;
import com.pgmacdesign.mc3dprint.machine.MachineTier;
import com.pgmacdesign.mc3dprint.machine.PrinterBlock;
import com.pgmacdesign.mc3dprint.machine.WinderBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.CasingBlock;
import com.pgmacdesign.mc3dprint.machine.multiblock.ControllerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, MC3DPrint.MOD_ID);

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** Cables break fast by hand and aren't full cubes (noOcclusion lets neighbors render). */
    private static BlockBehaviour.Properties cableProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(0.5F)
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    /** Bookshelf-style spool storage that doubles as a Filament-Unit reservoir. */
    public static final DeferredHolder<Block, Block> FILAMENT_RACK = BLOCKS.register("filament_rack",
            () -> new com.pgmacdesign.mc3dprint.machine.rack.FilamentRackBlock(machineProperties()));

    /** Single dual-carry (RF + Filament Units) cable. */
    public static final DeferredHolder<Block, Block> MC3DCABLE = BLOCKS.register("mc3dcable",
            () -> new com.pgmacdesign.mc3dprint.machine.cable.MC3DCableBlock(cableProperties()));

    /** Library terminal: browse deposited blueprints, re-burn them onto blank discs. */
    public static final DeferredHolder<Block, Block> BLUEPRINT_REPOSITORY = BLOCKS.register("blueprint_repository",
            () -> new com.pgmacdesign.mc3dprint.machine.repository.BlueprintRepositoryBlock(machineProperties()));

    /** Single-block printers, index 0 = Tier 1. T5-T8 are multiblock controllers (phase b). */
    public static final List<DeferredHolder<Block, Block>> PRINTERS = buildPrinters();

    /** Single universal Filament Winder (kept as a 1-element list so the block
     * item, creative tab, and block-entity registrations stay uniform). */
    public static final List<DeferredHolder<Block, Block>> WINDERS = buildWinders();

    /** Multiblock controllers, index 0 = Tier 5. */
    public static final List<DeferredHolder<Block, Block>> CONTROLLERS = buildControllers();

    public static final DeferredHolder<Block, Block> PRINTER_CASING = BLOCKS.register("printer_casing",
            () -> new CasingBlock(machineProperties()
                    .lightLevel(s -> s.getValue(CasingBlock.ACTIVE) ? 6 : 0)));

    public static final DeferredHolder<Block, Block> FILAMENT_CONVERTER = BLOCKS.register("filament_converter",
            () -> new com.pgmacdesign.mc3dprint.machine.FilamentConverterBlock(machineProperties()));

    public static final DeferredHolder<Block, Block> REMOTE_TERMINAL = BLOCKS.register("remote_terminal",
            () -> new com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock(machineProperties()));

    /** Free trickle generator so the mod works with no other RF mod installed. */
    public static final DeferredHolder<Block, Block> CLOCK_GENERATOR = BLOCKS.register("clock_generator",
            () -> new com.pgmacdesign.mc3dprint.machine.ClockGeneratorBlock(machineProperties()));

    /** Autonomous, silent redstone timer: pulses all 6 sides every N (1-60) seconds. */
    public static final DeferredHolder<Block, Block> REDSTONE_CLOCK = BLOCKS.register("redstone_clock",
            () -> new com.pgmacdesign.mc3dprint.machine.RedstoneClockBlock(machineProperties()));

    /** Creative-only infinite RF source (no recipe). */
    public static final DeferredHolder<Block, Block> CREATIVE_ENERGY_SOURCE = BLOCKS.register("creative_energy_source",
            () -> new com.pgmacdesign.mc3dprint.machine.CreativeEnergyBlock(machineProperties()));

    /** End-only ore for T5+ machine components (name placeholder per design doc). */
    public static final DeferredHolder<Block, Block> EXTRUDIUM_ORE = BLOCKS.register("extrudium_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(4.5F, 9.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 6)
                    .requiresCorrectToolForDrops()));

    // Aliases for the most-referenced blocks
    public static final DeferredHolder<Block, Block> TIER1_PRINTER = PRINTERS.get(0);
    public static final DeferredHolder<Block, Block> FILAMENT_WINDER = WINDERS.get(0);

    private static List<DeferredHolder<Block, Block>> buildControllers() {
        List<DeferredHolder<Block, Block>> controllers = new ArrayList<>(4);
        for (int tierNumber = 5; tierNumber <= 8; tierNumber++) {
            final MachineTier tier = MachineTier.byNumber(tierNumber);
            controllers.add(BLOCKS.register("tier" + tierNumber + "_fabricator",
                    () -> new ControllerBlock(tier, machineProperties()
                            .lightLevel(s -> s.hasProperty(ControllerBlock.FORMED)
                                    && s.getValue(ControllerBlock.FORMED) ? 8 : 0))));
        }
        return List.copyOf(controllers);
    }

    private static List<DeferredHolder<Block, Block>> buildPrinters() {
        List<DeferredHolder<Block, Block>> printers = new ArrayList<>(4);
        for (int tierNumber = 1; tierNumber <= 4; tierNumber++) {
            final MachineTier tier = MachineTier.byNumber(tierNumber);
            printers.add(BLOCKS.register("tier" + tierNumber + "_printer",
                    () -> new PrinterBlock(tier, machineProperties())));
        }
        return List.copyOf(printers);
    }

    private static List<DeferredHolder<Block, Block>> buildWinders() {
        // one universal winder — the spool tier (not a winder ladder) gates which
        // materials it accepts, so a single block handles every tier
        return List.of(BLOCKS.register("filament_winder",
                () -> new WinderBlock(machineProperties())));
    }

    private ModBlocks() {}
}
