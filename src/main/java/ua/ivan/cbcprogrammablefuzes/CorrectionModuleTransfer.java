package ua.ivan.cbcprogrammablefuzes;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;

/** Copies a tail module from assembled block data into the live projectile. */
public final class CorrectionModuleTransfer {
    private CorrectionModuleTransfer() {
    }

    public static void attachFromBlocks(AbstractBigCannonProjectile projectile,
                                        List<StructureBlockInfo> blocks,
                                        HolderLookup.Provider registries) {
        if (projectile == null || blocks == null || blocks.isEmpty()
                || CorrectionFlightController.isAttached(projectile)) {
            return;
        }
        CompoundTag nbt = blocks.get(0).nbt();
        if (nbt == null || !nbt.contains("TailModule", Tag.TAG_COMPOUND)) return;
        attach(projectile, ItemStack.parseOptional(registries, nbt.getCompound("TailModule")));
    }

    public static void attach(AbstractBigCannonProjectile projectile, ItemStack module) {
        if (projectile == null || CorrectionFlightController.isAttached(projectile)
                || module == null
                || !module.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) {
            return;
        }
        CorrectionFlightController.attach(projectile, CorrectionSettings.read(module));
    }
}
