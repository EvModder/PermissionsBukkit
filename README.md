PermissionsBukkit
======================

Original Author: [ConspiracyWizard](https://github.com/SpaceManiac)\
Updated by: [EvModder](https://github.com/EvModder), [warriordog](https://github.com/warriordog)\
<br>


A plugin providing groups and other permissions configuration for Bukkit's built-in permissions architecture.

Sample configuration file and more info on how the configuration is laid out follows:

```yaml
# PermissionsBukkit configuration file
# 
# A permission node is a string like 'permissions.help', usually starting
# with the name of the plugin. Refer to a plugin's documentation for what
# permissions it cares about.
# Each node should be followed by true to grant that permission or false to
# revoke it, e.g. 'permissions.help: true'.
# Some plugins provide permission nodes that map to a group of permissions -
# for example, PermissionsBukkit has 'permissions.*', which can grant/revoke
# all admin permissions at once.
# 
# Users inherit permissions from the groups they are a part of. If a user is
# not specified here, or does not have a 'groups' node, they will be in the
# group 'default'. Any permissions specified for individual users will
# override their group permissions.
# World-specific permissions can be assigned with a 'worlds:' entry.
# 
# Groups can also inherit permissions from other groups. Like user
# permissions, groups may override the permissions of their parent group(s).
# Unlike users, groups do NOT automatically inherit from default.
# World permissions can also be assigned to groups with a 'worlds:' entry.

users:
  Notch:
    permissions:
      permissions.info: true
    groups:
    - admin
groups:
  default:
    permissions:
      permissions.info: false
  admin:
    permissions:
      permissions.*: true
    inheritance:
    - mod
  mod:
    permissions:
      permissions.info: true
    worlds:
      creative:
        coolplugin.item: true
    inheritance:
    - default

debug: false
command-permission-message: '&cYou do not have permissions to do that.'
hide-namespaced-commands-in-tab-complete: false
hide-commands-with-null-permission: false
hide-command-aliases-in-tab-complete: false
hide-specific-commands:
- icanhasbukkit
```
