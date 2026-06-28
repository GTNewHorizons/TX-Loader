[![](http://cf.way2muchnoise.eu/706505.svg)](https://www.curseforge.com/minecraft/mc-mods/tx-loader) [![](http://cf.way2muchnoise.eu/versions/706505.svg)](https://www.curseforge.com/minecraft/mc-mods/tx-loader)

TX Loader
=================

**Downloads:** [Modrinth](https://modrinth.com/mod/tx-loader) [CurseForge](https://www.curseforge.com/minecraft/mc-mods/tx-loader)

### Features
- Provides a directory which acts as any other resource pack (`./config/txloader/load/`)
- Provides a directory which overrides all other assets with the same resource locations (`./config/txloader/forceload/`)
- Official assets can be downloaded automatically at startup from the official Mojang servers (Mojang's [Brand and Asset Guidelines](https://www.minecraft.net/en-us/terms#terms-brand_guidelines) are not violated this way). Pack devs can do this via a JSON config (`./config/txloader/config.json`), mod devs can use a builder class via `glowredman.txloader.TXLoaderCore#getAssetBuilder`
- Resource packs in the `resourcepacks/` folder can auto-enable themselves on first boot via a `txloader` block in their `pack.mcmeta` (see [Force-loading resource packs](#force-loading-resource-packs))

### Config Format

|Field|Type|Default Value|Description|
|:---:|:---:|:---:|:---|
|resourceLocation|String||Source path|
|resourceLocationOverride|String|`null`|Destination path, if you want it to be different from the source path|
|forceLoad|boolean|`false`|If true, this asset will be prioritized over assets from other resource packs|
|version|String|latest release|The version from which this asset should be taken(valid versions can be found [here](https://launchermeta.mojang.com/mc/game/version_manifest.json))<br>*It is recommended to populate this field*|

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

### Force-loading resource packs

A resource pack placed in the `resourcepacks/` folder can ask to be enabled automatically the first time the game boots with that pack present. The pack opts in by adding a `txloader` section to its `pack.mcmeta` (this works for both folder packs and `.zip` packs):

```json
{
  "pack": {
    "pack_format": 1,
    "description": "My Pack"
  },
  "txloader": {
    "forceLoad": true,
    "priority": "bottom"
  }
}
```

|Field|Type|Default|Description|
|:---:|:---:|:---:|:---|
|`forceLoad`|boolean|`false`|If true, TX Loader enables this pack automatically on the first boot it is detected.|
|`priority`|String|`"bottom"`|`"top"` places the pack above your other selected packs (it overrides them); `"bottom"` places it below them (your other packs win on conflict).|

The pack is enabled only **once**. TX Loader records which packs it has force-loaded in `./config/txloader/forceloaded.json` (keyed by filename, with the timestamp of first load). After that you can disable the pack in the normal Resource Packs screen and it will stay disabled - it will never force itself back on.
