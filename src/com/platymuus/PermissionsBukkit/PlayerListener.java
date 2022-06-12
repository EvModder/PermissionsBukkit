package com.platymuus.PermissionsBukkit;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

/**
 * Listen for player-based events to keep track of players and build permissions.
 */
final class PlayerListener implements Listener{
	private final PermissionsPlugin plugin;
	private Set<String> hiddenCmds;
	private final boolean HIDE_NAMESPACED_CMDS, HIDE_NULL_PERM_CMDS, HIDE_CMD_ALIASES, GUESS_PERM_NAME_IF_NULL = true;
	private boolean aliasesAddedToHidden = false;

	public PlayerListener(PermissionsPlugin plugin){
		this.plugin = plugin;
		HIDE_NAMESPACED_CMDS = plugin.getConfig().getBoolean("hide-namespaced-commands-in-tab-complete", false);
		HIDE_NULL_PERM_CMDS = plugin.getConfig().getBoolean("hide-commands-with-null-permission", false);
		HIDE_CMD_ALIASES = plugin.getConfig().getBoolean("hide-command-aliases-in-tab-complete", false);
		hiddenCmds = new HashSet<>(plugin.getConfig().getStringList("hide-specific-commands"));
	}

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

	@EventHandler public void cmdSendEvent(PlayerCommandSendEvent event){
		plugin.getLogger().info("Player " + event.getPlayer().getName() + " is being sent commands they can see in tab-complete");
		if(HIDE_NAMESPACED_CMDS) event.getCommands().removeIf(cmd -> cmd.contains(":"));
		if(HIDE_NULL_PERM_CMDS) event.getCommands().removeIf(cmd -> {
			final PluginCommand pc = plugin.getServer().getPluginCommand(cmd);
			return pc != null && pc.getPermission() == null &&
				(!GUESS_PERM_NAME_IF_NULL || (
						!event.getPlayer().hasPermission(pc.getPlugin().getName()+"."+pc.getName())
						&& !event.getPlayer().hasPermission(pc.getPlugin().getName()+".command."+pc.getName())
					)
				);
		});
		if(HIDE_CMD_ALIASES && !aliasesAddedToHidden){
			hiddenCmds.addAll(plugin.getServer().getCommandAliases().keySet());
			for(Plugin p : plugin.getServer().getPluginManager().getPlugins()){
				if(p.getDescription().getCommands() == null) continue;
				for(String cmdName : p.getDescription().getCommands().keySet()){
					PluginCommand cmd = p.getServer().getPluginCommand(cmdName);
					if(cmd != null) hiddenCmds.addAll(cmd.getAliases());
				}
			}
			aliasesAddedToHidden = true;
		}
		if(!hiddenCmds.isEmpty()) event.getCommands().removeIf(cmd -> hiddenCmds.contains(cmd));
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
