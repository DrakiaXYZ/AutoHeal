package net.TheDgtl.AutoHeal;

import java.io.File;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.command.ColouredConsoleSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.nijikokun.bukkit.Permissions.Permissions;

public class AutoHeal extends JavaPlugin {
	private Server server;
	private ColouredConsoleSender console;
	private Configuration config;
	private PluginManager pm;
	
	// Listeners
	private final serverListener sListener = new serverListener();
	
	private Permissions permissions = null;
	
	private HashMap<String, WorldData> worlds = new HashMap<String, WorldData>();
	WorldData defData = new WorldData();
	@Override
	public void onEnable() {
		server = getServer();
		pm = server.getPluginManager();
		console = new ColouredConsoleSender((CraftServer)server);
		config = getConfiguration();
		
		loadConfig();
		
		permissions = (Permissions)checkPlugin("Permissions");
		
		pm.registerEvent(Event.Type.PLUGIN_ENABLE, sListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLUGIN_DISABLE, sListener, Priority.Monitor, this);
		
		server.getScheduler().scheduleSyncRepeatingTask(this, new HealRun(), 0L, 10L);
		
		console.sendMessage("AutoHeal v" + getDescription().getVersion() + ChatColor.GREEN + " Enabled");
	}
	
	@Override
	public void onDisable() {
		console.sendMessage("AutoHeal " + ChatColor.RED + "Disabled");
	}
	
	public void loadConfig() {
		// Clear config
		if (!worlds.isEmpty()) worlds.clear();
		// Load/save default config
		loadConfig(this.config, defData);
		config.save();
		
		// Load world configs
		for (World world : server.getWorlds()) {
			loadConfig(world.getName());
		}
	}
	
	public WorldData loadConfig(String name) {
		WorldData newData = new WorldData();
		File fh = new File(this.getDataFolder().getPath() + File.separator + name);
		if (!fh.exists()) {
			loadConfig(config, newData);
		} else {
			Configuration conf = new Configuration(fh);
			loadConfig(conf, newData);
		}
		worlds.put(name, newData);
		return newData;
	}
	
	public void loadConfig(Configuration config, WorldData data) {
		data.rate = config.getInt("rate", data.rate) * 1000;
		data.amount = config.getInt("amount", data.amount);
		data.max = config.getInt("max", data.max);
		data.min = config.getInt("min", data.min);
		data.altitude = config.getInt("altitude", data.altitude);
	}
	
	// Used to store per-world data on healing
	private class WorldData {
		long lastHeal = 0;
		int rate = 1;
		int amount = 1;
		int max = 20;
		int min = 0;
		int altitude = 0;
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
					if (!hasPerm(player, "autoheal.heal", true)) continue;
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
	public boolean hasPerm(Player player, String perm, boolean def) {
		if (permissions != null) {
			return permissions.getHandler().has(player, perm);
		} else {
			return def;
		}
	}
	
	/*
	 * Check if a plugin is loaded/enabled already. Returns the plugin if so, null otherwise
	 */
	private Plugin checkPlugin(String p) {
		Plugin plugin = pm.getPlugin(p);
		return checkPlugin(plugin);
	}
	
	private Plugin checkPlugin(Plugin plugin) {
		if (plugin != null && plugin.isEnabled()) {
			console.sendMessage("[AutoHeal] Found " + plugin.getDescription().getName() + " (v" + plugin.getDescription().getVersion() + ")");
			return plugin;
		}
		return null;
	}
	
	// Used for loading plugin dependencies
	private class serverListener extends ServerListener {
		@Override
		public void onPluginEnable(PluginEnableEvent event) {
			if (permissions == null) {
				if (event.getPlugin().getDescription().getName().equalsIgnoreCase("Permissions")) {
					permissions = (Permissions)checkPlugin(event.getPlugin());
				}
			}
		}
		
		@Override
		public void onPluginDisable(PluginDisableEvent event) {
			if (event.getPlugin() == permissions) {
				console.sendMessage("[AutoHeal] Permissions plugin lost.");
				permissions = null;
			}
		}
	}
}
