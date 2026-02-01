package org.betterx.betterend.mixin.common;

import java.util.HashMap;
import java.util.ArrayList;
import com.mojang.datafixers.util.Pair;

import com.github.L_Ender.lionfishapi.server.world.ModdedBiomeSlice;
import com.github.L_Ender.lionfishapi.server.world.ModdedBiomeSlicesManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.LevelStem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ModdedBiomeSlicesManager.class, remap = false)
public class BlockLionfishWorldGenMixin {
    @Redirect(method = "onServerAboutToStart",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/HashMap;get(Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private static Object filterOutEndSlices(
            HashMap<ResourceLocation, ArrayList<Pair<ResourceLocation, ModdedBiomeSlice>>> assignedSlices,
            Object locationObj
    ) {

        ResourceLocation location = (ResourceLocation) locationObj;
        return location.equals(LevelStem.END.location()) ? null : assignedSlices.get(location);
    }
}
