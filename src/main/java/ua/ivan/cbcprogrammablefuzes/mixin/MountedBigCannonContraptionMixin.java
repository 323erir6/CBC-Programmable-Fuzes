package ua.ivan.cbcprogrammablefuzes.mixin;

import java.util.List;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleTransfer;

/**
 * Covers addon projectile blocks which replace CBC's projectile factory and
 * therefore bypass the injections on ProjectileBlock itself.
 */
@Mixin(MountedBigCannonContraption.class)
abstract class MountedBigCannonContraptionMixin {
    @Redirect(
        method = "fireShot",
        at = @At(
            value = "INVOKE",
            target = "Lrbasamoyai/createbigcannons/munitions/big_cannon/ProjectileBlock;getProjectile(Lnet/minecraft/world/level/Level;Ljava/util/List;)Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;"
        )
    )
    private AbstractBigCannonProjectile cbcProgrammableFuzes$attachAddonTailModule(
            ProjectileBlock<?> block, Level level, List<StructureBlockInfo> projectileBlocks) {
        AbstractBigCannonProjectile projectile = block.getProjectile(level, projectileBlocks);
        CorrectionModuleTransfer.attachFromBlocks(
            projectile, projectileBlocks, level.registryAccess());
        return projectile;
    }
}
