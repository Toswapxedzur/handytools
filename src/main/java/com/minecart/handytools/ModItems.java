package com.minecart.handytools;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(HandyTools.MOD_ID);

    public static final DeferredItem<Item> WOODEN_HAMMER =
            registerHammer("wooden_hammer", Tiers.WOOD, false);
    public static final DeferredItem<Item> COBBLESTONE_HAMMER =
            registerHammer("cobblestone_hammer", Tiers.STONE, false);
    public static final DeferredItem<Item> IRON_HAMMER =
            registerHammer("iron_hammer", Tiers.IRON, false);
    public static final DeferredItem<Item> GOLD_HAMMER =
            registerHammer("gold_hammer", Tiers.GOLD, false);
    public static final DeferredItem<Item> DIAMOND_HAMMER =
            registerHammer("diamond_hammer", Tiers.DIAMOND, false);
    public static final DeferredItem<Item> NETHERITE_HAMMER =
            registerHammer("netherite_hammer", Tiers.NETHERITE, true);

    public static final List<DeferredItem<Item>> HAMMERS = List.of(
            WOODEN_HAMMER,
            COBBLESTONE_HAMMER,
            IRON_HAMMER,
            GOLD_HAMMER,
            DIAMOND_HAMMER,
            NETHERITE_HAMMER
    );

    private ModItems() {
    }

    private static DeferredItem<Item> registerHammer(
            String name,
            Tier tier,
            boolean fireResistant
    ) {
        return ITEMS.register(name, () -> {
            Item.Properties properties = new Item.Properties().durability(tier.getUses());
            if (fireResistant) {
                properties.fireResistant();
            }
            return new Item(properties);
        });
    }
}
