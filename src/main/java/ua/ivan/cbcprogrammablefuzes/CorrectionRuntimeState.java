package ua.ivan.cbcprogrammablefuzes;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;

/** Transient per-projectile cache; persistent flight state remains stored in entity NBT. */
public interface CorrectionRuntimeState {
    byte cbcProgrammableFuzes$getCorrectionState();

    void cbcProgrammableFuzes$setCorrectionState(byte state, @Nullable CompoundTag data);

    @Nullable
    CompoundTag cbcProgrammableFuzes$getCorrectionData();
}
