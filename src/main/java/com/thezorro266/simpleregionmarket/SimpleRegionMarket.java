package com.thezorro266.simpleregionmarket;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Acrobot.ChestShop.ChestShop;
import com.nijikokun.register.payment.Method;
import com.nijikokun.register.payment.Methods;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class SimpleRegionMarket extends JavaPlugin {
	// public static Permission permission = null;
	public static Economy economy = null;
	public static int enableEconomy = 1;

	public static String plugin_dir = null;
	private static Server server;
	public static boolean unloading = false;

	public static WorldGuardPlugin getWorldGuard() {
		final Plugin plugin = server.getPluginManager().getPlugin("WorldGuard");

		if (plugin == null || !(plugin instanceof WorldGuardPlugin))
			return null;

		return (WorldGuardPlugin) plugin;
	}

	private AgentManager agentManager;

	private CommandHandler commandHandler;

	private ConfigHandler configurationHandler = null;

	private boolean error = false;

	private LanguageHandler langHandler;

	private LimitHandler limitHandler;
	
	private ChestShop chestShop;

	public boolean canBuy(Player player) {
		return (configurationHandler.getConfig().getBoolean("defp_player_buy") || player.hasPermission("simpleregionmarket.buy"));
	}

	public boolean canLet(Player player) {
		return (configurationHandler.getConfig().getBoolean("defp_player_let") || player.hasPermission("simpleregionmarket.let"));
	}

	public boolean canRent(Player player) {
		return (configurationHandler.getConfig().getBoolean("defp_player_rent") || player.hasPermission("simpleregionmarket.rent"));
	}

	public boolean canSell(Player player) {
		return (configurationHandler.getConfig().getBoolean("defp_player_sell") || player.hasPermission("simpleregionmarket.sell"));
	}

	public boolean canAddOwner(Player player) {
		return (configurationHandler.getConfig().getBoolean("defp_player_addowner") || player.hasPermission("simpleregionmarket.addowner"));
	}

	public boolean canAddMember(Player player) {
		return (configurationHandler.getConfig().getBoolean("defp_player_addmember") || player.hasPermission("simpleregionmarket.addmember"));
	}

	public String econFormat(double price) {
		String ret = String.valueOf(price);
		if (enableEconomy == 1) {
			if (getEconomicManager() != null) {
				ret = getEconomicManager().format(price);
			}
		} else if (enableEconomy == 2) {
			ret = economy.format(price);
		}
		return ret;
	}

	public boolean econGiveMoney(String account, double money) throws Exception {
		final boolean ret = true;
		if (enableEconomy == 1) {
			if (getEconomicManager() != null) {
				if (money > 0) {
					getEconomicManager().getAccount(account).add(money);
				} else {
					getEconomicManager().getAccount(account).subtract(-money);
				}
			}
		} else if (enableEconomy == 2) {
			try {
				if (money > 0) {
					economy.depositPlayer(account, money);
				} else {
					economy.withdrawPlayer(account, -money);
				}
			} catch (final Exception e) {
				throw e;
			}
		}
		return ret;
	}

	public boolean econHasEnough(String account, double money) {
		boolean ret = false;
		if (enableEconomy == 1) {
			if (getEconomicManager() != null) {
				ret = getEconomicManager().getAccount(account).hasEnough(money);
			}
		} else if (enableEconomy == 2) {
			ret = economy.has(account, money);
		}
		return ret;
	}

	public AgentManager getAgentManager() {
		return agentManager;
	}

	public ConfigHandler getConfigurationHandler() {
		return configurationHandler;
	}

	public String getCopyright() {
		return "Copyright (C) 2011-2012  theZorro266  -  GPLv3";
	}

	public Method getEconomicManager() {
		if (Methods.hasMethod())
			return Methods.getMethod();
		else {
			langHandler.langOutputConsole("ERR_NO_ECO", Level.SEVERE, null);
			enableEconomy = 0;
			return null;
		}
	}

	public boolean isAdmin(Player player) {
		return (player.isOp() || player.hasPermission("simpleregionmarket.admin"));
	}

	public boolean isEconomy() {
		return enableEconomy > 0
				&& (enableEconomy != 1 || getEconomicManager() != null);
	}

	@Override
	public void onDisable() {
		unloading = true;
		if (error) {
			langHandler.langOutputConsole("ERR_PLUGIN_UNLOAD", Level.SEVERE,
					null);
		} else {
			saveAll();
			langHandler.langOutputConsole("PLUGIN_UNLOAD", Level.INFO, null);
		}
	}

	@Override
	public void onEnable() {
		server = getServer();
		plugin_dir = getDataFolder() + File.separator;

		if (getWorldGuard() == null) {
			error = true;
			langHandler.langOutputConsole("ERR_NO_WORLDGUARD", Level.SEVERE,
					null);
			server.getPluginManager().disablePlugin(this);
			return;
		}

		langHandler = new LanguageHandler(this);

		agentManager = new AgentManager(this, langHandler);

		configurationHandler = new ConfigHandler(this, langHandler);

		enableEconomy = configurationHandler.getConfig().getBoolean(
				"enable_economy") ? 1 : 0;
		if (enableEconomy > 0) {
			if (server.getPluginManager().getPlugin("Register") == null
					&& server.getPluginManager().getPlugin("Vault") == null) {
				langHandler.langOutputConsole("NO_REGISTER_VAULT",
						Level.WARNING, null);
				enableEconomy = 0;
			} else if (server.getPluginManager().getPlugin("Register") != null
					&& server.getPluginManager().getPlugin("Vault") == null) {
				enableEconomy = 1;
			} else {
				enableEconomy = 2;
				/*
				 * if(!setupPermissions()) {
				 * langHandler.langOutputConsole("ERR_VAULT_PERMISSIONS",
				 * Level.WARNING, null); }
				 */
				if (!setupEconomy()) {
					langHandler.langOutputConsole("ERR_VAULT_ECONOMY",
							Level.WARNING, null);
					enableEconomy = 0;
				}
			}
		}

		limitHandler = new LimitHandler(this, langHandler);
		limitHandler.loadLimits();

		new ListenerHandler(this, limitHandler, langHandler);

		commandHandler = new CommandHandler(this, limitHandler, langHandler);
		getCommand("regionmarket").setExecutor(commandHandler);
		
		configurationHandler.load();
		
		chestShop = (ChestShop) Bukkit.getPluginManager().getPlugin("ChestShop");
		if(chestShop != null) {
			langHandler.langOutputConsole("HOOKED_CHESTSHOP", Level.INFO, null);
		}

		server.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			@Override
			public void run() {
				getAgentManager().checkAgents();
			}
		}, 20L, 1200L);

		langHandler.outputConsole(Level.INFO, "loaded version " + getDescription().getVersion() + ",  " + getCopyright());
	}

	/*
	 * private Boolean setupPermissions() {
	 * RegisteredServiceProvider<Permission> permissionProvider =
	 * getServer().getServicesManager
	 * ().getRegistration(net.milkbowl.vault.permission.Permission.class); if
	 * (permissionProvider != null) { permission =
	 * permissionProvider.getProvider(); } return (permission != null); }
	 */
	
	public void unrentHotel(SignAgent hotel) {
		hotel.getProtectedRegion().setMembers(new DefaultDomain());
		hotel.getProtectedRegion().setOwners(new DefaultDomain());
		if (getConfigurationHandler().getConfig().getBoolean("logging")) {
			final ArrayList<String> list = new ArrayList<String>();
			list.add(hotel.getRegion());
			list.add(hotel.getRent());
			langHandler.langOutputConsole("LOG_EXPIRED_HOTEL", Level.INFO, list);
		}
		hotel.rentTo("");
	}

	public void rentHotel(ProtectedRegion region, Player p, long renttime) {
		if (region.getParent() != null) {
			for (final String player : region.getParent().getOwners()
					.getPlayers()) {
				Player powner;
				powner = Bukkit.getPlayerExact(player);
				if (powner != null) {
					final ArrayList<String> list = new ArrayList<String>();
					list.add(region.getId());
					list.add(p.getName());
					langHandler.outputMessage(powner, "HOTEL_RENT", list);
				}
			}
		}
		region.setMembers(new DefaultDomain());
		region.setOwners(new DefaultDomain());
		if (configurationHandler.getConfig().getBoolean("renter_get_owner")) {
			region.getOwners().addPlayer(getWorldGuard().wrapPlayer(p));
		} else {
			region.getMembers().addPlayer(getWorldGuard().wrapPlayer(p));
		}
		getAgentManager().rentRegionForPlayer(region, p, renttime);
		saveAll();
		if (configurationHandler.getConfig().getBoolean("logging")) {
			final ArrayList<String> list = new ArrayList<String>();
			list.add(region.getId());
			list.add(p.getName());
			langHandler.langOutputConsole("LOG_RENT_HOTEL", Level.INFO, list);
		}
	}

	public void saveAll() {
		if (getWorldGuard() != null && getWorldGuard().isEnabled()) {
			for (final World w : server.getWorlds()) {
				final RegionManager mgr = getWorldGuard().getGlobalRegionManager().get(w);

				try {
					mgr.save();
				} catch (final ProtectionDatabaseException e) {
					langHandler.outputConsole(Level.SEVERE, "WorldGuard >> Failed to write regionsfile: " + e.getMessage());
				}
			}
		} else {
			if (!unloading) {
				langHandler.outputConsole(Level.SEVERE, "Saving WorldGuard failed, because it is not loaded.");
			}
		}
		limitHandler.saveLimits();

		configurationHandler.save();
	}

	public void sellRegion(ProtectedRegion region, Player p) {
		for (final String player : region.getOwners().getPlayers()) {
			Player powner;
			powner = Bukkit.getPlayerExact(player);
			if (powner != null) {
				final ArrayList<String> list = new ArrayList<String>();
				list.add(region.getId());
				list.add(p.getName());
				langHandler.outputMessage(powner, "REGION_SOLD", list);
			}
		}
		region.setMembers(new DefaultDomain());
		region.setOwners(new DefaultDomain());
		if (configurationHandler.getConfig().getBoolean("buyer_get_owner")) {
			region.getOwners().addPlayer(getWorldGuard().wrapPlayer(p));
		} else {
			region.getMembers().addPlayer(getWorldGuard().wrapPlayer(p));
		}

		final Iterator<SignAgent> itr = getAgentManager().getAgentList()
				.iterator();
		if (configurationHandler.getConfig().getBoolean("remove_buyed_signs")) {
			while (itr.hasNext()) {
				final SignAgent obj = itr.next();
				if (obj.getProtectedRegion() == region) {
					obj.destroyAgent(false);
					itr.remove();
				}
			}
		} else {
			while (itr.hasNext()) {
				final SignAgent obj = itr.next();
				if (obj.getProtectedRegion() == region) {
					final Sign agentsign = (Sign) obj.getLocation().getBlock()
							.getState();
					agentsign.setLine(2, p.getName());
					agentsign.update();
					itr.remove();
				}
			}
		}

		saveAll();
		if (configurationHandler.getConfig().getBoolean("logging")) {
			final ArrayList<String> list = new ArrayList<String>();
			list.add(region.getId());
			list.add(p.getName());
			langHandler.langOutputConsole("LOG_SOLD_REGION", Level.INFO, list);
		}
	}

	private Boolean setupEconomy() {
		final RegisteredServiceProvider<Economy> economyProvider = getServer()
				.getServicesManager().getRegistration(
						net.milkbowl.vault.economy.Economy.class);
		if (economyProvider != null) {
			economy = economyProvider.getProvider();
		}

		return economy != null;
	}
}