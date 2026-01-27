package org.betterx.datagen.betterend;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonElement;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataResolver;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Loot table provider that keeps output JSON compatible with 1.20.1.
 */
public class CompatLootTableProvider extends LootTableProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput.PathProvider pathProvider;

    public CompatLootTableProvider(
            PackOutput output,
            Set<ResourceLocation> requiredTables,
            List<SubProviderEntry> subProviders
    ) {
        super(output, requiredTables, subProviders);
        this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "loot_tables");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cachedOutput) {
        final Map<ResourceLocation, LootTable> tables = Maps.newHashMap();
        Map<RandomSupport.Seed128bit, ResourceLocation> sequences = new Object2ObjectOpenHashMap<>();

        this.getTables().forEach((entry) -> {
            entry.provider().get().generate((id, builder) -> {
                ResourceLocation previous = sequences.put(RandomSequence.seedForKey(id), id);
                if (previous != null) {
                    Util.logAndPauseIfInIde("Loot table random sequence seed collision on " + previous + " and " + id);
                }

                builder.setRandomSequence(id);
                if (tables.put(id, builder.setParamSet(entry.paramSet()).build()) != null) {
                    throw new IllegalStateException("Duplicate loot table " + id);
                }
            });
        });

        ValidationContext validationContext = new ValidationContext(LootContextParamSets.ALL_PARAMS, new LootDataResolver() {
            @Nullable
            @Override
            public <T> T getElement(LootDataId<T> id) {
                return id.type() == LootDataType.TABLE ? (T) tables.get(id.location()) : null;
            }
        });

        validate(tables, validationContext);

        Multimap<String, String> problems = validationContext.getProblems();
        if (!problems.isEmpty()) {
            problems.forEach((table, problem) -> LOGGER.warn("Found validation problem in {}: {}", table, problem));
            throw new IllegalStateException("Failed to validate loot tables, see logs");
        }

        return CompletableFuture.allOf(tables.entrySet().stream().map((entry) -> {
            ResourceLocation id = entry.getKey();
            LootTable table = entry.getValue();
            Path path = this.pathProvider.json(id);
            JsonElement json = LootDataType.TABLE.parser().toJsonTree(table);
            LootTableCompatFixer.fix(json);
            return DataProvider.saveStable(cachedOutput, json, path);
        }).toArray(CompletableFuture[]::new));
    }
}
