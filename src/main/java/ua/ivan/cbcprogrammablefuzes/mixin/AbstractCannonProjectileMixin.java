package ua.ivan.cbcprogrammablefuzes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import ua.ivan.cbcprogrammablefuzes.CorrectionFlightController;
import ua.ivan.cbcprogrammablefuzes.CorrectionRuntimeState;
import ua.ivan.cbcprogrammablefuzes.ProjectileRemovalState;
import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;

/** Priority 900 lets CBC Realistic Ballistics capture its unmodified impact-energy reference first. */
@Mixin(value = AbstractCannonProjectile.class, priority = 900)
abstract class AbstractCannonProjectileMixin implements CorrectionRuntimeState, ProjectileRemovalState {
    @Shadow
    protected boolean removeNextTick;

    @Unique
    private byte cbcProgrammableFuzes$correctionState;
    @Unique
    @Nullable
    private CompoundTag cbcProgrammableFuzes$correctionData;

    @Override
    public byte cbcProgrammableFuzes$getCorrectionState() {
        return cbcProgrammableFuzes$correctionState;
    }

    @Override
    public void cbcProgrammableFuzes$setCorrectionState(byte state, @Nullable CompoundTag data) {
        cbcProgrammableFuzes$correctionState = state;
        cbcProgrammableFuzes$correctionData = data;
    }

    @Override
    @Nullable
    public CompoundTag cbcProgrammableFuzes$getCorrectionData() {
        return cbcProgrammableFuzes$correctionData;
    }

    @Override
    public boolean cbcProgrammableFuzes$removeAfterImpact() {
        return removeNextTick;
    }

    @Override
    public void cbcProgrammableFuzes$scheduleRemoval() {
        removeNextTick = true;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cbcProgrammableFuzes$guideTailModule(CallbackInfo ci) {
        CorrectionFlightController.tick((AbstractCannonProjectile) (Object) this);
    }
}
