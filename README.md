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
