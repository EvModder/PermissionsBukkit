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

/**
 * CommandExecutor for /permissions
 */
final class PermissionsCommand implements CommandExecutor{
	private final PermissionsPlugin plugin;
	private final String PERM_ERROR_MSG;

	public PermissionsCommand(PermissionsPlugin plugin){
		this.plugin = plugin;
		PERM_ERROR_MSG = ChatColor.translateAlternateColorCodes('&',
				plugin.getConfig().getString("command-permission-message", "&cYou do not have permissions to do that."));
	}
	
	// -- utilities --

	private UUID resolvePlayer(CommandSender sender, String arg){
		arg = arg.toLowerCase();

		// see if it resolves to a single player name
		@SuppressWarnings("deprecation")
		List<Player> players = plugin.getServer().matchPlayer(arg);
		if(players.size() == 1) return players.get(0).getUniqueId();
		else if(players.size() > 1){
			sender.sendMessage(ChatColor.RED + "Username " + ChatColor.WHITE + arg + ChatColor.RED + " is ambiguous.");
			return null;
		}

		if(arg.length() == 32){
			// expand UUIDs which do not have dashes in them
			arg = new StringBuilder()
					.append(arg.substring(0, 8)).append('-')
					.append(arg.substring(8, 12)).append('-')
					.append(arg.substring(12, 16)).append('-')
					.append(arg.substring(16, 20)).append('-')
					.append(arg.substring(20, 32)).toString();
		}
		if(arg.length() == 36){
			try{return UUID.fromString(arg);}
			catch(IllegalArgumentException ex){}
		}

		sender.sendMessage(ChatColor.RED + "Could not resolve player: " + ChatColor.WHITE + arg);
		sender.sendMessage(ChatColor.RED + "You must provide a UUID or the name of an online player.");
		return null;
	}

	private boolean checkPerm(CommandSender sender, String subnode){
		final boolean ok = sender.hasPermission("permissions." + subnode);
		if(!ok) sender.sendMessage(PERM_ERROR_MSG);
		return ok;
	}

	private boolean usage(CommandSender sender, Command command, String label){
		final int subCmdStart = "/<command> ".length();
		sender.sendMessage(ChatColor.RED + "[====" + ChatColor.GREEN + " /permissons " + ChatColor.RED + "====]");
		for(String line : command.getUsage().split("\\n")){
			if((line.startsWith("/<command> group") && !line.startsWith("/<command> group -"))
					|| (line.startsWith("/<command> player") && !line.startsWith("/<command> player -"))){
				continue;
			}
			if(sender.hasPermission("permissions."+line.substring(subCmdStart, line.indexOf(' ', subCmdStart+1)))){
				sender.sendMessage(formatLine(line, label));
			}
		}
		return true;
	}

	private boolean usage(CommandSender sender, Command command, String label, String subcommand){
		final String subCmdPermPrefix = "permissions."+subcommand+".";
		final int subSubCmdStart = ("/<command> "+subcommand+" ").length();
		sender.sendMessage(ChatColor.RED + "[====" + ChatColor.GREEN + " /permissons " + subcommand + " " + ChatColor.RED + "====]");
		for(String line : command.getUsage().split("\\n")){
			if(line.startsWith("/<command> " + subcommand)){
				if(sender.hasPermission(subCmdPermPrefix+line.substring(subSubCmdStart, line.indexOf(' ', subSubCmdStart+1)))){
					sender.sendMessage(formatLine(line, label));
				}
			}
		}
		return true;
	}

	private String formatLine(String line, String label){
		int i = line.indexOf(" - ");
		String usage = line.substring(0, i);
		String desc = line.substring(i + 3);

		usage = usage.replace("<command>", label);
		usage = usage.replaceAll("\\[[^]:]+\\]", ChatColor.AQUA + "$0" + ChatColor.GREEN);
		usage = usage.replaceAll("\\[[^]]+:\\]", ChatColor.AQUA + "$0" + ChatColor.LIGHT_PURPLE);
		usage = usage.replaceAll("<[^>]+>", ChatColor.LIGHT_PURPLE + "$0" + ChatColor.GREEN);

		return ChatColor.GREEN + usage + " - " + ChatColor.WHITE + desc;
	}

	// -- core logic --

	@SuppressWarnings("deprecation")
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
		if(args.length < 1){
			return !checkPerm(sender, "help") || usage(sender, command, label);
		}

		final String subcommand = args[0];
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
				final PluginDescriptionFile desc = plugin.getDescription();
				sender.sendMessage(ChatColor.GOLD + desc.getName() + ChatColor.GREEN + " version " + ChatColor.GOLD + desc.getVersion());
				sender.sendMessage(ChatColor.GREEN + "By " + ChatColor.WHITE + String.join(ChatColor.GREEN+", "+ChatColor.WHITE, desc.getAuthors()));
				sender.sendMessage(ChatColor.GREEN + "Website: " + ChatColor.WHITE + desc.getWebsite());
				return true;
			case "check":{
				if(!checkPerm(sender, "check")) return true;
				if(args.length != 2 && args.length != 3) return usage(sender, command, label, subcommand);

				final String node = args[1];
				final Permissible permissible = args.length == 2 ? sender : plugin.getServer().getPlayer(args[2]);
				final String name = (permissible instanceof Player) ? ((Player)permissible).getName()
						: (permissible instanceof ConsoleCommandSender) ? "Console" : "Unknown";

				if(permissible == null){
					sender.sendMessage(ChatColor.RED + "Player " + ChatColor.WHITE + args[2] + ChatColor.RED + " not found.");
				}
				else{
					final boolean set = permissible.isPermissionSet(node), has = permissible.hasPermission(node);
					final String sets = set ? " sets " : " defaults ";
					final String perm = has ? "true" : "false";
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + name
							+ ChatColor.GREEN + sets + ChatColor.WHITE + node + ChatColor.GREEN
							+ " to " + ChatColor.WHITE + perm + ChatColor.GREEN + ".");
				}
				return true;
			}
			case "info":{
				if(!checkPerm(sender, "info")) return true;
				if(args.length != 2) return usage(sender, command, label, subcommand);

				final String node = args[1];
				final Permission perm = plugin.getServer().getPluginManager().getPermission(node);

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
				if(args.length < 1 || args.length > 3) return usage(sender, command, label, subcommand);

				int page;
				Permissible permissible;
				if(args.length == 1){
					permissible = sender;
					page = 1;
				}
				else if(args.length == 2){
					permissible = sender;
					try{
						page = Integer.parseInt(args[1]);
					}
					catch(NumberFormatException ex){
						if(args[1].equalsIgnoreCase("-file")){
							page = -1;
						}
						else{
							permissible = plugin.getServer().getPlayer(args[1]);
							page = 1;
						}
					}
				}
				else{
					permissible = plugin.getServer().getPlayer(args[1]);
					try{
						page = Integer.parseInt(args[2]);
					}
					catch(NumberFormatException ex){
						if(args[2].equalsIgnoreCase("-file")){
							page = -1;
						}
						else{
							page = 1;
						}
					}
				}

				if(permissible == null){
					sender.sendMessage(ChatColor.RED + "Player " + ChatColor.WHITE + args[1] + ChatColor.RED + " not found.");
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

				final int numpages = 1 + (dump.size() - 1) / 8;
				if(page > numpages) page = numpages;
				else if(page < 1) page = 1;

				ChatColor g = ChatColor.GREEN, w = ChatColor.WHITE, r = ChatColor.RED;

				final int start = 8 * (page - 1);
				sender.sendMessage(ChatColor.RED + "[==== " + ChatColor.GREEN + "Page " + page + " of " + numpages + ChatColor.RED + " ====]");
				for(int i = start; i < start + 8 && i < dump.size(); ++i){
					PermissionAttachmentInfo info = dump.get(i);

					if(info.getAttachment() == null){
						sender.sendMessage(g + "Node " + w + info.getPermission() + g + "=" + w + info.getValue() + g+ " (" + r + "default" + g + ")");
					}
					else{
						sender.sendMessage(g + "Node " + w + info.getPermission() + g + "=" + w + info.getValue() + g
								+ " (" + w + "config.yml" + g + ")");
					}
				}
				return true;
			}
			case "group":
				if(args.length < 2){
					return !checkPerm(sender, "group.help") || usage(sender, command, label, subcommand);
				}
				groupCommand(sender, command, label, args);
				return true;
			case "player":
				if(args.length < 2){
					return !checkPerm(sender, "player.help") || usage(sender, command, label, subcommand);
				}
				playerCommand(sender, command, label, args);
				return true;
			default:
				return !checkPerm(sender, "help") || usage(sender, command, label);
		}
	}

	private boolean groupCommand(CommandSender sender, Command command, String label, String[] args){
		String subcommand = args[1];

		switch(subcommand) {
			case "list":
				if(!checkPerm(sender, "group.list")) return true;
				if(args.length != 2) return usage(sender, command, label, "group list");
				sender.sendMessage(ChatColor.GREEN + "Groups: " + ChatColor.WHITE + String.join(", ", plugin.getNode("groups").getKeys(false)));
				return true;
			case "players":{
				if(!checkPerm(sender, "group.players")) return true;
				if(args.length != 3) return usage(sender, command, label, "group players");
				final String group = args[2];

				if(plugin.getNode("groups/" + group) == null){
					sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				final List<String> users = new LinkedList<>();
				for(String userKey : plugin.getNode("users").getKeys(false)){
					ConfigurationSection node = plugin.getNode("users/" + userKey);
					if(node.getStringList("groups").contains(group)){
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
				for(String user : users) sender.sendMessage("  " + user);
				return true;
			}
			case "setperm":{
				if(!checkPerm(sender, "group.setperm")) return true;
				if(args.length != 4 && args.length != 5) return usage(sender, command, label, "group setperm");
				final String group = args[2];
				String perm = args[3];
				final boolean value = (args.length != 5) || Boolean.parseBoolean(args[4]);

				String node = "permissions";
				if(plugin.getNode("groups/" + group) == null){
					sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				if(perm.contains(":")){
					final String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				plugin.createNode("groups/" + group + "/" + node).set(perm, value);
				plugin.refreshForGroup(group);

				sender.sendMessage(ChatColor.GREEN + "Group " + ChatColor.WHITE + group + ChatColor.GREEN + " now has " 
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " = " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
				return true;
			}
			case "unsetperm":{
				if(!checkPerm(sender, "group.unsetperm")) return true;
				if(args.length != 4) return usage(sender, command, label, "group unsetperm");
				final String group = args[2].toLowerCase();
				String perm = args[3];

				String node = "permissions";
				if(plugin.getNode("groups/" + group) == null){
					sender.sendMessage(ChatColor.RED + "No such group " + ChatColor.WHITE + group + ChatColor.RED + ".");
					return true;
				}

				if(perm.contains(":")){
					final String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				ConfigurationSection sec = plugin.createNode("groups/" + group + "/" + node);
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
				return !checkPerm(sender, "group.help") || usage(sender, command, label);
		}
	}

	private boolean playerCommand(CommandSender sender, Command command, String label, String[] args){
		final String subcommand = args[1];

		switch(subcommand) {
			case "groups":{
				if(!checkPerm(sender, "player.groups")) return true;
				if(args.length != 3) return usage(sender, command, label, "player groups");
				final UUID player = resolvePlayer(sender, args[2]);
				if(player == null) return true;
				final String playerName = plugin.getServer().getPlayer(player).getName();

				if(plugin.getNode("users/" + player) == null){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.RED + " is in the default group.");
					return true;
				}

				final List<String> groups = plugin.getNode("users/" + player).getStringList("groups");
				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN
						+ " is in groups (" + ChatColor.WHITE + groups.size() + ChatColor.GREEN + "): " + ChatColor.WHITE + String.join(", ", groups));
				return true;
			}
			case "addgroup":{
				if(!checkPerm(sender, "player.addgroup")) return true;
				if(args.length != 4) return usage(sender, command, label, "player addgroup");
				final UUID player = resolvePlayer(sender, args[2]);
				if(player == null) return true;
				final String playerName = plugin.getServer().getPlayer(player).getName();
				final String group = args[3];

				List<String> list = plugin.createNode("users/" + player).getStringList("groups");
				if(list.contains(group)){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " was already in "
							+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
					return true;
				}
				list.add(group);
				plugin.getNode("users/" + player).set("groups", list);

				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " is now in "
						+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
				return true;
			}
			case "removegroup":{
				if(!checkPerm(sender, "player.removegroup")) return true;
				if(args.length != 4) return usage(sender, command, label, "player removegroup");
				final UUID player = resolvePlayer(sender, args[2]);
				if(player == null) return true;
				final String playerName = plugin.getServer().getPlayer(player).getName();
				final String group = args[3];

				List<String> list = plugin.createNode("users/" + player).getStringList("groups");
				if(!list.contains(group)){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " was not in "
							+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
					return true;
				}
				list.remove(group);
				plugin.getNode("users/" + player).set("groups", list);

				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " is no longer in "
						+ ChatColor.WHITE + group + ChatColor.GREEN + ".");
				return true;
			}
			case "setperm":{
				if(!checkPerm(sender, "player.setperm")) return true;
				if(args.length != 4 && args.length != 5) return usage(sender, command, label, "player setperm");
				final UUID player = resolvePlayer(sender, args[2]);
				if(player == null) return true;
				final String playerName = plugin.getServer().getPlayer(player).getName();
				String perm = args[3];
				boolean value = (args.length != 5) || Boolean.parseBoolean(args[4]);

				String node = "permissions";
				if(perm.contains(":")){
					final String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				plugin.createNode("users/" + player + "/" + node).set(perm, value);
				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " now has "
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " = " + ChatColor.WHITE + value + ChatColor.GREEN + ".");
				return true;
			}
			case "unsetperm":{
				if(!checkPerm(sender, "player.unsetperm")) return true;
				if(args.length != 4) return usage(sender, command, label, "player unsetperm");
				final UUID player = resolvePlayer(sender, args[2]);
				if(player == null) return true;
				final String playerName = plugin.getServer().getPlayer(player).getName();
				String perm = args[3];

				String node = "permissions";
				if(perm.contains(":")){
					final String world = perm.substring(0, perm.indexOf(':'));
					perm = perm.substring(perm.indexOf(':') + 1);
					node = "worlds/" + world;
				}

				ConfigurationSection sec = plugin.createNode("users/" + player + "/" + node);
				if(!sec.contains(perm)){
					sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " did not have "
							+ ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
					return true;
				}
				sec.set(perm, null);
				plugin.refreshForPlayer(player);

				sender.sendMessage(ChatColor.GREEN + "Player " + ChatColor.WHITE + playerName + ChatColor.GREEN + " no longer has "
						+ ChatColor.WHITE + perm + ChatColor.GREEN + " set.");
				return true;
			}
			default:
				return !checkPerm(sender, "player.help") || usage(sender, command, label);
		}
	}
}
