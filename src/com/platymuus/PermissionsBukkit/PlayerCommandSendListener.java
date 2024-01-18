package com.platymuus.PermissionsBukkit;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

/**
 * Listen for player-based events to keep track of players and build permissions.
 */
final class PlayerCommandSendListener implements Listener{
	private final PermissionsPlugin plugin;
	private final Set<String> hiddenCmds;
	private final boolean HIDE_NAMESPACED_CMDS, HIDE_NULL_PERM_CMDS, HIDE_CMD_ALIASES, GUESS_PERM_NAME_IF_NULL = true;
	private boolean aliasesAddedToHidden = false;

	public PlayerCommandSendListener(PermissionsPlugin plugin){
		this.plugin = plugin;
		HIDE_NAMESPACED_CMDS = plugin.getConfig().getBoolean("hide-namespaced-commands-in-tab-complete", false);
		HIDE_NULL_PERM_CMDS = plugin.getConfig().getBoolean("hide-commands-with-null-permission", false);
		HIDE_CMD_ALIASES = plugin.getConfig().getBoolean("hide-command-aliases-in-tab-complete", false);
		hiddenCmds = new HashSet<>(plugin.getConfig().getStringList("hide-specific-commands"));
	}

	private boolean noAccess(final PluginCommand pc, final Player player){
		return pc != null && pc.getPermission() == null && (
			!GUESS_PERM_NAME_IF_NULL || (
				!player.hasPermission(pc.getPlugin().getName()+"."+pc.getName())
				&& !player.hasPermission(pc.getPlugin().getName()+".command."+pc.getName())
			)
		);
	}

	// TODO: This is only available in 1.13+, and prevents the rest of the plugin from being 1.8 compatible
	@EventHandler public void cmdSendEvent(PlayerCommandSendEvent event){
		plugin.debug("Player " + event.getPlayer().getName() + " is being sent commands they can see in tab-complete");
		if(!HIDE_NULL_PERM_CMDS && HIDE_NAMESPACED_CMDS) event.getCommands().removeIf(cmd -> cmd.contains(":"));
		if(HIDE_NULL_PERM_CMDS){
			final Set<String> namespaceCmdWithAccess = new HashSet<>();
			event.getCommands().removeIf(cmd -> {
				final int i = cmd.indexOf(':');
				if(i == -1) return false;
				if(!noAccess(plugin.getServer().getPluginCommand(cmd), event.getPlayer())) namespaceCmdWithAccess.add(cmd.substring(i+1));
				return HIDE_NAMESPACED_CMDS;
			});
			event.getCommands().removeIf(cmd -> !namespaceCmdWithAccess.contains(cmd)
					&& noAccess(plugin.getServer().getPluginCommand(cmd), event.getPlayer()));
		}
		if(HIDE_CMD_ALIASES && !aliasesAddedToHidden){
			Set<String> cmdAliases = new HashSet<>(), cmdNames = new HashSet<>();
			cmdAliases.addAll(plugin.getServer().getCommandAliases().keySet());
			for(Plugin p : plugin.getServer().getPluginManager().getPlugins()){
				if(p.getDescription().getCommands() == null) continue;
				for(String cmdName : p.getDescription().getCommands().keySet()){
					cmdNames.add(cmdName);
					final PluginCommand cmd = p.getServer().getPluginCommand(cmdName);
					if(cmd != null) cmdAliases.addAll(cmd.getAliases());
				}
			}
			cmdAliases.removeAll(cmdNames);
			hiddenCmds.addAll(cmdAliases);
			aliasesAddedToHidden = true;
		}
		if(!hiddenCmds.isEmpty()) event.getCommands().removeIf(cmd -> hiddenCmds.contains(cmd));
	}
}