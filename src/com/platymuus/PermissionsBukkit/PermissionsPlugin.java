package com.platymuus.PermissionsBukkit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main class for PermissionsBukkit.
 */
public final class PermissionsPlugin extends JavaPlugin{
	private HashMap<UUID, PermissionAttachment> permissions;
	private File configFile;
	private YamlConfiguration config;

	public boolean configLoadError = false;

	@Override public void onEnable(){
		// Take care of configuration
		permissions = new HashMap<>();
		configFile = new File(getDataFolder(), "config.yml");
		saveDefaultConfig();
		reloadConfig();

		// Dynamically add the children perms of "permissions.player.addgroup.*" for all defined groups
		Permission addgroupPerm = getServer().getPluginManager().getPermission("permissions.player.addgroup");
		Permission removegroupPerm = getServer().getPluginManager().getPermission("permissions.player.removegroup");
		if(addgroupPerm != null && config.isConfigurationSection("groups")) try{
			for(String groupName : config.getConfigurationSection("groups").getKeys(/*deep=*/false)){
				// permissions.player.addgroup.<group>
				Permission addgroupPermForGroup = new Permission(addgroupPerm.getName()+"."+groupName.toLowerCase(), 
						"Allows use of /permissions player addgroup "+groupName, PermissionDefault.FALSE);
				addgroupPermForGroup.addParent(addgroupPerm, true);
				getServer().getPluginManager().addPermission(addgroupPermForGroup);
				// permissions.player.removegroup.<group>
				Permission removegroupPermForGroup = new Permission(removegroupPerm.getName()+"."+groupName.toLowerCase(), 
						"Allows use of /permissions player removegroup "+groupName, PermissionDefault.FALSE);
				removegroupPermForGroup.addParent(removegroupPerm, true);
				getServer().getPluginManager().addPermission(removegroupPermForGroup);
			}
			//addgroupPerm.recalculatePermissibles();
			//removegroupPerm.recalculatePermissibles();
		}
		catch(IllegalArgumentException ex){
			// The permissions are already defined; potentially the plugin or server was reloaded
		}

		// Register stuff
		getCommand("permissions").setExecutor(new PermissionsCommand(this));
		getCommand("permissions").setTabCompleter(new PermissionsTabComplete(this));
		getServer().getPluginManager().registerEvents(new PlayerPermChangeListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerCommandSendListener(this), this);

		// Register everyone online right now
		for(Player p : getServer().getOnlinePlayers()) registerPlayer(p);
		getLogger().fine("Enabled successfully, " + getServer().getOnlinePlayers().size() + " online players registered");
	}

	@Override public FileConfiguration getConfig(){
		return config;
	}

	@Override public void reloadConfig(){
		config = new YamlConfiguration();
		config.options().pathSeparator('/');
		try{
			config.load(configFile);
		}
		catch(InvalidConfigurationException ex){
			configLoadError = true;

			// extract line numbers from the exception if we can
			ArrayList<String> lines = new ArrayList<>();
			Pattern pattern = Pattern.compile("line (\\d+), column");
			Matcher matcher = pattern.matcher(ex.getMessage());
			while(matcher.find()){
				String lineNo = matcher.group(1);
				if(!lines.contains(lineNo)){
					lines.add(lineNo);
				}
			}

			// make a nice message
			StringBuilder msg = new StringBuilder("Your configuration is invalid! ");
			if(lines.isEmpty()){
				msg.append("Unable to find any line numbers.");
			}
			else{
				msg.append("Take a look at line(s): ").append(lines.get(0));
				for(String lineNo : lines.subList(1, lines.size())){
					msg.append(", ").append(lineNo);
				}
			}
			getLogger().severe(msg.toString());

			// save the whole error to config_error.txt
			try{
				File outFile = new File(getDataFolder(), "config_error.txt");
				PrintStream out = new PrintStream(new FileOutputStream(outFile));
				out.println("Use the following website to help you find and fix configuration errors:");
				out.println("https://yaml-online-parser.appspot.com/");
				out.println();
				out.println(ex.toString());
				out.close();
				getLogger().info("Saved the full error message to " + outFile);
			}
			catch(IOException ex2){
				getLogger().severe("Failed to save the full error message!");
			}

			// save a backup
			File backupFile = new File(getDataFolder(), "config_backup.yml");
			File sourceFile = new File(getDataFolder(), "config.yml");
			if(FileUtil.copy(sourceFile, backupFile)){
				getLogger().info("Saved a backup of your configuration to " + backupFile);
			}
			else{
				getLogger().severe("Failed to save a configuration backup!");
			}
		}
		catch(Exception ex){
			getLogger().log(Level.SEVERE, "Failed to load configuration", ex);
		}
	}

	@Override public void saveConfig(){
		// If there's no keys (such as in the event of a load failure) don't save
		if(!config.getKeys(true).isEmpty()){
			try{config.save(configFile);}
			catch(IOException ex){getLogger().log(Level.SEVERE, "Failed to save configuration", ex);}
		}
	}

	@Override public void onDisable(){
		// Unregister everyone
		for(Player p : getServer().getOnlinePlayers()) unregisterPlayer(p);
		getLogger().fine("Disabled successfully, " + getServer().getOnlinePlayers().size() + " online players unregistered");
	}

	// -- External API
	/**
	 * Get the group with the given name.
	 *
	 * @param groupName The name of the group.
	 * @return A Group if it exists or null otherwise.
	 */
	public Group getGroup(String groupName){
		ConfigurationSection node = getNode("groups");
		if(node != null){
			for(String key : node.getKeys(false)){
				if(key.equalsIgnoreCase(groupName)){
					return new Group(this, key);
				}
			}
		}
		return null;
	}

	// -- Plugin stuff

	protected void registerPlayer(Player player){
		if(permissions.containsKey(player.getUniqueId())){
			debug("Registering " + player.getName() + ": was already registered");
			unregisterPlayer(player);
		}
		PermissionAttachment attachment = player.addAttachment(this);
		permissions.put(player.getUniqueId(), attachment);
		calculateAttachment(player);
	}

	protected void unregisterPlayer(Player player){
		if(permissions.containsKey(player.getUniqueId())){
			try{
				player.removeAttachment(permissions.get(player.getUniqueId()));
			}
			catch(IllegalArgumentException ex){
				debug("Unregistering " + player.getName() + ": player did not have attachment");
			}
			permissions.remove(player.getUniqueId());
		}
		else{
			debug("Unregistering " + player.getName() + ": was not registered");
		}
	}

	protected void refreshForPlayer(UUID player){
		saveConfig();
		debug("Refreshing for player " + player);

		Player onlinePlayer = getServer().getPlayer(player);
		if(onlinePlayer != null){
			calculateAttachment(onlinePlayer);
		}
	}

	private void fillChildGroups(HashSet<String> childGroups, String group){
		if(childGroups.contains(group)) return;
		childGroups.add(group);

		for(String key : getNode("groups").getKeys(false)){
			for(String parent : getNode("groups/" + key).getStringList("inheritance")){
				if(parent.equalsIgnoreCase(group)){
					fillChildGroups(childGroups, key);
				}
			}
		}
	}

	protected void refreshForGroup(String group){
		saveConfig();

		// build the set of groups which are children of "group"
		// e.g. if Bob is only a member of "expert" which inherits "user",
		// he must be updated if the permissions of "user" change
		HashSet<String> childGroups = new HashSet<>();
		fillChildGroups(childGroups, group);
		debug("Refreshing for group " + group + " (total " + childGroups.size() + " subgroups)");

		for(UUID uuid : permissions.keySet()){
			Player player = getServer().getPlayer(uuid);
			ConfigurationSection node = getUserNode(player);

			// if the player isn't in the config, act like they're in default
			List<String> groupList = (node != null) ? node.getStringList("groups") : Collections.singletonList("default");
			for(String userGroup : groupList){
				if(childGroups.contains(userGroup)){
					calculateAttachment(player);
					break;
				}
			}
		}
	}

	protected void refreshPermissions(){
		debug("Refreshing all permissions (for " + permissions.size() + " players)");
		for(UUID player : permissions.keySet()){
			calculateAttachment(getServer().getPlayer(player));
		}
	}

	protected ConfigurationSection getNode(String node){
		if(getConfig().isConfigurationSection(node)) return getConfig().getConfigurationSection(node);
		node = node.toLowerCase();
		if(getConfig().isConfigurationSection(node)) return getConfig().getConfigurationSection(node);

		for(String entry : getConfig().getKeys(true)){
			if(node.equalsIgnoreCase(entry) && getConfig().isConfigurationSection(entry)){
				return getConfig().getConfigurationSection(entry);
			}
		}
		return null;
	}

	protected ConfigurationSection getUserNode(Player player){
		ConfigurationSection sec = getNode("users/" + player.getUniqueId());//TODO: case-insensitive username lookup
		if(sec == null){
			sec = getNode("users/" + player.getName());
			if(sec == null) return null;

			getConfig().set(sec.getCurrentPath(), null);
			getConfig().set("users/" + player.getUniqueId(), sec);
			sec.set("name", player.getName());
			debug("Migrated " + player.getName() + " to UUID " + player.getUniqueId());
			saveConfig();
		}

		// make sure name field matches
		if(!player.getName().equals(sec.getString("name"))){
			debug("Updating name of " + player.getUniqueId() + " to: " + player.getName());
			sec.set("name", player.getName());
			saveConfig();
		}

		return sec;
	}

	protected ConfigurationSection createNode(String node){
		ConfigurationSection sec = getConfig();
		for(String piece : node.split("/")){
			ConfigurationSection sec2 = getNode(sec == getConfig() ? piece : sec.getCurrentPath() + "/" + piece);
			if(sec2 == null){
				sec2 = sec.createSection(piece);
			}
			sec = sec2;
		}
		return sec;
	}

	protected HashMap<String, Boolean> getAllPerms(String desc, String path){
		ConfigurationSection node = getNode(path);

		int failures = 0;
		String firstFailure = "";

		// Make an attempt to autofix incorrect nesting
		boolean fixed = false;
		boolean fixedNow = true;
		while(fixedNow){
			fixedNow = false;
			for(String key : node.getKeys(true)){
				if(node.isBoolean(key) && key.contains("/")){
					node.set(key.replace("/", "."), node.getBoolean(key));
					node.set(key, null);
					fixed = fixedNow = true;
				}
				else if(node.isConfigurationSection(key) && node.getConfigurationSection(key).getKeys(true).isEmpty()){
					node.set(key, null);
					fixed = fixedNow = true;
				}
			}
		}
		if(fixed){
			getLogger().info("Fixed broken nesting in " + desc + ".");
			saveConfig();
		}

		LinkedHashMap<String, Boolean> result = new LinkedHashMap<>();
		// Do the actual getting of permissions
		for(String key : node.getKeys(false)){
			if(node.isBoolean(key)){
				result.put(key, node.getBoolean(key));
			}
			else{
				++failures;
				if(firstFailure.length() == 0){
					firstFailure = key;
				}
			}
		}

		if(failures == 1){
			getLogger().warning("In " + desc + ": " + firstFailure + " is non-boolean.");
		}
		else if(failures > 1){
			getLogger().warning("In " + desc + ": " + firstFailure + " is non-boolean (+" + (failures - 1) + " more).");
		}

		return result;
	}

	protected void debug(String message){
		if(getConfig().getBoolean("debug", false)) getLogger().info("Debug: " + message);
	}

	private static Field pField;
	@SuppressWarnings("unchecked")
	private Map<String, Boolean> reflectMap(PermissionAttachment attachment){
		try{
			if(pField == null){
				pField = PermissionAttachment.class.getDeclaredField("permissions");
				pField.setAccessible(true);
			}
			return (Map<String, Boolean>)pField.get(attachment);
		}
		catch(Exception e){throw new RuntimeException(e);}
	}
	protected void calculateAttachment(Player player){
		if(player == null){
			return;
		}
		PermissionAttachment attachment = permissions.get(player.getUniqueId());
		if(attachment == null){
			debug("Calculating permissions on " + player.getName() + ": attachment was null");
			return;
		}

		Map<String, Boolean> values = calculatePlayerPermissions(player, player.getWorld().getName());

		// Fill the attachment reflectively so we don't recalculate for each permission
		// it turns out there's a lot of permissions!
		Map<String, Boolean> dest = reflectMap(attachment);
		dest.clear();
		dest.putAll(values);
		debug("Calculated permissions on " + player.getName() + ": " + dest.size() + " values");

		player.recalculatePermissions();
	}

	private Map<String, Map<String, Boolean>> groupPermissions = new HashMap<>();
//	protected boolean checkGroupPermission(String group, String perm){
//		Map<String, Boolean> perms = groupPermissions.get(group);
//		return perms != null && perms.getOrDefault(perm, /*TODO: default perm value here?:*/false);
//	}

	// -- Private stuff

	// normally, LinkedHashMap.put (and thus putAll) will not reorder the list
	// if that key is already in the map, which we don't want - later puts should
	// always be bumped to the end of the list
	private <K, V> void putAll(Map<K, V> dest, Map<K, V> src){
		for(Map.Entry<K, V> entry : src.entrySet()){
			dest.remove(entry.getKey());
			dest.put(entry.getKey(), entry.getValue());
		}
	}

	private Map<String, Boolean> calculatePlayerPermissions(Player player, String world){
		ConfigurationSection node = getUserNode(player);

		// if the player isn't in the config, act like they're in default
		if(node == null){
			return calculateGroupPermissions("default", world);
		}

		String nodePath = node.getCurrentPath();
		Map<String, Boolean> perms = new LinkedHashMap<>();

		// first, apply the player's groups (getStringList returns an empty list if not found)
		// later groups override earlier groups
		for(String group : node.getStringList("groups")){
			putAll(perms, calculateGroupPermissions(group, world));
		}

		// now apply user-specific permissions
		if(getNode(nodePath + "/permissions") != null){
			putAll(perms, getAllPerms("user " + player, nodePath + "/permissions"));
		}

		// now apply world- and user-specific permissions
		if(getNode(nodePath + "/worlds/" + world) != null){
			putAll(perms, getAllPerms("user " + player + " world " + world, nodePath + "/worlds/" + world));
		}

		return perms;
	}

	private Map<String, Boolean> calculateGroupPermissions0(String group, String world, Set<String> recursionBuffer){
		final Map<String, Boolean> perms = new LinkedHashMap<>();
		final String groupNode = "groups/" + group;

		// If the group's not in the config, nothing
		if(getNode(groupNode) == null) return perms;

		recursionBuffer.add(group);

		// First apply any parent groups (see calculatePlayerPermissions for more)
		for(String parent : getNode(groupNode).getStringList("inheritance")){
			if(recursionBuffer.contains(parent)){
				getLogger().warning("In group " + group + ": recursive inheritance from " + parent);
				continue;
			}

			putAll(perms, calculateGroupPermissions0(parent, world, recursionBuffer));
		}

		// Now apply the group's permissions
		if(getNode(groupNode + "/permissions") != null){
			putAll(perms, getAllPerms("group " + group, groupNode + "/permissions"));
		}

		// Now apply world-specific permissions
		if(getNode(groupNode + "/worlds/" + world) != null){
			putAll(perms, getAllPerms("group " + group + " world " + world, groupNode + "/worlds/" + world));
		}

		groupPermissions.put(group, perms);
		return perms;
	}
	private Map<String, Boolean> calculateGroupPermissions(String group, String world){
		return calculateGroupPermissions0(group, world, new HashSet<>());
	}
}
