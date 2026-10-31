package ua.ivan.cbcprogrammablefuzes.peripheral;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileBlockEntity;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;

public final class FuzeCapabilities {
    public static final BlockCapability<ProgrammableFuzeAccess, Direction> PROGRAMMABLE_FUZE =
        BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(CBCProgrammableFuzes.MOD_ID, "programmable_fuze"),
            ProgrammableFuzeAccess.class
        );

    private FuzeCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        // Registering our own capability on all block-entity blocks lets addon mounts participate without us
        // replacing ComputerCraft's peripheral capability or any API supplied by another mod.
        Block[] entityBlocks = BuiltInRegistries.BLOCK.stream()
            .filter(block -> block instanceof EntityBlock)
            .toArray(Block[]::new);

        event.registerBlock(PROGRAMMABLE_FUZE, (level, pos, state, blockEntity, side) -> {
            if (blockEntity instanceof FuzedBlockEntity fuzed) {
                PlacedFuzeAccess access = new PlacedFuzeAccess(fuzed);
                if (access.programmableFuze$hasFuze() || access.programmableFuze$hasCorrectionModule()) return access;
            }
            if (blockEntity instanceof BigCannonProjectileBlockEntity projectile) {
                PlacedModuleAccess access = new PlacedModuleAccess(projectile);
                if (access.programmableFuze$hasCorrectionModule()) return access;
            }

            if (blockEntity instanceof ControlPitchContraption.Block) {
                return new MountFuzeAccess(blockEntity);
            }
            return null;
        }, entityBlocks);

        CBCProgrammableFuzes.LOGGER.info(
            "Registered programmable fuze capability for {} block-entity blocks",
            entityBlocks.length
        );
    }
}
