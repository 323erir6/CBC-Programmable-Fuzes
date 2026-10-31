package ua.ivan.cbcprogrammablefuzes.peripheral;

import net.minecraft.world.item.ItemStack;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileBlockEntity;
import ua.ivan.cbcprogrammablefuzes.CBCProgrammableFuzes;
import ua.ivan.cbcprogrammablefuzes.CorrectionModuleHolder;

public final class PlacedModuleAccess implements ProgrammableFuzeAccess {
    private final BigCannonProjectileBlockEntity blockEntity;

    public PlacedModuleAccess(BigCannonProjectileBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public ItemStack programmableFuze$getFuze() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean programmableFuze$setFuze(ItemStack stack) {
        return false;
    }

    @Override
    public ItemStack programmableFuze$getCorrectionModule() {
        return !blockEntity.isRemoved() && blockEntity instanceof CorrectionModuleHolder holder
            ? holder.cbcProgrammableFuzes$getCorrectionModule() : ItemStack.EMPTY;
    }

    @Override
    public boolean programmableFuze$setCorrectionModule(ItemStack stack) {
        if (blockEntity.isRemoved() || !(blockEntity instanceof CorrectionModuleHolder holder)
            || !stack.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) return false;
        holder.cbcProgrammableFuzes$setCorrectionModule(stack.copyWithCount(1));
        blockEntity.setChanged();
        blockEntity.notifyUpdate();
        return true;
    }
}
