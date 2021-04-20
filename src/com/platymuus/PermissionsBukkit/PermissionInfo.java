package com.platymuus.PermissionsBukkit;

import org.bukkit.configuration.ConfigurationSection;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A class representing the global and world nodes attached to a player or group.
 */
public final class PermissionInfo{
	private final PermissionsPlugin plugin;
	private final ConfigurationSection node;
	public enum GroupType{GROUPS, INHERITANCE};
	private final GroupType groupType;

	PermissionInfo(PermissionsPlugin plugin, ConfigurationSection node, GroupType groupType){
		this.plugin = plugin;
		this.node = node;
		this.groupType = groupType;
	}

	/**
	 * Gets the list of groups this group/player inherits permissions from.
	 *
	 * @return The list of groups.
	 */
	public List<Group> getGroups(){ // Unused / API-only
		return node.getStringList(groupType.name().toLowerCase()).stream()
				.map(key -> plugin.getGroup(key))
				.filter(group -> group != null)
				.collect(Collectors.toList());
	}

	/**
	 * Gets a map of non-world-specific permission nodes to boolean values that this group/player defines.
	 *
	 * @return The map of permissions.
	 */
	public Map<String, Boolean> getPermissions(){ // Unused / API-only
		return plugin.getAllPerms(node.getName(), node.getCurrentPath());
	}

	/**
	 * Gets a list of worlds this group/player defines world-specific permissions for.
	 *
	 * @return The list of worlds.
	 */
	public Set<String> getWorlds(){ // Unused / API-only
		return node.isConfigurationSection("worlds")
			? node.getConfigurationSection("worlds").getKeys(false)
			: new HashSet<>();
	}

	/**
	 * Gets a map of world-specific permission nodes to boolean values that this group/player defines.
	 *
	 * @param world The name of the world.
	 * @return The map of permissions.
	 */
	public Map<String, Boolean> getWorldPermissions(String world){ // Unused / API-only
		return plugin.getAllPerms(node.getName() + ":" + world, node.getName() + "/world/" + world);
	}
}
