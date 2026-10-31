package ua.ivan.cbcprogrammablefuzes.mixin;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;
import ua.ivan.cbcprogrammablefuzes.CorrectionFlightController;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleHolder;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleTransfer;
import ua.ivan.cbcprogrammablefuzes.CorrectionSettings;

@Mixin(ProjectileBlock.class)
abstract class ProjectileBlockMixin {
    @Inject(method = "getHandloadingInfo", at = @At("RETURN"), cancellable = true)
    private void cbcProgrammableFuzes$handloadingTail(ItemStack stack, BlockPos localPos,
                                                       Direction orientation, HolderLookup.Provider registries,
                                                       CallbackInfoReturnable<StructureBlockInfo> cir) {
        CorrectionSettings settings = CorrectionSettings.readCarrier(stack);
        StructureBlockInfo info = cir.getReturnValue();
        if (settings == null || info.nbt() == null) return;
        CompoundTag nbt = info.nbt().copy();
        ItemStack module = new ItemStack(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get());
        CorrectionSettings.write(module, settings);
        nbt.put("TailModule", module.saveOptional(registries));
        cir.setReturnValue(new StructureBlockInfo(info.pos(), info.state(), nbt));
    }

    @Inject(method = "getExtractedItem", at = @At("RETURN"))
    private void cbcProgrammableFuzes$extractTail(StructureBlockInfo info, HolderLookup.Provider registries,
                                                   CallbackInfoReturnable<ItemStack> cir) {
        if (info.nbt() == null) return;
        ItemStack module = ItemStack.parseOptional(registries, info.nbt().getCompound("TailModule"));
        if (module.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) {
            CorrectionSettings.writeCarrier(cir.getReturnValue(), CorrectionSettings.read(module));
        }
    }

    @Inject(method = "getCloneItemStack", at = @At("RETURN"))
    private void cbcProgrammableFuzes$cloneTail(LevelReader level, BlockPos pos, BlockState state,
                                                 CallbackInfoReturnable<ItemStack> cir) {
        if (level.getBlockEntity(pos) instanceof CorrectionModuleHolder holder
            && holder.cbcProgrammableFuzes$hasCorrectionModule()) {
            CorrectionSettings.writeCarrier(cir.getReturnValue(),
                CorrectionSettings.read(holder.cbcProgrammableFuzes$getCorrectionModule()));
        }
    }

    @Inject(method = "getProjectile(Lnet/minecraft/world/level/Level;Ljava/util/List;)Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;",
        at = @At("RETURN"))
    private void cbcProgrammableFuzes$attachFromBlocks(Level level, List<StructureBlockInfo> blocks,
                                                        CallbackInfoReturnable<AbstractBigCannonProjectile> cir) {
        if (blocks.isEmpty() || blocks.get(0).nbt() == null) return;
        attach(cir.getReturnValue(),
            ItemStack.parseOptional(level.registryAccess(), blocks.get(0).nbt().getCompound("TailModule")));
    }

    @Inject(method = "getProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;",
        at = @At("RETURN"))
    private void cbcProgrammableFuzes$attachFromItem(Level level, ItemStack stack,
                                                      CallbackInfoReturnable<AbstractBigCannonProjectile> cir) {
        CorrectionSettings settings = CorrectionSettings.readCarrier(stack);
        if (settings != null) CorrectionFlightController.attach(cir.getReturnValue(), settings);
    }

    @Inject(method = "getProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lrbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile;",
        at = @At("RETURN"))
    private void cbcProgrammableFuzes$attachFromBlock(Level level, BlockPos pos, BlockState state,
                                                       CallbackInfoReturnable<AbstractBigCannonProjectile> cir) {
        if (level.getBlockEntity(pos) instanceof CorrectionModuleHolder holder) {
            attach(cir.getReturnValue(), holder.cbcProgrammableFuzes$getCorrectionModule());
        }
    }

    private static void attach(AbstractBigCannonProjectile projectile, ItemStack module) {
        CorrectionModuleTransfer.attach(projectile, module);
    }
}
