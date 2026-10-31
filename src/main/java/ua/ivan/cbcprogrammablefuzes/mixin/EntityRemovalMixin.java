package ua.ivan.cbcprogrammablefuzes.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.ivan.cbcprogrammablefuzes.ProjectilePersistence;
import ua.ivan.cbcprogrammablefuzes.CorrectionTelemetryRegistry;
import ua.ivan.cbcprogrammablefuzes.InFlightFuzeRegistry;
import ua.ivan.cbcprogrammablefuzes.ProgrammableFuzeItem;

@Mixin(Entity.class)
abstract class EntityRemovalMixin {
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void programmableFuze$keepProjectileUntilDetonation(Entity.RemovalReason reason, CallbackInfo ci) {
        if (ProjectilePersistence.preventRemoval((Entity) (Object) this, reason)) {
            ci.cancel();
        }
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void cbcProgrammableFuzes$forgetTelemetry(Entity.RemovalReason reason, CallbackInfo ci) {
        CorrectionTelemetryRegistry.remove((Entity) (Object) this);
        InFlightFuzeRegistry.remove((Entity) (Object) this);
        ProgrammableFuzeItem.forgetPhysical((Entity) (Object) this);
    }
}
