package net.TheDgtl.AutoHeal;

import java.io.File;
import java.util.HashMap;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoHeal extends JavaPlugin {
	private Server server;
	private Logger log;
	private FileConfiguration newConfig;
	
	private HashMap<String, WorldData> worlds = new HashMap<String, WorldData>();
	WorldData defData = new WorldData();
	
	private PluginManager pm;
	
	@Override
	public void onEnable() {
		server = getServer();
		log = getServer().getLogger();
		newConfig = this.getConfig();
		pm = this.getServer().getPluginManager();
		
		loadConfig();
		server.getScheduler().scheduleSyncRepeatingTask(this, new HealRun(), 0L, 10L);
		
		pm.registerEvents(new entityListener(), this);
		
		log.info("AutoHeal v" + getDescription().getVersion() + ChatColor.GREEN + " Enabled");
	}
	
	@Override
	public void onDisable() {
		log.info("AutoHeal " + ChatColor.RED + "Disabled");
	}
	
	public void loadConfig() {
		// Clear config
		if (!worlds.isEmpty()) worlds.clear();
		
		// Load/save default config
		loadConfig(newConfig, defData);
		
		// Load world configs
		for (World world : server.getWorlds()) {
			loadConfig(world.getName());
		}
	}
	
	public WorldData loadConfig(String name) {
		WorldData newData = new WorldData();
		File fh = new File(this.getDataFolder().getPath() + File.separator + name);
		if (!fh.exists()) {
			loadConfig(newConfig, newData);
		} else {
			FileConfiguration conf = new YamlConfiguration();
			try {
				conf.load(fh);
				loadConfig(conf, newData);
			} catch (Exception e) {
				e.printStackTrace();
				log.warning("[AutoHeal] Could not load configuration for " + name + " using default.");
				loadConfig(newConfig, newData);
			}
		}
		worlds.put(name, newData);
		return newData;
	}
	
	public void loadConfig(FileConfiguration config, WorldData data) {
		data.rate = config.getInt("rate", data.rate) * 1000;
		data.amount = config.getInt("amount", data.amount);
		data.max = config.getInt("max", data.max);
		data.min = config.getInt("min", data.min);
		data.altitude = config.getInt("altitude", data.altitude);
		data.disableHurt = config.getBoolean("disableHurt", data.disableHurt);
	}
	
	// Used to store per-world data on healing
	private class WorldData {
		long lastHeal = 0;
		int rate = 1;
		int amount = 1;
		int max = 20;
		int min = 0;
		int altitude = 0;
		int minFood = 10;
		int maxFood = 20;
		boolean disableHurt = true;
	}
	
	// Task used for healing players
	private class HealRun implements Runnable {
		public void run() {
			long time = System.currentTimeMillis();
			for (World world : server.getWorlds()) {
				WorldData data = worlds.get(world.getName());
				if (data == null) {
					data = loadConfig(world.getName());
				}
				// Not healing yet
				if (time - data.lastHeal < data.rate) continue;
				// Loop all players, check altitude, health range, and perms
				for (Player player : world.getPlayers()) {
					if (player.getLocation().getY() < data.altitude) continue;
					if (player.getHealth() >= data.max || player.getHealth() <= data.min) continue;
					if (player.getFoodLevel() > data.maxFood || player.getFoodLevel() < data.minFood) continue;
					if (!hasPerm(player, "autoheal.heal")) continue;
					player.setHealth(player.getHealth() + data.amount);
				}
				if (data.lastHeal == 0) data.lastHeal = time;
				else data.lastHeal += data.rate;
			}
		}
	}
	
	/*
	 * Check whether the player has the given permissions.
	 */
	public boolean hasPerm(Player player, String perm) {
		return player.hasPermission(perm);
	}
	
	public class entityListener implements Listener {
		/**
		 * Cancel starvation events if disabled on this world
		 */
		@EventHandler
		public void onEntityDamage(EntityDamageEvent event) {
			if (event.isCancelled()) return;
			if (event.getCause() != DamageCause.STARVATION) return;
			if (!(event.getEntity() instanceof Player)) return;
			
			Player p = (Player)event.getEntity();
			WorldData data = worlds.get(p.getWorld());
			if (data == null) {
				data = loadConfig(p.getWorld().getName());
			}
			if (data.disableHurt) event.setCancelled(true);
		}
	}
}
