# EntityAiRange

Paper plugin for Minecraft 26.3. It disables AI for loaded mobs when no online player is within 128 blocks and restores AI when a player comes back within range.

## Compatibility

- Paper 26.3 (compiled against Paper API `26.3.build.157-beta`)
- Java 25
- Paper forks may work, but are not tested here.

## Build

From this folder, with Maven and Java 25 installed:

```sh
mvn package
```

The plugin JAR is written to `target/entity-ai-range-1.0.0.jar`. Copy it to the server's `plugins` folder and restart Paper. Back up the world before testing.

## Behavior and limitations

- The plugin checks loaded mobs every 5 seconds by default. It compares distance to every online player in the same world.
- It disables AI only for mobs whose AI was enabled. Entities already saved without AI are left alone, so villagers already handled by the Stillagers datapack are not claimed by this plugin.
- A small persistent marker records which mobs this plugin changed. If a marked mob loads within range after restart, its AI is restored. On plugin shutdown, marked mobs in loaded chunks are restored.
- `/entityairange status` reports the number of managed mobs in loaded chunks.
- `/entityairange restore` restores managed mobs in loaded chunks and pauses new AI changes. Marked mobs in unloaded chunks are restored as their chunks load.
- `/entityairange` opens the in-game settings menu. The redstone and emerald buttons change the distance in 16- or 64-block steps; the clock resets it to 128 blocks. Changes save immediately. Only players with `entityairange.admin` (operators by default) can open and use the menu.
- The menu constrains the distance to 16–1024 blocks.
- `/entityairange restore` pauses automatic management until the next server restart; `/entityairange resume` starts it again.
- If removing the plugin permanently, first run `/entityairange restore`, then visit/load areas containing managed mobs before removing the plugin. Unloaded mobs retain their marker and will not be restored after the plugin is removed.
- Disabling AI also stops normal mob movement, pathfinding, targeting, and other AI behavior. Mob farms, raids, pets, and other systems may behave differently. Test on a backup/staging copy first.
- The plugin scans loaded living entities every scan interval. On very large servers, monitor MSPT after enabling it and increase `scan-interval-ticks` if the scan itself becomes noticeable.

## Configuration

`plugins/EntityAiRange/config.yml`:

```yaml
disable-distance: 128
scan-interval-ticks: 100
log-scan-summary: false
```

`disable-distance` and `scan-interval-ticks` are read when the plugin starts; restart after changing them.

The project includes a GitHub Actions build. On each push, the workflow builds the JAR with Java 25 and publishes it as a downloadable workflow artifact.

## Interaction with Stillagers

Stillagers villagers already have AI disabled, so this plugin does not change or take ownership of those villagers. Do not use this plugin as a second villager restock system; keep trade restocking configured in Stillagers.
