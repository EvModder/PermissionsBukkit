package com.platymuus.PermissionsBukkit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.PluginDescriptionFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Level;
import static com.platymuus.PermissionsBukkit.Constants.CONFIG_GROUPS;
import static com.platymuus.PermissionsBukkit.Constants.CONFIG_PERMISSIONS;

/**
 * CommandExecutor for /permissions
 */
final class PermissionsCommand implements CommandExecutor{

	private final PermissionsPlugin plugin;

	public PermissionsCommand(PermissionsPlugin plugin){
		this.plugin = plugin;
	}

	@SuppressWarnings("deprecation")
	public boolean onCommand(CommandSender sender, Command command, String label, String[] split){
		if(split.length < 1){
			return !checkPerm(sender, "help") || usage(sender, command);
		}

		String subcommand = split[0];
		switch(subcommand) {
			case "reload":
				if(!checkPerm(sender, "reload")) return true;
				plugin.reloadConfig();
				if(plugin.configLoadError){
					plugin.configLoadError = false;
					sender.sendMessage(ChatColor.RED + "Your configuration is invalid, see the console for details.");
				}
				else{
					plugin.refreshPermissions();
					sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
				}
				return true;
			case "about":
				if(!checkPerm(sender, "about")) return true;

				// plugin information
				PluginDescriptionFile desc = plugin.getDescription();
				sender.sendMessage(ChatColor.GOLD + desc.getName() + ChatColor.GREEN + " version " + ChatColor.GOLD + desc.getVersion());
				StringBuilder auth = new StringBuilder(desc.getAuthors().get(0));
				for(int i = 1; i < desc.getAuthors().size(); ++i){
					auth.append(ChatColor.GREEN + ", " + ChatColor.WHITE).append(desc.getAuthors().get(i));
				}
				sender.sendMessage(ChatColor.GREEN + "By " + ChatColor.WHITE + auth);
				sender.sendMessage(ChatColor.GREEN + "Website: " + ChatColor.WHITE + desc.getWebsite());

				return true;
			case "check":{
				if(!checkPerm(sender, "check")) return true;
				if(split.length != 2 && split.length != 3) return usage(sender, command, subcommand);

				String node = split[1];
				Permissible permissible;
				if(split.length == 2){
					permissible = sender;
				}
				else{
					permissible = plugin.getServer().getPlayer(split[2]);
				}

				String name = (permissible instanceof Player) ? ((Player)permissible).getName()
						: (permissible instanceof ConsoleCommandSender) ? "Console" : "Unknown";

				if(permissible == null){
					sender.sendMessage(ChatColor.RED + "Player " + ChatColor.WHITE + split[2] + ChatColor.RED + " not found.");
				}
				else{
					boolean set = permissible.isPermissionSet(node), has = permissible.hasPermission(node);
					String sets = set ? " sets " : " defaults ";
					String perm = has ? "true" : "false";
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + name
							+ ChatColor.GREEN + sets + ChatColor.WHITE + node + ChatColor.GREEN
							+ " to " + ChatColor.WHITE + perm + ChatColor.GREEN + ".");
				}
				return true;
			}
			case "info":{
				if(!checkPerm(sender, "info")) return true;
				if(split.length != 2) return usage(sender, command, subcommand);

				String node = split[1];
				Permission perm = plugin.getServer().getPluginManager().getPermission(node);

				if(perm == null){
					sender.sendMessage(ChatColor.RED + "Permission " + ChatColor.WHITE + node + ChatColor.RED + " not found.");
				}
				else{
					sender.sendMessage(ChatColor.GREEN + "Info on permission " + ChatColor.WHITE + perm.getName() + ChatColor.GREEN + ":");
					sender.sendMessage(ChatColor.GREEN + "Default: " + ChatColor.WHITE + perm.getDefault());
					if(perm.getDescription() != null && perm.getDescription().length() > 0){
						sender.sendMessage(ChatColor.GREEN + "Description: " + ChatColor.WHITE + perm.getDescription());
					}
					if(perm.getChildren() != null && perm.getChildren().size() > 0){
						sender.sendMessage(ChatColor.GREEN + "Children: " + ChatColor.WHITE + perm.getChildren().size());
					}
					if(perm.getPermissibles() != null && !perm.getPermissibles().isEmpty()){
						int num = 0;
						int numTrue = 0;
						for(Permissible who : perm.getPermissibles()){
							++num;
							if(who.hasPermission(perm)){
								++numTrue;
							}
						}
						sender.sendMessage(ChatColor.GREEN + "Set on: " + ChatColor.WHITE + num
								+ ChatColor.GREEN + " (" + ChatColor.WHITE + numTrue + ChatColor.GREEN + " true)");
					}
				}
				return true;
			}
			case "dump":{
				if(!checkPerm(sender, "dump")) return true;
				if(split.length < 1 || split.length > 3) return usage(sender, command, subcommand);

				int page;
				Permissible permissible;
				if(split.length == 1){
					permissible = sender;
					page = 1;
				}
				else if(split.length == 2){
					permissible = sender;
					try{
						page = Integer.parseInt(split[1]);
					}
					catch(NumberFormatException ex){
						if(split[1].equalsIgnoreCase("-file")){
							page = -1;
						}
						else{
							permissible = plugin.getServer().getPlayer(split[1]);
							page = 1;
						}
					}
				}
				else{
					permissible = plugin.getServer().getPlayer(split[1]);
					try{
						page = Integer.parseInt(split[2]);
					}
					catch(NumberFormatException ex){
						if(split[2].equalsIgnoreCase("-file")){
							page = -1;
						}
						else{
							page = 1;
						}
					}
				}

				if(permissible == null){
					sender.sendMessage(ChatColor.RED + "Player " + ChatColor.WHITE + split[1] + ChatColor.RED + " not found.");
					return true;
				}

				ArrayList<PermissionAttachmentInfo> dump = new ArrayList<>(permissible.getEffectivePermissions());
				dump.sort(Comparator.comparing(PermissionAttachmentInfo::getPermission));

				if(page == -1){
					// Dump to file
					File file = new File(plugin.getDataFolder(), "dump.txt");
					try(FileOutputStream fos = new FileOutputStream(file)){
						try(PrintStream out = new PrintStream(fos)){
							// right now permissible is always a CommandSender
							out.println("PermissionsBukkit dump for: " + ((CommandSender)permissible).getName());
							out.println(new Date().toString());

							for(PermissionAttachmentInfo info : dump){
								if(info.getAttachment() == null){
									out.println(info.getPermission() + "=" + info.getValue() + " (default)");
								}
								else{
									out.println(info.getPermission() + "=" + info.getValue() + " ("
											+ info.getAttachment().getPlugin().getDescription().getName() + ")");
								}
							}
						}

						sender.sendMessage(ChatColor.GREEN + "Permissions dump written to " + ChatColor.WHITE + file);
					}
					catch(IOException e){
						sender.sendMessage(ChatColor.RED + "Failed to write to dump.txt, see the console for more details");
						sender.sendMessage(ChatColor.RED + e.toString());
						plugin.getLogger().log(Level.SEVERE, "Failed to write permissions dump", e);
					}
					return true;
				}

				int numpages = 1 + (dump.size() - 1) / 8;
				if(page > numpages){
					page = numpages;
				}
				else if(page < 1){
					page = 1;
				}

				ChatColor g = ChatColor.GREEN, w = ChatColor.WHITE, r = ChatColor.RED;

				int start = 8 * (page - 1);
				sender.sendMessage(ChatColor.RED + "[==== " + ChatColor.GREEN + "Page " + page + " of " + numpages + ChatColor.RED + " ====]");
				for(int i = start; i < start + 8 && i < dump.size(); ++i){
					PermissionAttachmentInfo info = dump.get(i);

					if(info.getAttachment() == null){
						sender.sendMessage(g + "Node " + w + info.getPermission() + g + "=" + w + info.getValue() + g+ " (" + r + "default" + g + ")");
					}
					else{
						sender.sendMessage(g + "Node " + w + info.getPermission() + g + "=" + w + info.getValue() + g
								+ " (" + w + info.getAttachment().getPlugin().getDescription().getName() + g + ")");
					}
				}
				return true;
			}
			case "rank":
			case "setrank":
				if(!checkPerm(sender, "setrank")) return true;
				if(split.length != 3) return usage(sender, command, subcommand);

				// This is essentially player setgroup with an added check
				UUID player = resolvePlayer(sender, split[1]);
				if(player == null) return true;
				String group = split[2];

				if(!sender.hasPermission("permissions.setrank." + group)){
					sender.sendMessage(ChatColor.RED + "You do not have permission to add players to " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				plugin.createNode("users/" + player).set(CONFIG_GROUPS, Collections.singletonList(group));
				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is now in "
							+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
				return true;
			case "group":
				if(split.length < 2){
					return !checkPerm(sender, "group.help") || usage(sender, command, subcommand);
				}
				groupCommand(sender, command, split);
				return true;
			case "player":
				if(split.length < 2){
					return !checkPerm(sender, "player.help") || usage(sender, command, subcommand);
				}
				playerCommand(sender, command, split);
				return true;
			default:
				return !checkPerm(sender, "help") || usage(sender, command);
		}
	}

	private boolean groupCommand(CommandSender sender, Command command, String[] split){
		String subcommand = split[1];

		switch(subcommand) {
			case "list":
				if(!checkPerm(sender, "group.list")) return true;
				if(split.length != 2) return usage(sender, command, "group list");

				StringBuilder result = new StringBuilder();
				String sep = "";
				for(String key : plugin.getNode(CONFIG_GROUPS).getKeys(false)){
					result.append(sep).append(key);
					sep = ", ";
				}
				sender.sendMessage(ChatColor.GREEN + "Groups: " + ChatColor.WHITE + result);
				return true;
			case "players":{
				if(!checkPerm(sender, "group.players")) return true;
				if(split.length != 3) return usage(sender, command, "group players");
				String group = split[2];

				if(plugin.getNode(CONFIG_GROUPS + "/" + group) == null){
					sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				List<String> users = new LinkedList<>();
				for(String userKey : plugin.getNode("users").getKeys(false)){
					ConfigurationSection node = plugin.getNode("users/" + userKey);
					if(node.getStringList(CONFIG_GROUPS).contains(group)){
						try{
							// show UUID and name if available
							UUID uuid = UUID.fromString(userKey);
							String name = node.getString("name", "???");
							users.add(name + ChatColor.GREEN + " (" + ChatColor.WHITE + uuid + ChatColor.GREEN + ")");
						}
						catch(IllegalArgumentException ex){
							// show as unconverted name-only entry
							users.add(userKey + ChatColor.GREEN + " (" + ChatColor.WHITE + "unconverted" + ChatColor.GREEN + ")");
						}
					}
				}
				sender.sendMessage(ChatColor.GREEN + "Users in " + ChatColor.WHITE + group + ChatColor.GREEN + " (" + ChatColor.WHITE + users.size()
						+ ChatColor.GREEN + "):");
				for(String user : users){
					sender.sendMessage("  " + user);
				}
				return true;
			}
			case "setperm":{
				if(!checkPerm(sender, "group.setperm")) return true;
				if(split.length != 4 && split.length != 5) return usage(sender, command, "group setperm");
				String group = split[2];
				String perm = split[3];
				boolean value = (split.length != 5) || Boolean.parseBoolean(split[4]);

				String node = CONFIG_PERMISSIONS;
				if(plugin.getNode("groups/" + group) == null){
					sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				if(perm.contains(":")){
					String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				plugin.createNode(CONFIG_GROUPS + "/" + group + "/" + node).set(perm, value);
				plugin.refreshForGroup(group);

				sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " now has " 
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " = " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
				return true;
			}
			case "unsetperm":{
				if(!checkPerm(sender, "group.unsetperm")) return true;
				if(split.length != 4) return usage(sender, command, "group unsetperm");
				String group = split[2].toLowerCase();
				String perm = split[3];

				String node = CONFIG_PERMISSIONS;
				if(plugin.getNode(CONFIG_GROUPS + "/" + group) == null){
					sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				if(perm.contains(":")){
					String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				ConfigurationSection sec = plugin.createNode(CONFIG_GROUPS + "/" + group + "/" + node);
				if(!sec.contains(perm)){
					sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " did not have "
							+ ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
					return true;
				}
				sec.set(perm, null);
				plugin.refreshForGroup(group);

				sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " no longer has "
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
				return true;
			}
			default:
				return !checkPerm(sender, "group.help") || usage(sender, command);
		}
	}

	private boolean playerCommand(CommandSender sender, Command command, String[] split){
		String subcommand = split[1];

		switch(subcommand) {
			case "groups":{
				if(!checkPerm(sender, "player.groups")) return true;
				if(split.length != 3) return usage(sender, command, "player groups");
				UUID player = resolvePlayer(sender, split[2]);
				if(player == null) return true;

				if(plugin.getNode("users/" + player) == null){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.RED + " is in the default group.");
					return true;
				}

				int count = 0;
				StringBuilder text = new StringBuilder();
				String sep = "";
				for(String group : plugin.getNode("users/" + player).getStringList(CONFIG_GROUPS)){
					++count;
					text.append(sep).append(group);
					sep = ", ";
				}
				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN
						+ " is in groups (" + ChatColor.WHITE + count + ChatColor.GREEN + "): " + ChatColor.WHITE + text);
				return true;
			}
			case "setgroup":{
				if(!checkPerm(sender, "player.setgroup")) return true;
				if(split.length != 4) return usage(sender, command, "player setgroup");
				UUID player = resolvePlayer(sender, split[2]);
				if(player == null) return true;
				String[] groups = split[3].split(",");

				plugin.createNode("users/" + player).set(CONFIG_GROUPS, Arrays.asList(groups));
				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is now in "
						+ ChatColor.WHITE + split[3] + ChatColor.GREEN + ".");
				return true;
			}
			case "addgroup":{
				if(!checkPerm(sender, "player.addgroup")) return true;
				if(split.length != 4) return usage(sender, command, "player addgroup");
				UUID player = resolvePlayer(sender, split[2]);
				if(player == null) return true;
				String group = split[3];

				List<String> list = plugin.createNode("users/" + player).getStringList(CONFIG_GROUPS);
				if(list.contains(group)){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " was already in "
							+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
					return true;
				}
				list.add(group);
				plugin.getNode("users/" + player).set(CONFIG_GROUPS, list);

				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is now in "
						+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
				return true;
			}
			case "removegroup":{
				if(!checkPerm(sender, "player.removegroup")) return true;
				if(split.length != 4) return usage(sender, command, "player removegroup");
				UUID player = resolvePlayer(sender, split[2]);
				if(player == null) return true;
				String group = split[3];

				List<String> list = plugin.createNode("users/" + player).getStringList(CONFIG_GROUPS);
				if(!list.contains(group)){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " was not in "
							+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
					return true;
				}
				list.remove(group);
				plugin.getNode("users/" + player).set(CONFIG_GROUPS, list);

				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " is no longer in "
						+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
				return true;
			}
			case "setperm":{
				if(!checkPerm(sender, "player.setperm")) return true;
				if(split.length != 4 && split.length != 5) return usage(sender, command, "player setperm");
				UUID player = resolvePlayer(sender, split[2]);
				if(player == null) return true;
				String perm = split[3];
				boolean value = (split.length != 5) || Boolean.parseBoolean(split[4]);

				String node = CONFIG_PERMISSIONS;
				if(perm.contains(":")){
					String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				plugin.createNode("users/" + player + "/" + node).set(perm, value);
				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " now has "
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " = " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
				return true;
			}
			case "unsetperm":{
				if(!checkPerm(sender, "player.unsetperm")) return true;
				if(split.length != 4) return usage(sender, command, "player unsetperm");
				UUID player = resolvePlayer(sender, split[2]);
				if(player == null) return true;
				String perm = split[3];

				String node = CONFIG_PERMISSIONS;
				if(perm.contains(":")){
					String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				ConfigurationSection sec = plugin.createNode("users/" + player + "/" + node);
				if(!sec.contains(perm)){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " did not have "
							+ ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
					return true;
				}
				sec.set(perm, null);
				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + player + ChatColor.GREEN + " no longer has "
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
				return true;
			}
			default:
				return !checkPerm(sender, "player.help") || usage(sender, command);
		}
	}

	private UUID resolvePlayer(CommandSender sender, String arg){
		arg = arg.toLowerCase();

		// see if it resolves to a single player name
		@SuppressWarnings("deprecation")
		List<Player> players = plugin.getServer().matchPlayer(arg);
		if(players.size() == 1){
			return players.get(0).getUniqueId();
		}
		else if(players.size() > 1){
			sender.sendMessage(ChatColor.RED + "Username " + ChatColor.WHITE + arg + ChatColor.RED + " is ambiguous.");
			return null;
		}

		if(arg.length() == 32){
			// expand UUIDs which do not have dashes in them
			arg = arg.substring(0, 8) + "-" + arg.substring(8, 12) + "-" + arg.substring(12, 16) + "-" + arg.substring(16, 20) + "-" + arg.substring(20, 32);
		}
		if(arg.length() == 36){
			// is of correct UUID length
			try{
				return UUID.fromString(arg);
			}
			catch(IllegalArgumentException ex){
				// ignore
			}
		}

		sender.sendMessage(ChatColor.RED + "Could not resolve player: " + ChatColor.WHITE + arg);
		sender.sendMessage(ChatColor.RED + "You must provide a UUID or the name of an online player.");
		return null;
	}

	// -- utilities --

	private boolean checkPerm(CommandSender sender, String subnode){
		boolean ok = sender.hasPermission("permissions." + subnode);
		if(!ok){
			sender.sendMessage(ChatColor.RED + "You do not have permissions to do that.");
		}
		return ok;
	}

	private boolean usage(CommandSender sender, Command command){
		sender.sendMessage(ChatColor.RED + "[====" + ChatColor.GREEN + " /permissons " + ChatColor.RED + "====]");
		for(String line : command.getUsage().split("\\n")){
			if((line.startsWith("/<command> group") && !line.startsWith("/<command> group -"))
					|| (line.startsWith("/<command> player") && !line.startsWith("/<command> player -"))){
				continue;
			}
			sender.sendMessage(formatLine(line));
		}
		return true;
	}

	private boolean usage(CommandSender sender, Command command, String subcommand){
		sender.sendMessage(ChatColor.RED + "[====" + ChatColor.GREEN + " /permissons " + subcommand + " " + ChatColor.RED + "====]");
		for(String line : command.getUsage().split("\\n")){
			if(line.startsWith("/<command> " + subcommand)){
				sender.sendMessage(formatLine(line));
			}
		}
		return true;
	}

	private String formatLine(String line){
		int i = line.indexOf(" - ");
		String usage = line.substring(0, i);
		String desc = line.substring(i + 3);

		usage = usage.replace("<command>", "permissions");
		usage = usage.replaceAll("\\[[^]:]+\\]", ChatColor.AQUA + "$0" + ChatColor.GREEN);
		usage = usage.replaceAll("\\[[^]]+:\\]", ChatColor.AQUA + "$0" + ChatColor.LIGHT_PURPLE);
		usage = usage.replaceAll("<[^>]+>", ChatColor.LIGHT_PURPLE + "$0" + ChatColor.GREEN);

		return ChatColor.GREEN + usage + " - " + ChatColor.WHITE + desc;
	}

}
