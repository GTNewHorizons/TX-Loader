[![](http://cf.way2muchnoise.eu/706505.svg)](https://www.curseforge.com/minecraft/mc-mods/tx-loader) [![](http://cf.way2muchnoise.eu/versions/706505.svg)](https://www.curseforge.com/minecraft/mc-mods/tx-loader)

TX Loader
=================

**Downloads:** [Modrinth](https://modrinth.com/mod/tx-loader) [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tx-loader)

### Features
- Provides a directory which acts as any other resource pack (`./config/txloader/load/`)
- Provides a directory which overrides all other assets with the same resource locations (`./config/txloader/forceload/`)
- Official assets can be downloaded automatically at startup from the official Mojang servers (Mojang's [Brand and Asset Guidelines](https://www.minecraft.net/en-us/terms) are not violated this way). Pack devs can do this via a JSON config (`./config/txloader/config.json`), mod devs can use a builder class via `glowredman.txloader.TXLoaderCore#getAssetBuilder`

### Config Format

|Field|Type|Default Value|Description|
|:---:|:---:|:---:|:---|
|resourceLocation|String||Source path|
|resourceLocationOverride|String|`null`|Destination path, if you want it to be different from the source path|
|forceLoad|boolean|`false`|If true, this asset will be prioritized over assets from other resource packs|
|version|String||The version from which this asset should be taken(valid versions can be found [here](https://launchermeta.mojang.com/mc/game/version_manifest.json))|

*Example config:*
```json
[
  {
    "resourceLocation": "minecraft/lang/en_us.json",
    "version": "1.19.2"
  },
  {
    "resourceLocation": "minecraft/sounds/block/netherrack/break1.ogg",
    "resourceLocationOverride": "minecraft/sounds/block/netherrack/step1.ogg",
    "forceLoad": true,
    "version": "1.18"
  }
]
```

### JVM Arguments
|Name|Default Value|Description|
|:---|:---|:---|
|`-Dtxloader.keepalive.io`|10000|How long (in milliseconds) idle file IO threads are kept alive before being terminated|
|`-Dtxloader.keepalive.net`|10000|How long (in milliseconds) idle network threads are kept alive before being terminated|
|`-Dtxloader.poolsize.io`|32|Maximum number of file IO threads|
|`-Dtxloader.poolsize.net`|16|Maximum number of network threads|
|`-Dtxloader.timeout.connect`|5000|How long (in milliseconds) establishing a connection to a remote resource is attempted|
|`-Dtxloader.timeout.read`|10000|How long (in milliseconds) beginning to read data from a remote resource is allowed to take before the connection is terminated|


### TODO
- Allow some assets to be downloaded server-side too (maybe introduce a new flag in `Asset` for it)
- Clean up `RemoteHandler`
- Let config-driven assets override mod-driven assets (right now it's the other way around)
  - Clarify: should mods even be allowed to defined force-loaded assets?
- Improve error message if `/tx` command has wrong number of arguments
- Re-add progress bar
