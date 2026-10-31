package ua.ivan.cbcprogrammablefuzes;

import net.minecraft.world.item.ItemStack;

public interface CorrectionModuleHolder {
    ItemStack cbcProgrammableFuzes$getCorrectionModule();
    void cbcProgrammableFuzes$setCorrectionModule(ItemStack stack);

    default boolean cbcProgrammableFuzes$hasCorrectionModule() {
        return cbcProgrammableFuzes$getCorrectionModule().is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get());
    }
}
