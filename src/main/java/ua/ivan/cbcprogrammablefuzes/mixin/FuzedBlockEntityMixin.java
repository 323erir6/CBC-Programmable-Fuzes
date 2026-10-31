package ua.ivan.cbcprogrammablefuzes.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockEntity;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleHolder;
import ua.ivan.cbcprogrammablefuzes.CorrectionSettings;
import ua.ivan.cbcprogrammablefuzes.peripheral.FuzeAccessUtil;

@Mixin(FuzedBlockEntity.class)
abstract class FuzedBlockEntityMixin {
    @Unique
    private boolean cbcProgrammableFuzes$wasAvailable;
    @Inject(method = "getFuze", at = @At("RETURN"), cancellable = true)
    private void cbcProgrammableFuzes$carryTailSettings(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        FuzedBlockEntity self = (FuzedBlockEntity) (Object) this;
        if (!result.isEmpty() && self instanceof CorrectionModuleHolder holder
            && holder.cbcProgrammableFuzes$hasCorrectionModule()) {
            CorrectionSettings.writeCarrier(result,
                CorrectionSettings.read(holder.cbcProgrammableFuzes$getCorrectionModule()));
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "setFuze", at = @At("HEAD"))
    private void cbcProgrammableFuzes$captureOldFuze(ItemStack stack, CallbackInfo ci) {
        FuzedBlockEntity self = (FuzedBlockEntity) (Object) this;
        cbcProgrammableFuzes$wasAvailable = FuzeAccessUtil.isProgrammableFuze(self.getFuze());
    }

    @Inject(method = "setFuze", at = @At("TAIL"))
    private void cbcProgrammableFuzes$invalidateChangedFuze(ItemStack stack, CallbackInfo ci) {
        FuzedBlockEntity self = (FuzedBlockEntity) (Object) this;
        CorrectionSettings carried = CorrectionSettings.readCarrier(stack);
        if (carried != null && self instanceof CorrectionModuleHolder holder
            && !holder.cbcProgrammableFuzes$hasCorrectionModule()) {
            ItemStack module = new ItemStack(ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get());
            CorrectionSettings.write(module, carried);
            holder.cbcProgrammableFuzes$setCorrectionModule(module);
        }
        boolean available = FuzeAccessUtil.isProgrammableFuze(self.getFuze());
        Level level = self.getLevel();
        if (level != null && !level.isClientSide && available != cbcProgrammableFuzes$wasAvailable) {
            level.invalidateCapabilities(self.getBlockPos());
        }
    }
}
