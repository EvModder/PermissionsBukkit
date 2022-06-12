package com.platymuus.PermissionsBukkit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.util.StringUtil;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * TabCompleter for /permissions
 */
final class PermissionsTabComplete implements TabCompleter{
	private static final List<String> BOOLEAN = ImmutableList.of("true", "false");
	private static final List<String> ROOT_SUBS = ImmutableList.of("reload", "about", "check", "info", "dump", "group", "player");
	private static final List<String> GROUP_SUBS = ImmutableList.of("list", "players", "setperm", "unsetperm");
	private static final List<String> PLAYER_SUBS = ImmutableList.of("groups", "setgroup", "addgroup", "removegroup", "setperm", "unsetperm");

	private final HashSet<Permission> permSet = new HashSet<>();
	private final ArrayList<String> permList = new ArrayList<>();

	private final PermissionsPlugin plugin;

	public PermissionsTabComplete(PermissionsPlugin plugin){
		this.plugin = plugin;
	}

	private List<String> partial(String token, Collection<String> from){
		return StringUtil.copyPartialMatches(token, from, new ArrayList<>(from.size()));
	}

	private List<String> matchingPageNumbers(String token, int max){
		return partial(token, IntStream.rangeClosed(1, max).mapToObj(i -> ""+i).collect(Collectors.toList()));
	}

	private Collection<String> allNodes(){
		Set<Permission> newPermSet = plugin.getServer().getPluginManager().getPermissions();
		if(!permSet.equals(newPermSet)){
			permSet.clear();
			permSet.addAll(newPermSet);

			permList.clear();
			for(Permission p : permSet) permList.add(p.getName());
			Collections.sort(permList);
		}
		permList.remove("minecraft");//these perms control nothing and shouldn't be suggested
		permList.remove("craftbukkit");
		return permList;
	}

	private Collection<String> allGroups(){
		return plugin.getConfig().getConfigurationSection("groups").getKeys(/*deep=*/false);
	}

	private List<String> worldNodeComplete(String token){
		List<String> results = partial(token, allNodes());
		plugin.getServer().getWorlds().stream().map(w -> w.getName()).forEach(w -> {if(w.startsWith(token)) results.add(w+':');});
		final int i = token.indexOf(':');
		if(i != -1){
			final String worldPrefix = token.substring(0, i+1);
			partial(token.substring(i+1), allNodes()).forEach(perm -> results.add(worldPrefix+perm));
		}
		return results;
	}

	private UUID resolvePlayer(String arg){
		arg = arg.toLowerCase();
		// See if it resolves to a single player name
		@SuppressWarnings("deprecation")
		List<Player> players = plugin.getServer().matchPlayer(arg);
		if(players.size() == 1) return players.get(0).getUniqueId();
		else if(players.size() > 1) return null;

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
		return null;
	}

	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args){
		// Remember that we can return null to default to online player name matching

		/*
		 * reload - reload the configuration from disk.
		 * check <node> [player] - check if a player or the sender has a permission (any plugin).
		 * info <node> - prints information on a specific permission.
		 * dump [player] [page] - prints info about a player's (or the sender's) permissions.
		 * group - list group-related commands.
		 * player - list player-related commands.
		 */
		final String lastArg = args[args.length - 1];

		if(args.length <= 1){
			return partial(args[0], ROOT_SUBS).stream().filter(
					cmd -> {
						if(cmd.equals("group")) return GROUP_SUBS.stream().anyMatch(subCmd -> sender.hasPermission("permissions.group."+subCmd));
						if(cmd.equals("player")) return PLAYER_SUBS.stream().anyMatch(subCmd -> sender.hasPermission("permissions.player."+subCmd));
						return sender.hasPermission("permissions."+cmd);
					}
			).collect(Collectors.toList());
		}
		else if(args.length == 2){
			final String sub = args[0];
			switch(sub){
				case "check":
					if(!sender.hasPermission("permissions.check")) return ImmutableList.of();
					return partial(lastArg, allNodes());
				case "info":
					if(!sender.hasPermission("permissions.info")) return ImmutableList.of();
					return partial(lastArg, allNodes());
				case "dump":
					if(!sender.hasPermission("permissions.dump")) return ImmutableList.of();
					return null;//TODO: assumes null -> all online player names
				case "group":
					return partial(lastArg, GROUP_SUBS).stream()
							.filter(subCmd -> sender.hasPermission("permissions.group."+subCmd)).collect(Collectors.toList());
				case "player":
					return partial(lastArg, PLAYER_SUBS).stream()
							.filter(subCmd -> sender.hasPermission("permissions.player."+subCmd)).collect(Collectors.toList());
			}
		}
		else{
			final String sub = args[0];
			switch(sub){
				case "check":
					if(args.length != 3 || !sender.hasPermission("permissions.check")) return ImmutableList.of();
					return null;//TODO: assumes null -> all online player names
				case "dump":
					if(args.length != 3 || !lastArg.matches("^[0-9]*$") || !sender.hasPermission("permissions.dump")) return ImmutableList.of();
					final UUID player = resolvePlayer(args[2]);
					if(player == null) return ImmutableList.of();
					final int numPerms = plugin.getServer().getPlayer(player).getEffectivePermissions().size();
					final int numPages = 1 + (numPerms -1) / 8;
					return matchingPageNumbers(lastArg, numPages);
				case "group":
					return groupComplete(sender, args);
				case "player":
					return playerComplete(sender, args);
			}
		}

		return ImmutableList.of();
	}

	private List<String> groupComplete(CommandSender sender, String[] args){
		final String sub = args[1];
		final String lastArg = args[args.length - 1];
		/*
		 * group list - list all groups.
		 * group players <group> - list players in a group.
		 * group setperm <group> <[world:]node> [true|false] - set a permission on a group.
		 * group unsetperm <group> <[world:]node> - unset a permission on a group.
		 */

		switch(sub) {
			case "players":
				if(!sender.hasPermission("permissions.group.players")) return ImmutableList.of();
				if(args.length == 3) return partial(lastArg, allGroups());
				break;
			case "setperm":
				if(!sender.hasPermission("permissions.group.setperm")) return ImmutableList.of();
				if(args.length == 3) return partial(lastArg, allGroups());
				if(!allGroups().contains(args[2])) return ImmutableList.of();
				if(args.length == 4) return worldNodeComplete(lastArg);
				if(args.length == 5) return partial(lastArg, BOOLEAN);
				break;
			case "unsetperm":
				if(!sender.hasPermission("permissions.group.unsetperm")) return ImmutableList.of();
				if(args.length == 3) return partial(lastArg, allGroups());
				if(args.length == 4){
					if(!allGroups().contains(args[2])) return ImmutableList.of();

					final int i = lastArg.indexOf(':');
					final String world = i == -1 ? null : lastArg.substring(0, i);
					final String groupNode = "groups/"+args[2];
					final Map<String, Boolean> setPerms = world == null || plugin.getNode(groupNode+"/worlds/"+world) == null
							? plugin.getAllPerms("group "+args[2], groupNode+"/permissions")
							: plugin.getAllPerms("group "+args[2] + " world "+world, groupNode+"/worlds/"+world);

					List<String> results = worldNodeComplete(lastArg);
					results.removeIf(worldNode -> worldNode.contains(".")
							? !setPerms.containsKey(worldNode.substring(worldNode.indexOf(':')+1))
							: plugin.getNode(groupNode+"/worlds/"+worldNode.substring(0, worldNode.length()-1)) == null);
					return results;
				}
				break;
			default:
		}

		return ImmutableList.of();
	}

	private List<String> playerComplete(CommandSender sender, String[] args){
		final String sub = args[1];
		final String lastArg = args[args.length - 1];
		/*
		 * player groups <player> - list groups a player is in.
		 * player setgroup <player> <group,...> - set a player to be in only the given groups.
		 * player addgroup <player> <group> - add a player to a group.
		 * player removegroup <player> <group> - remove a player from a group.
		 * player setperm <player> <[world:]node> [true|false] - set a permission on a player.
		 * player unsetperm <player> <[world:]node> - unset a permission on a player.
		 */

		final List<String> players = null;//TODO: assumes null -> all online player names

		switch(sub){
			case "groups":
				if(!sender.hasPermission("permissions.player.groups")) return ImmutableList.of();
				if(args.length == 3) return players;
				break;
			case "addgroup":
				if(!sender.hasPermission("permissions.player.addgroup")) return ImmutableList.of();
				if(args.length == 3) return players;
				else if(args.length == 4){
					final UUID player = resolvePlayer(args[2]);
					if(player == null) return partial(lastArg, allGroups());
					// Only show groups of which the target player is NOT already a member
					final List<String> list = plugin.createNode("users/" + player).getStringList("groups");
					return partial(lastArg, allGroups().stream().filter(g -> !list.contains(g)).collect(Collectors.toList()));
				}
				break;
			case "removegroup":
				if(!sender.hasPermission("permissions.player.removegroup")) return ImmutableList.of();
				if(args.length == 3) return players;
				else if(args.length == 4){
					final UUID player = resolvePlayer(args[2]);
					if(player == null) return partial(lastArg, allGroups());
					// Only show groups of which the target player IS currently a member
					final List<String> list = plugin.createNode("users/" + player).getStringList("groups");
					return partial(lastArg, allGroups().stream().filter(g -> list.contains(g)).collect(Collectors.toList()));
				}
				break;
			case "setperm":
				if(!sender.hasPermission("permissions.player.setperm")) return ImmutableList.of();
				if(args.length == 3) return players;
				else if(args.length == 4) return worldNodeComplete(lastArg);
				else if(args.length == 5) return partial(lastArg, BOOLEAN);
				break;
			case "unsetperm":
				if(!sender.hasPermission("permissions.player.unsetperm")) return ImmutableList.of();
				if(args.length == 3) return players;
				else if(args.length == 4){
					List<String> results = worldNodeComplete(lastArg);
					final UUID player = resolvePlayer(args[2]);
					if(player == null) return results;
					final ConfigurationSection userNode = plugin.getUserNode(plugin.getServer().getPlayer(player));
					if(userNode == null) return ImmutableList.of();
					final String nodePath = userNode.getCurrentPath();
					final String permsNodePath = nodePath+"/permissions";

					final int i = lastArg.indexOf(':');
					final String world = i == -1 ? null : lastArg.substring(0, i);
					final Map<String, Boolean> setPerms = world == null || plugin.getNode(nodePath+"/worlds/"+world) == null
							? (plugin.getNode(permsNodePath) == null
								? ImmutableMap.of()
								: plugin.getAllPerms("user "+player, permsNodePath)
							)
							: plugin.getAllPerms("user "+player+" world "+world, nodePath+"/worlds/"+world);

					results.removeIf(worldNode -> worldNode.contains(".")
							? !setPerms.containsKey(worldNode.substring(worldNode.indexOf(':')+1))
							: plugin.getNode(nodePath+"/worlds/"+worldNode.substring(0, worldNode.length()-1)) == null/*|| worldPathPerms.isEmpty()*/);
					return results;
				}
				break;
			default:
		}
		return ImmutableList.of();
	}
}
