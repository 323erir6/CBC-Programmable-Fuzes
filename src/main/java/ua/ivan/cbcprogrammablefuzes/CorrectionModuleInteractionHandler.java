package ua.ivan.cbcprogrammablefuzes;

import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;

/** Handles installation before CBC's block interaction can reject the non-fuze item, and also provides a
 * common removal fallback for projectiles without a fuze-specific useWithoutItem implementation. */
public final class CorrectionModuleInteractionHandler {
    private CorrectionModuleInteractionHandler() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof ProjectileBlock<?>)) return;
        Direction tail = state.getValue(ProjectileBlock.FACING).getOpposite();
        if (event.getFace() != tail
            || !(level.getBlockEntity(event.getPos()) instanceof BigCannonProjectileBlockEntity blockEntity)
            || !((Object) blockEntity instanceof CorrectionModuleHolder holder)) return;

        ItemStack held = event.getItemStack();
        if (held.is(CBCProgrammableFuzes.TRAJECTORY_CORRECTION_MODULE.get())) {
            if (holder.cbcProgrammableFuzes$hasCorrectionModule()) return;
            if (!level.isClientSide) {
                ItemStack installed = player.getAbilities().instabuild
                    ? held.copyWithCount(1) : held.split(1);
                if (CorrectionSettings.readCarrier(installed) == null) {
                    CorrectionSettings.write(installed, CorrectionSettings.defaults());
                }
                holder.cbcProgrammableFuzes$setCorrectionModule(installed);
                blockEntity.setChanged();
                blockEntity.notifyUpdate();
                level.invalidateCapabilities(event.getPos());
                level.playSound(null, event.getPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                    SoundSource.NEUTRAL, 0.8f, 1.15f);
            }
            finish(event, level);
            return;
        }

        if (!player.isShiftKeyDown() || !held.isEmpty()
            || !holder.cbcProgrammableFuzes$hasCorrectionModule()) return;

        if (!level.isClientSide) {
            ItemStack removed = holder.cbcProgrammableFuzes$getCorrectionModule();
            holder.cbcProgrammableFuzes$setCorrectionModule(ItemStack.EMPTY);
            blockEntity.setChanged();
            blockEntity.notifyUpdate();
            level.invalidateCapabilities(event.getPos());
            if (!player.getAbilities().instabuild && !player.addItem(removed)) {
                ItemEntity dropped = player.drop(removed, false);
                if (dropped != null) dropped.setNoPickUpDelay();
            }
            level.playSound(null, event.getPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.NEUTRAL, 0.8f, 1.15f);
        }

        finish(event, level);
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event, Level level) {
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }
}
