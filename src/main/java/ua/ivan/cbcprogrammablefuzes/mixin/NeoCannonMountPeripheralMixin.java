package ua.ivan.cbcprogrammablefuzes.mixin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.ivan.cbcprogrammablefuzes.peripheral.FuzeAccessUtil;
import ua.ivan.cbcprogrammablefuzes.peripheral.MountFuzeAccess;
import ua.ivan.cbcprogrammablefuzes.peripheral.ProgrammableFuzeAccess;

/** Adds methods to NeoPeripheral's existing objects instead of replacing their mount peripheral. */
@Pseudo
@Mixin(targets = {
    "io.p2vman.neoperipheral.integration.cbc.peripheral.CannonMountPeripheral",
    "io.p2vman.neoperipheral.integration.cbc.peripheral.FixedCannonMountPeripheral",
    "io.p2vman.neoperipheral.integration.cbccm.peripheral.CompactCannonMountPeripheral"
}, remap = false)
abstract class NeoCannonMountPeripheralMixin implements ProgrammableFuzeAccess {
    private MountFuzeAccess cbcProgrammableFuzes$access() {
        return new MountFuzeAccess(FuzeAccessUtil.findContraptionInPeripheral(this));
    }

    @Override
    public ItemStack programmableFuze$getFuze() {
        return cbcProgrammableFuzes$access().programmableFuze$getFuze();
    }

    @Override
    public boolean programmableFuze$setFuze(ItemStack stack) {
        return cbcProgrammableFuzes$access().programmableFuze$setFuze(stack);
    }

    @Override
    public ItemStack programmableFuze$getCorrectionModule() {
        return cbcProgrammableFuzes$access().programmableFuze$getCorrectionModule();
    }

    @Override
    public boolean programmableFuze$setCorrectionModule(ItemStack stack) {
        return cbcProgrammableFuzes$access().programmableFuze$setCorrectionModule(stack);
    }

    @Override
    public Map<String, Object> programmableFuze$getLoadedProjectileInfo() {
        return cbcProgrammableFuzes$access().programmableFuze$getLoadedProjectileInfo();
    }

    @Override
    public Map<String, Object> programmableFuze$getLoadedPropellantInfo() {
        return cbcProgrammableFuzes$access().programmableFuze$getLoadedPropellantInfo();
    }

    @Override
    public Map<String, Object> programmableFuze$getCannonInfo() {
        return cbcProgrammableFuzes$access().programmableFuze$getCannonInfo();
    }

    @Inject(method = "getAdditionalTypes", at = @At("RETURN"), cancellable = true, require = 0)
    private void cbcProgrammableFuzes$advertiseLoadedFuze(CallbackInfoReturnable<Set<String>> cir) {
        if (FuzeAccessUtil.findContraptionInPeripheral(this) == null) return;
        Set<String> types = new HashSet<>(cir.getReturnValue());
        types.add("programmable_fuze");
        types.add("cbc_programmable_fuze");
        types.add("trajectory_correction");
        cir.setReturnValue(Set.copyOf(types));
    }
}
