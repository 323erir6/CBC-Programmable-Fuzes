package ua.ivan.cbcprogrammablefuzes.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleHolder;
import ua.ivan.cbcprogrammablefuzes.CorrectionSettings;

@Mixin(BigCannonProjectileBlockEntity.class)
abstract class BigCannonProjectileBlockEntityMixin implements CorrectionModuleHolder {
    @Unique
    private ItemStack cbcProgrammableFuzes$correctionModule = ItemStack.EMPTY;

    @Override
    public ItemStack cbcProgrammableFuzes$getCorrectionModule() {
        return cbcProgrammableFuzes$correctionModule.copy();
    }

    @Override
    public void cbcProgrammableFuzes$setCorrectionModule(ItemStack stack) {
        cbcProgrammableFuzes$correctionModule = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (cbcProgrammableFuzes$correctionModule.isEmpty()
            && (Object) this instanceof FuzedBlockEntity fuzed) {
            ItemStack fuze = fuzed.getFuze();
            CorrectionSettings.clearCarrier(fuze);
            fuzed.setFuze(fuze);
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void cbcProgrammableFuzes$saveTailModule(CompoundTag tag, HolderLookup.Provider registries,
                                                      CallbackInfo ci) {
        tag.put("TailModule", cbcProgrammableFuzes$correctionModule.saveOptional(registries));
    }

    @Inject(method = "writeSafe", at = @At("TAIL"))
    private void cbcProgrammableFuzes$writeSafeTailModule(CompoundTag tag, HolderLookup.Provider registries,
                                                           CallbackInfo ci) {
        tag.put("TailModule", cbcProgrammableFuzes$correctionModule.saveOptional(registries));
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void cbcProgrammableFuzes$loadTailModule(CompoundTag tag, HolderLookup.Provider registries,
                                                      CallbackInfo ci) {
        ItemStack loaded = ItemStack.parseOptional(registries, tag.getCompound("TailModule"));
        cbcProgrammableFuzes$correctionModule = loaded.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())
            ? loaded.copyWithCount(1) : ItemStack.EMPTY;
    }
}
