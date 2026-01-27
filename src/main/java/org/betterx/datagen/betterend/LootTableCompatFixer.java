package org.betterx.datagen.betterend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fixes loot table JSON to be compatible with 1.20.1.
 */
public final class LootTableCompatFixer {
    private LootTableCompatFixer() {
    }

    public static boolean fix(JsonElement element) {
        return fixElement(element);
    }

    private static boolean fixElement(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonObject()) {
            return fixObject(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            return fixArray(element.getAsJsonArray());
        }
        return false;
    }

    private static boolean fixObject(JsonObject obj) {
        boolean changed = false;

        // Fix match_tool predicates.
        if (obj.has("condition") && "minecraft:match_tool".equals(asString(obj.get("condition")))) {
            JsonObject predicate = asObject(obj.get("predicate"));
            if (predicate != null) {
                changed |= fixPredicate(predicate);
            }
        }

        // Recurse into members.
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            changed |= fixElement(entry.getValue());
        }

        return changed;
    }

    private static boolean fixArray(JsonArray array) {
        boolean changed = false;
        JsonArray rebuilt = null;

        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            JsonObject functionObj = asObject(element);
            if (functionObj != null && "minecraft:copy_components".equals(asString(functionObj.get("function")))) {
                if (rebuilt == null) {
                    rebuilt = new JsonArray();
                    for (int j = 0; j < i; j++) rebuilt.add(array.get(j));
                }
                rebuilt.addAll(convertCopyComponents(functionObj));
                changed = true;
                continue;
            }

            if (rebuilt != null) rebuilt.add(element);
            changed |= fixElement(element);
        }

        if (rebuilt != null) {
            while (array.size() > 0) {
                array.remove(array.size() - 1);
            }
            rebuilt.forEach(array::add);
        }

        return changed;
    }

    private static boolean fixPredicate(JsonObject predicate) {
        boolean changed = false;

        // items: "minecraft:foo" -> items: ["minecraft:foo"] or tag: "namespace:tag"
        if (predicate.has("items")) {
            JsonElement items = predicate.get("items");
            if (items.isJsonPrimitive()) {
                String value = items.getAsString();
                predicate.remove("items");
                if (value.startsWith("#")) {
                    predicate.addProperty("tag", value.substring(1));
                } else {
                    JsonArray array = new JsonArray();
                    array.add(value);
                    predicate.add("items", array);
                }
                changed = true;
            } else if (items.isJsonArray() && items.getAsJsonArray().size() == 1) {
                JsonElement only = items.getAsJsonArray().get(0);
                if (only.isJsonPrimitive()) {
                    String value = only.getAsString();
                    if (value.startsWith("#")) {
                        predicate.remove("items");
                        predicate.addProperty("tag", value.substring(1));
                        changed = true;
                    }
                }
            }
        }

        // predicates.minecraft:enchantments -> enchantments (1.20.1 format)
        JsonObject predicates = asObject(predicate.get("predicates"));
        if (predicates != null && predicates.has("minecraft:enchantments")) {
            JsonArray ench = predicates.getAsJsonArray("minecraft:enchantments");
            for (JsonElement entry : ench) {
                JsonObject enchObj = asObject(entry);
                if (enchObj != null && enchObj.has("enchantments")) {
                    JsonElement value = enchObj.remove("enchantments");
                    enchObj.add("enchantment", value);
                    changed = true;
                }
            }
            predicate.remove("predicates");
            predicate.add("enchantments", ench);
            changed = true;
        }

        return changed;
    }

    private static JsonArray convertCopyComponents(JsonObject original) {
        List<String> include = new ArrayList<>();
        JsonArray includeArr = original.has("include") && original.get("include").isJsonArray()
                ? original.getAsJsonArray("include")
                : null;
        if (includeArr != null) {
            for (JsonElement el : includeArr) {
                if (el.isJsonPrimitive()) {
                    include.add(el.getAsString());
                }
            }
        }

        JsonArray out = new JsonArray();

        if (include.contains("minecraft:custom_name")) {
            JsonObject copyName = new JsonObject();
            copyName.addProperty("function", "minecraft:copy_name");
            copyName.addProperty("source", "block_entity");
            out.add(copyName);
        }

        JsonArray ops = new JsonArray();
        if (include.contains("minecraft:container")) {
            ops.add(copyNbtOp("Items", "BlockEntityTag.Items"));
        }
        if (include.contains("minecraft:lock")) {
            ops.add(copyNbtOp("Lock", "BlockEntityTag.Lock"));
        }
        if (include.contains("minecraft:container_loot")) {
            ops.add(copyNbtOp("LootTable", "BlockEntityTag.LootTable"));
            ops.add(copyNbtOp("LootTableSeed", "BlockEntityTag.LootTableSeed"));
        }

        if (!ops.isEmpty()) {
            JsonObject copyNbt = new JsonObject();
            copyNbt.addProperty("function", "minecraft:copy_nbt");
            copyNbt.addProperty("source", "block_entity");
            copyNbt.add("ops", ops);
            out.add(copyNbt);
        }

        return out;
    }

    private static JsonObject copyNbtOp(String source, String target) {
        JsonObject op = new JsonObject();
        op.addProperty("source", source);
        op.addProperty("target", target);
        op.addProperty("op", "replace");
        return op;
    }

    private static JsonObject asObject(JsonElement element) {
        return (element != null && element.isJsonObject()) ? element.getAsJsonObject() : null;
    }

    private static String asString(JsonElement element) {
        return (element != null && element.isJsonPrimitive()) ? element.getAsString() : null;
    }
}
