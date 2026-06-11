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
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MC3DPrint.MOD_ID);

    private static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops();
    }

    /** Single-block printers, index 0 = Tier 1. T5-T8 are multiblock controllers (phase b). */
    public static final List<RegistryObject<Block>> PRINTERS = buildPrinters();

    /** Filament winders, index 0 = Tier 1. */
    public static final List<RegistryObject<Block>> WINDERS = buildWinders();

    /** Multiblock controllers, index 0 = Tier 5. */
    public static final List<RegistryObject<Block>> CONTROLLERS = buildControllers();

    public static final RegistryObject<Block> PRINTER_CASING = BLOCKS.register("printer_casing",
            () -> new CasingBlock(machineProperties()));

    public static final RegistryObject<Block> FILAMENT_CONVERTER = BLOCKS.register("filament_converter",
            () -> new com.pgmacdesign.mc3dprint.machine.FilamentConverterBlock(machineProperties()));

    public static final RegistryObject<Block> REMOTE_TERMINAL = BLOCKS.register("remote_terminal",
            () -> new com.pgmacdesign.mc3dprint.machine.RemoteTerminalBlock(machineProperties()));

    /** End-only ore for T5+ machine components (name placeholder per design doc). */
    public static final RegistryObject<Block> PRINTITE_ORE = BLOCKS.register("printite_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(4.5F, 9.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 4)
                    .requiresCorrectToolForDrops()));

    // Aliases for the most-referenced blocks
    public static final RegistryObject<Block> TIER1_PRINTER = PRINTERS.get(0);
    public static final RegistryObject<Block> FILAMENT_WINDER = WINDERS.get(0);

    private static List<RegistryObject<Block>> buildControllers() {
        List<RegistryObject<Block>> controllers = new ArrayList<>(4);
        for (int tierNumber = 5; tierNumber <= 8; tierNumber++) {
            final MachineTier tier = MachineTier.byNumber(tierNumber);
            controllers.add(BLOCKS.register("tier" + tierNumber + "_fabricator",
                    () -> new ControllerBlock(tier, machineProperties())));
        }
        return List.copyOf(controllers);
    }

    private static List<RegistryObject<Block>> buildPrinters() {
        List<RegistryObject<Block>> printers = new ArrayList<>(4);
        for (int tierNumber = 1; tierNumber <= 4; tierNumber++) {
            final MachineTier tier = MachineTier.byNumber(tierNumber);
            printers.add(BLOCKS.register("tier" + tierNumber + "_printer",
                    () -> new PrinterBlock(tier, machineProperties())));
        }
        return List.copyOf(printers);
    }

    private static List<RegistryObject<Block>> buildWinders() {
        List<RegistryObject<Block>> winders = new ArrayList<>(4);
        winders.add(BLOCKS.register("filament_winder",
                () -> new WinderBlock(MachineTier.T1, machineProperties())));
        for (int tierNumber = 2; tierNumber <= 4; tierNumber++) {
            final MachineTier tier = MachineTier.byNumber(tierNumber);
            winders.add(BLOCKS.register("filament_winder_t" + tierNumber,
                    () -> new WinderBlock(tier, machineProperties())));
        }
        return List.copyOf(winders);
    }

    private ModBlocks() {}
}
