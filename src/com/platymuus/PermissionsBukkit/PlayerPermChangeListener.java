package com.platymuus.PermissionsBukkit;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

/**
 * Listen for player join/quit/change-world events to update current permissions.
 */
final class PlayerPermChangeListener implements Listener{
	private final PermissionsPlugin plugin;

	public PlayerPermChangeListener(PermissionsPlugin plugin){this.plugin = plugin;}

	// Keep track of player's world
	@EventHandler(priority = EventPriority.LOWEST)
	public void onWorldChange(PlayerChangedWorldEvent event){
		plugin.calculateAttachment(event.getPlayer());
	}

	// Register players when needed
	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerLogin(PlayerLoginEvent event){
		plugin.debug("Player " + event.getPlayer().getName() + " logged in, registering...");
		plugin.registerPlayer(event.getPlayer());

		if(plugin.configLoadError && event.getPlayer().hasPermission("permissions.reload")){
			plugin.configLoadError = false;
			event.getPlayer().sendMessage(ChatColor.RED + "[" + ChatColor.GREEN + "PermissionsBukkit" + ChatColor.RED
					+ "] Your configuration is invalid, see the console for details.");
		}
	}

	// Unregister players when needed
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlayerKick(PlayerKickEvent event){
		plugin.debug("Player " + event.getPlayer().getName() + " was kicked, unregistering...");
		plugin.unregisterPlayer(event.getPlayer());
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerQuit(PlayerQuitEvent event){
		plugin.debug("Player " + event.getPlayer().getName() + " quit, unregistering...");
		plugin.unregisterPlayer(event.getPlayer());
	}
}