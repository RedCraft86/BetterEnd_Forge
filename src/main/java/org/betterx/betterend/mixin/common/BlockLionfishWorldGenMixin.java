package org.betterx.betterend.mixin.common;

import net.minecraft.server.MinecraftServer;
import org.betterx.betterend.world.generator.GeneratorOptions;
import com.github.L_Ender.lionfishapi.server.world.ModdedBiomeSlicesManager;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModdedBiomeSlicesManager.class)
public class BlockLionfishWorldGenMixin {
    @Unique private static final Logger BE_LOGGER = LogUtils.getLogger();

    @Inject(method = "onServerAboutToStart", at = @At("HEAD"), cancellable = true, remap = false)
    private static void blockBiomeSlices(MinecraftServer server, CallbackInfo ci) {
        if (GeneratorOptions.blockLionfishAPI()) {
            BE_LOGGER.info("[BetterEnd] Stopping Lionfish API's biome system from breaking custom end terrain gen.");
            BE_LOGGER.info("If you do not want this, disable it in BetterEnd's generator config.");
            ci.cancel();
        }
    }
}
