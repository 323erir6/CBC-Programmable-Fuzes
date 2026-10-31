package ua.ivan.cbcprogrammablefuzes;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import rbasamoyai.createbigcannons.munitions.big_cannon.BigCannonProjectileBlockEntity;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock;

public final class TrajectoryCorrectionModuleItem extends Item {
    public TrajectoryCorrectionModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CorrectionSettings settings = CorrectionSettings.read(stack);
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.trajectory_correction_module.channel",
            settings.channel()).withStyle(ChatFormatting.DARK_PURPLE));
        if (settings.hasTarget()) {
            tooltip.add(Component.translatable(
                "item.cbc_programmable_fuzes.trajectory_correction_module.target",
                format(settings.targetX()), format(settings.targetY()), format(settings.targetZ()))
                .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                "item.cbc_programmable_fuzes.trajectory_correction_module.no_target")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.trajectory_correction_module.install")
            .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(
            "item.cbc_programmable_fuzes.trajectory_correction_module.persistent",
            settings.persistentProjectile()).withStyle(ChatFormatting.GRAY));
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState state = context.getLevel().getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof ProjectileBlock<?> projectileBlock)
            || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof BigCannonProjectileBlockEntity blockEntity)
            || !((Object) blockEntity instanceof CorrectionModuleHolder holder)) {
            return InteractionResult.PASS;
        }
        Direction tail = state.getValue(ProjectileBlock.FACING).getOpposite();
        if (context.getClickedFace() != tail || holder.cbcProgrammableFuzes$hasCorrectionModule()) {
            return InteractionResult.PASS;
        }

        if (!context.getLevel().isClientSide) {
            ItemStack source = context.getItemInHand();
            ItemStack installed = context.getPlayer() != null && context.getPlayer().getAbilities().instabuild
                ? source.copyWithCount(1) : source.split(1);
            if (CorrectionSettings.readCarrier(installed) == null) {
                CorrectionSettings.write(installed, CorrectionSettings.defaults());
            }
            holder.cbcProgrammableFuzes$setCorrectionModule(installed);
            blockEntity.setChanged();
            blockEntity.notifyUpdate();
            context.getLevel().invalidateCapabilities(context.getClickedPos());
            context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_ADD_ITEM,
                SoundSource.NEUTRAL, 0.8f, 1.15f);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
