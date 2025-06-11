package com.garsooon.goldtoolsutil;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class GoldToolUtils extends JavaPlugin {

    private Properties config = new Properties();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        loadConfig();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Type.BLOCK_BREAK, new BlockListenerImpl(), Priority.Normal, this);
        pm.registerEvent(Type.BLOCK_DAMAGE, new BlockListenerImpl(), Priority.Normal, this);
        pm.registerEvent(Type.ENTITY_DAMAGE, new GoldToolsEntityListener(), Priority.Normal, this);

        System.out.println("[GoldToolUtils] Enabled");
    }

    @Override
    public void onDisable() {
        System.out.println("[GoldToolUtils] Disabled");
    }

    // Creates the properties file.
    // NOTE TO SERVER OWNERS: EDIT THE VALUES IN Plugins/GoldToolUtils/config.properties
    //You do not need to compile the plugin to change these values.
    private void loadConfig() {
        File file = new File(getDataFolder(), "config.properties");

        if (!file.exists()) {
            try {
                getDataFolder().mkdirs();
                OutputStream out = new FileOutputStream(file);
                config.setProperty("shovel-bonus-drops.COAL", "5");
                config.setProperty("shovel-bonus-drops.IRON_INGOT", "3");
                config.setProperty("shovel-bonus-drops.GOLD_INGOT", "2");
                config.setProperty("shovel-bonus-drops.DIAMOND", "1");
                config.setProperty("shovel-bonus-drops.REDSTONE", "4");
                config.setProperty("shovel-bonus-drops.LAPIS_LAZULI", "2");
                config.setProperty("shovel-bonus-chance", "20");
                config.setProperty("sword-damage-value", "14");
                config.store(out, null);
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (InputStream in = new FileInputStream(file)) {
            config.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Sword Logic (Type check + event listener)
    // Gold Sword Damage logic / Listener
    private class GoldToolsEntityListener extends EntityListener {
        public void onEntityDamage(EntityDamageEvent event) {
            if (!isWeekend()) return; // Exit if not the weekend

            if (!(event instanceof EntityDamageByEntityEvent)) return;

            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) event;

            if (!(damageEvent.getDamager() instanceof Player)) return;

            Player player = (Player) damageEvent.getDamager();
            ItemStack weapon = player.getItemInHand();

            if (weapon == null || weapon.getType() != Material.GOLD_SWORD) return;

            if (isWeekend()) {
                int damage = 14; // default damage value
                try {
                    damage = Integer.parseInt(config.getProperty("sword-damage-value", "14"));
                } catch (NumberFormatException ignored) {}

                damageEvent.setDamage(damage);
            }
        }
    }

    // Tool Logic: Pickaxe, Shovel, Axe, Hoe

    // Pickaxe related material check and drop methods
    private boolean isOre(Material type) {
        return type == Material.COAL_ORE || type == Material.IRON_ORE ||
                type == Material.GOLD_ORE || type == Material.DIAMOND_ORE ||
                type == Material.REDSTONE_ORE || type == Material.GLOWING_REDSTONE_ORE ||
                type == Material.LAPIS_ORE;
    }

    private ItemStack getOreDrop(Material ore) {
        switch (ore) {
            case COAL_ORE: return new ItemStack(Material.COAL, 1);
            case IRON_ORE: return new ItemStack(Material.IRON_INGOT, 2);
            case GOLD_ORE: return new ItemStack(Material.GOLD_INGOT, 2);
            case DIAMOND_ORE: return new ItemStack(Material.DIAMOND, 2);
            case REDSTONE_ORE:
            case GLOWING_REDSTONE_ORE: return new ItemStack(Material.REDSTONE, 5 + random.nextInt(2));
            case LAPIS_ORE:
                ItemStack lapis = new ItemStack(Material.INK_SACK, 5 + random.nextInt(5));
                lapis.setDurability((short) 4);
                return lapis;
            default: return null;
        }
    }

    // Shovel related material check and bonus drop logic
    private boolean isShovelMaterial(Material type) {
        return type == Material.DIRT || type == Material.GRASS ||
                type == Material.SAND || type == Material.GRAVEL ||
                type == Material.CLAY || type == Material.SOUL_SAND;
    }

    private ItemStack getWeightedBonusDrop() {
        Map<String, Integer> weights = new HashMap<>();

        for (Map.Entry<Object, Object> entry : config.entrySet()) {
            String key = (String) entry.getKey();
            if (key.startsWith("shovel-bonus-drops.")) {
                String materialName = key.substring("shovel-bonus-drops.".length());
                try {
                    int weight = Integer.parseInt((String) entry.getValue());
                    weights.put(materialName, weight);
                } catch (NumberFormatException ignored) {}
            }
        }

        int totalWeight = weights.values().stream().mapToInt(i -> i).sum();
        if (totalWeight == 0) return null;

        int r = random.nextInt(totalWeight);
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            r -= entry.getValue();
            if (r < 0) {
                Material mat = Material.getMaterial(entry.getKey());
                if (mat == null) continue;
                ItemStack drop = new ItemStack(mat, 1);
                if (mat == Material.INK_SACK) drop.setDurability((short) 4);
                return drop;
            }
        }

        return null;
    }

    // Axe related material check
    private boolean isLog(Material type) {
        return type == Material.LOG;
    }

    private class BlockListenerImpl extends BlockListener {
        @Override
        public void onBlockDamage(BlockDamageEvent event) {
            Player player = event.getPlayer();
            ItemStack tool = player.getItemInHand();
            Block block = event.getBlock();

            if (tool == null) return;

            // Pickaxe insta break for ores
            if (tool.getType() == Material.GOLD_PICKAXE && isOre(block.getType())) {
                event.setInstaBreak(true);
            }
        }

        @Override
        public void onBlockBreak(BlockBreakEvent event) {
            Player player = event.getPlayer();
            ItemStack tool = player.getItemInHand();
            Block block = event.getBlock();

            if (tool == null) return;

            // Pickaxe logic for insta mined ores
            if (tool.getType() == Material.GOLD_PICKAXE && isOre(block.getType())) {
                ItemStack drop = getOreDrop(block.getType());
                if (drop != null) {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
                tool.setDurability((short) (tool.getDurability() + 1));
                return;
            }

            // Shovel logic
            if (tool.getType() == Material.GOLD_SPADE && isShovelMaterial(block.getType())) {
                int chance = Integer.parseInt(config.getProperty("shovel-bonus-chance", "10"));
                if (random.nextInt(100) < chance) {
                    ItemStack bonus = getWeightedBonusDrop();
                    if (bonus != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), bonus);
                    }
                }
                tool.setDurability((short) (tool.getDurability() + 1));
                return;
            }

            // Axe logic - convert logs to charcoal
            else if (tool.getType() == Material.GOLD_AXE) {
                if (isLog(block.getType())) {
                    event.setCancelled(true);
                    block.setType(Material.AIR);

                    ItemStack charcoal = new ItemStack(Material.COAL, 1);
                    charcoal.setDurability((short) 1); // Charcoal variant
                    block.getWorld().dropItemNaturally(block.getLocation(), charcoal);
                    return;
                }
            }

            // Hoe logic - triple wheat yield
            else if (tool.getType() == Material.GOLD_HOE) {
                if (block.getType() == Material.CROPS) {
                    if (block.getData() == 7) { // fully grown wheat
                        event.setCancelled(true);
                        block.setType(Material.AIR);

                        ItemStack wheat = new ItemStack(Material.WHEAT, 3);
                        ItemStack seeds = new ItemStack(Material.SEEDS, random.nextInt(3) + 1);

                        block.getWorld().dropItemNaturally(block.getLocation(), wheat);
                        block.getWorld().dropItemNaturally(block.getLocation(), seeds);
                        return;
                    }
                }
            }
        }
    }

    //Self explanatory.
    private boolean isWeekend() {
        Calendar calendar = Calendar.getInstance();
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY;
    }
}
