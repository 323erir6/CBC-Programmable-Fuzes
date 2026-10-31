package ua.ivan.cbcprogrammablefuzes.mixin;

import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import ua.ivan.cbcprogrammablefuzes.CorrectionFlightController;
import ua.ivan.cbcprogrammablefuzes.CorrectionSettings;
import ua.ivan.cbcprogrammablefuzes.ProgrammableFuzeItem;
import ua.ivan.cbcprogrammablefuzes.ProjectileRemovalState;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleTransfer;
import rbasamoyai.createbigcannons.munitions.autocannon.flak.FlakAutocannonProjectile;

@Mixin(FuzedBigCannonProjectile.class)
abstract class FuzedBigCannonProjectileMixin implements ProgrammableFuzeItem.PhysicalDetonationAccess {
    @Shadow
    protected abstract void detonate(Position position);

    @Inject(method = "setFuze", at = @At("TAIL"))
    private void cbcProgrammableFuzes$readTailCarrier(ItemStack stack, CallbackInfo ci) {
        CorrectionSettings settings = CorrectionSettings.readCarrier(stack);
        if (settings != null) {
            CorrectionFlightController.attach((FuzedBigCannonProjectile) (Object) this, settings);
        }
    }

    @Override
    public void cbcProgrammableFuzes$detonateFromPhysics(Vec3 position) {
        detonate(position);
        ((ProjectileRemovalState) this).cbcProgrammableFuzes$scheduleRemoval();
    }
}

/** Optional CBC Enhanced Shells carrier integration without a hard runtime dependency. */
@Pseudo
@Mixin(targets = "com.example.cbcenhancedshells.rocket.RocketEntity", remap = false)
abstract class EnhancedShellsRocketMixin implements ProgrammableFuzeItem.PhysicalDetonationAccess {
    @Invoker("airburst")
    protected abstract void cbcProgrammableFuzes$releasePayloadNow();

    @Inject(method = "setPayloadBlockNbt", at = @At("TAIL"), require = 0)
    private void cbcProgrammableFuzes$attachPayloadTailModule(CompoundTag tag, CallbackInfo ci) {
        if (tag == null || !tag.contains("TailModule", Tag.TAG_COMPOUND)) return;
        AbstractBigCannonProjectile projectile = (AbstractBigCannonProjectile) (Object) this;
        ItemStack module = ItemStack.parseOptional(projectile.level().registryAccess(),
                tag.getCompound("TailModule"));
        CorrectionModuleTransfer.attach(projectile, module);
    }

    @Override
    public void cbcProgrammableFuzes$detonateFromPhysics(Vec3 position) {
        cbcProgrammableFuzes$releasePayloadNow();
    }
}

@Mixin(FlakAutocannonProjectile.class)
abstract class FlakAutocannonProjectileMixin implements ProgrammableFuzeItem.PhysicalDetonationAccess {
    @Shadow
    protected abstract void detonate(Position position);

    @Override
    public void cbcProgrammableFuzes$detonateFromPhysics(Vec3 position) {
        detonate(position);
        ((ProjectileRemovalState) this).cbcProgrammableFuzes$scheduleRemoval();
    }
}
