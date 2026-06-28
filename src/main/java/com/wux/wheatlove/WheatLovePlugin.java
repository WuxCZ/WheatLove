package com.wux.wheatlove;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;

/**
 * Wheat Love (combo: datapack + plugin).
 *
 * Eat wheat -> love mode (hearts). Right-click another loving player to spread it.
 * Two loving entities pull together and spawn a baby named "<player> Junior":
 *  - player + player => a small humanoid baby (mini-player) wearing the parent's head
 *  - player + animal (also in love mode) => a baby of that animal, named after the player
 * Breeding drops a little XP and puts the player (and animal) on a cooldown, just like
 * vanilla animal breeding.
 */
public class WheatLovePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Long> readyUntilMs = new HashMap<>();
    private final Map<UUID, Long> lastFeedMs = new HashMap<>();
    private final Map<UUID, Long> breedCooldownUntil = new HashMap<>();
    private final Random rng = new Random();
    private BukkitTask tickTask;

    private static final long LOVE_MS = 8000L;
    private static final long COOLDOWN_MS = 300_000L;   // 5 min, stejně jako zvíře
    private static final String LOVE_TAG = "wl_loving";

    private static final EnumSet<EntityType> SUPPORTED_ANIMALS = EnumSet.of(
        EntityType.COW,
        EntityType.PIG,
        EntityType.SHEEP,
        EntityType.CHICKEN,
        EntityType.RABBIT,
        EntityType.WOLF,
        EntityType.CAT,
        EntityType.HORSE,
        EntityType.DONKEY,
        EntityType.GOAT,
        EntityType.LLAMA,
        EntityType.MOOSHROOM
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tickLogic, 1L, 1L);
        getLogger().info("WheatLovePlugin enabled (combo datapack+plugin)");
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.removeScoreboardTag(LOVE_TAG);
        }
        readyUntilMs.clear();
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleFeed(event, event.getPlayer(), event.getRightClicked(), event.getHand());
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleFeed(event, event.getPlayer(), event.getRightClicked(), event.getHand());
    }

    private void handleFeed(Cancellable event, Player clicker, Entity clicked, EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND || !(clicked instanceof Player target)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = readyUntilMs.get(clicker.getUniqueId());
        if (until == null || until < now) {
            return;
        }
        event.setCancelled(true);
        Long last = lastFeedMs.get(clicker.getUniqueId());
        if (last != null && now - last < 250) {
            return;
        }
        lastFeedMs.put(clicker.getUniqueId(), now);
        startLove(target, now);
    }

    private void tickLogic() {
        long now = System.currentTimeMillis();

        Objective obj = Bukkit.getScoreboardManager() == null ? null
            : Bukkit.getScoreboardManager().getMainScoreboard().getObjective("wl_eat");
        if (obj != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Score sc = obj.getScore(p.getName());
                if (sc.isScoreSet() && sc.getScore() >= 1) {
                    sc.setScore(0);
                    startLove(p, now);
                }
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            Long until = readyUntilMs.get(p.getUniqueId());
            if (until == null || until < now || p.isDead()) {
                if (readyUntilMs.remove(p.getUniqueId()) != null) {
                    p.removeScoreboardTag(LOVE_TAG);
                }
                continue;
            }

            stripWheatEdibility(p);
            spawnHearts(p.getLocation().add(0, 1.8, 0), 2);

            // Player + player => mini-player baby
            Player partner = findNearestReadyPlayer(p, 8.0);
            if (partner != null) {
                moveTowardsEachOther(p, partner, 0.07);
                spawnHearts(partner.getLocation().add(0, 1.8, 0), 2);
                if (p.getLocation().distanceSquared(partner.getLocation()) <= (1.8 * 1.8)) {
                    spawnBaby(midpoint(p.getLocation(), partner.getLocation()), EntityType.ZOMBIE, p, null, true);
                    breedCooldownUntil.put(p.getUniqueId(), now + COOLDOWN_MS);
                    breedCooldownUntil.put(partner.getUniqueId(), now + COOLDOWN_MS);
                    endLove(p);
                    endLove(partner);
                }
                continue;
            }

            // Player + animal (animal must also be in love mode)
            Animals animal = findNearestLoveAnimal(p, 8.0);
            if (animal != null) {
                moveTowardsEachOther(p, animal, 0.07);
                spawnHearts(animal.getLocation().add(0, 1.0, 0), 3);
                if (p.getLocation().distanceSquared(animal.getLocation()) <= (2.0 * 2.0)) {
                    spawnBaby(midpoint(p.getLocation(), animal.getLocation()), animal.getType(), p, animal, false);
                    breedCooldownUntil.put(p.getUniqueId(), now + COOLDOWN_MS);
                    animal.setLoveModeTicks(0);
                    if (animal instanceof Ageable aa) {
                        aa.setAge(6000);   // zvíře dostane normální breed cooldown
                    }
                    endLove(p);
                }
            }
        }
    }

    private void startLove(Player p, long now) {
        Long cd = breedCooldownUntil.get(p.getUniqueId());
        if (cd != null && cd > now) {
            p.sendActionBar(Component.text("Jeste jsi unaveny po pareni (" + ((cd - now) / 1000) + "s)"));
            return;
        }
        readyUntilMs.put(p.getUniqueId(), now + LOVE_MS);
        p.addScoreboardTag(LOVE_TAG);
        stripWheatEdibility(p);
        spawnHearts(p.getLocation().add(0, 1.8, 0), 12);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.6f, 1.4f);
    }

    private void endLove(Player p) {
        readyUntilMs.remove(p.getUniqueId());
        p.removeScoreboardTag(LOVE_TAG);
    }

    private void stripWheatEdibility(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main.getType() == Material.WHEAT && main.hasItemMeta() && main.getItemMeta().hasFood()) {
            p.getInventory().setItemInMainHand(new ItemStack(Material.WHEAT, main.getAmount()));
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off.getType() == Material.WHEAT && off.hasItemMeta() && off.getItemMeta().hasFood()) {
            p.getInventory().setItemInOffHand(new ItemStack(Material.WHEAT, off.getAmount()));
        }
    }

    private Player findNearestReadyPlayer(Player self, double radius) {
        Player best = null;
        double bestDist = radius * radius;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(self.getUniqueId())) {
                continue;
            }
            Long until = readyUntilMs.get(p.getUniqueId());
            if (until == null || until < now || p.isDead() || !p.getWorld().equals(self.getWorld())) {
                continue;
            }
            double d = p.getLocation().distanceSquared(self.getLocation());
            if (d < bestDist && d > 0.04) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private Animals findNearestLoveAnimal(Player p, double radius) {
        Animals best = null;
        double bestDist = radius * radius;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Animals a) || !SUPPORTED_ANIMALS.contains(a.getType())) {
                continue;
            }
            if (!a.isLoveMode()) {
                continue;
            }
            if (a instanceof Ageable ageable && !ageable.isAdult()) {
                continue;
            }
            double d = a.getLocation().distanceSquared(p.getLocation());
            if (d < bestDist) {
                bestDist = d;
                best = a;
            }
        }
        return best;
    }

    private void moveTowardsEachOther(LivingEntity a, LivingEntity b, double strength) {
        Vector dir = b.getLocation().toVector().subtract(a.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() <= 0.04) {
            return;
        }
        Vector unit = dir.normalize();
        a.setVelocity(a.getVelocity().multiply(0.6).add(unit.clone().multiply(strength)));
        b.setVelocity(b.getVelocity().multiply(0.6).add(unit.multiply(-strength)));
    }

    private void spawnBaby(Location loc, EntityType type, Player namedAfter, Animals source, boolean playerBaby) {
        Entity e = loc.getWorld().spawnEntity(loc, type);
        if (e instanceof Ageable ageable) {
            ageable.setBaby();
        }
        if (e instanceof Horse foal && source instanceof Horse parent) {
            foal.setColor(parent.getColor());
            foal.setStyle(parent.getStyle());
            foal.setJumpStrength(parent.getJumpStrength());
        }
        if (e instanceof Sheep lamb && source instanceof Sheep parentSheep) {
            lamb.setColor(parentSheep.getColor());
        }
        if (playerBaby && e instanceof Zombie z) {
            z.setBaby();                       // malé (humanoidní) tělo
            z.setShouldBurnInDay(false);       // nehoří na slunci
            z.setAware(false);                 // pasivní - neútočí na rodiče
            z.setRemoveWhenFarAway(false);     // nezmizí
            if (z.getEquipment() != null) {
                z.getEquipment().setHelmet(playerHeadItem(namedAfter));  // hlava SEDÍ na humanoidní hlavě
                z.getEquipment().setHelmetDropChance(0f);
            }
        }
        if (e instanceof LivingEntity le) {
            le.customName(Component.text(namedAfter.getName() + " Junior"));
            le.setCustomNameVisible(true);
        }
        // XP jako při normálním množení (1-7)
        ExperienceOrb orb = loc.getWorld().spawn(loc.clone().add(0, 0.4, 0), ExperienceOrb.class);
        orb.setExperience(1 + rng.nextInt(7));

        spawnHearts(loc.clone().add(0, 1.0, 0), 14);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
    }

    private ItemStack playerHeadItem(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        if (sm != null) {
            sm.setOwningPlayer(player);
            head.setItemMeta(sm);
        }
        return head;
    }

    private Location midpoint(Location a, Location b) {
        return new Location(a.getWorld(),
            (a.getX() + b.getX()) / 2.0,
            (a.getY() + b.getY()) / 2.0,
            (a.getZ() + b.getZ()) / 2.0);
    }

    private void spawnHearts(Location loc, int count) {
        loc.getWorld().spawnParticle(Particle.HEART, loc, count, 0.35, 0.35, 0.35, 0.02);
    }
}
