package com.kouki.riskyrage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class RiskyRage extends JavaPlugin implements Listener {

    private final HashMap<UUID, Integer> rageMap = new HashMap<>();
    private final HashMap<UUID, UUID> lastAttacker = new HashMap<>();
    private FileConfiguration cfg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cfg = getConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        startRageDecayTask();
        getLogger().info("Risky Rage enabled! Compatible with ViaVersion, ViaBackwards, ViaRewind.");
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        int maxRage = cfg.getInt("rage.max");
        int gainPerHit = cfg.getInt("rage.gain-per-hit");
        double transferPercent = cfg.getDouble("rage.transfer-percent");

        // Increase Rage for victim
        int newRage = rageMap.getOrDefault(victim.getUniqueId(), 0) + gainPerHit;
        rageMap.put(victim.getUniqueId(), Math.min(newRage, maxRage));
        updateActionBar(victim);

        // Track last attacker
        if (e.getDamager() instanceof Player attacker) {
            lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());

            // Transfer Rage to attacker
            int transferred = (int) (gainPerHit * transferPercent);
            int attackerRage = rageMap.getOrDefault(attacker.getUniqueId(), 0) + transferred;
            rageMap.put(attacker.getUniqueId(), Math.min(attackerRage, maxRage));
            updateActionBar(attacker);

            // Check Rage Combo
            boolean victimRaging = rageMap.getOrDefault(victim.getUniqueId(), 0) >= maxRage;
            boolean attackerRaging = rageMap.getOrDefault(attacker.getUniqueId(), 0) >= maxRage;

            if (victimRaging && attackerRaging) activateRageCombo(victim, attacker);
        }

        // Activate Rage for victim if full
        if (rageMap.get(victim.getUniqueId()) >= maxRage) activateRage(victim);
    }

    private void activateRage(Player player) {
        int strength = cfg.getInt("buffs.strength-level");
        int speed = cfg.getInt("buffs.speed-level");
        int jump = cfg.getInt("buffs.jump-level");

        player.sendMessage(ChatColor.RED + "You are in RAGE!");
        player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, strength, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, speed, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, jump, false, false));
    }

    private void activateRageCombo(Player p1, Player p2) {
        int baseStrength = cfg.getInt("buffs.strength-level");
        int baseSpeed = cfg.getInt("buffs.speed-level");
        int baseJump = cfg.getInt("buffs.jump-level");

        int comboStrength = (int) Math.ceil(baseStrength * 1.5);
        int comboSpeed = (int) Math.ceil(baseSpeed * 1.5);
        int comboJump = (int) Math.ceil(baseJump * 1.5);

        p1.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, comboStrength, false, false));
        p1.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, comboSpeed, false, false));
        p1.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, comboJump, false, false));

        p2.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, comboStrength, false, false));
        p2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, comboSpeed, false, false));
        p2.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, comboJump, false, false));

        p1.sendMessage(ChatColor.DARK_RED + "RAGE COMBO ACTIVATED!");
        p2.sendMessage(ChatColor.DARK_RED + "RAGE COMBO ACTIVATED!");
    }

    private void removeRageEffects(Player player) {
        player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.JUMP);
    }

    private void updateActionBar(Player player) {
        int rage = rageMap.getOrDefault(player.getUniqueId(), 0);
        player.sendActionBar(ChatColor.RED + "Rage: " + rage + "/" + cfg.getInt("rage.max"));
    }

    private void startRageDecayTask() {
        int decay = cfg.getInt("rage.decay-per-second");
        int healthLoss = cfg.getInt("buffs.health-loss-per-second");

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : rageMap.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) continue;

                    int currentRage = rageMap.get(uuid);
                    if (currentRage > 0) {
                        int newRage = currentRage - decay;
                        rageMap.put(uuid, Math.max(newRage, 0));
                        updateActionBar(player);

                        if (newRage < cfg.getInt("rage.max")) removeRageEffects(player);

                        // Check if in Rage Combo
                        boolean inCombo = lastAttacker.containsKey(uuid)
                                && rageMap.getOrDefault(lastAttacker.get(uuid), 0) >= cfg.getInt("rage.max")
                                && currentRage >= cfg.getInt("rage.max");

                        double loss = healthLoss;
                        if (inCombo) loss *= 2;

                        double newHealth = player.getHealth() - loss;
                        player.setHealth(Math.max(newHealth, 1));
                    }
                }
            }
        }.runTaskTimer(this, 20, 20); // every second
    }
}
