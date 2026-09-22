# Husk

Say hi to Husk, a tiny limbo server thingy for Minecraft: Java Edition. You join, you float in the void, that's pretty much it, yea not sure what to say lol.  
Maybe useful for server networks for restarts or somthing?

One Husk instance accepts every client from 1.8 to 26.3. It reads the protocol version from the handshake and speaks that version for the rest of the connection.

Husk also loads extensions called roots. A root is a jar you drop into a folder, much like a Bukkit plugin, and it gets a small API for events, scheduling and talking to players.

## Compatibility

Every version below reaches the void. "Tested in client" means someone joined with that exact vanilla client. The others go through an automated test that runs the real network pipeline from handshake to spawn for every protocol number.

| Minecraft         | Protocol   | Joins | Tested in client |
|-------------------|------------|-------|------------------|
| 1.8.x             | 47         | yes   | 1.8              |
| 1.9 to 1.12.2     | 107 to 340 | yes   |                  |
| 1.13.x            | 393 to 404 | yes   |                  |
| 1.14.x            | 477 to 498 | yes   | 1.14             |
| 1.15.x            | 573 to 578 | yes   |                  |
| 1.16 to 1.16.5    | 735 to 754 | yes   | 1.16.5           |
| 1.17.x            | 755, 756   | yes   |                  |
| 1.18 to 1.18.2    | 757, 758   | yes   | 1.18.2           |
| 1.19 to 1.19.4    | 759 to 762 | yes   | 1.19.2           |
| 1.20 to 1.20.4    | 763 to 765 | yes   | 1.20.4           |
| 1.20.5 to 1.21.11 | 766 to 774 | yes   | 1.21.11          |
| 26.1              | 775        | yes   |                  |
| 26.2              | 776        | yes   |                  |
| 26.3              | 777        | yes   | 26.3             |

Only offline mode is supported for now. Players get the UUID their client sends (1.19.1 and later), or the usual `OfflinePlayer:<name>` UUID on older versions.

## Running it

You need JDK 25.

```bash
./gradlew :husk-server:run
```

On the first start Husk writes `husk.conf` into the working directory. Edit it and restart. The options are documented in the file itself: host and port, the dimension players spawn in, whether they show up in the tab list, and the server list MOTD (written in MiniMessage). Delete the file to get the defaults back.

Set `HUSK_DEBUG=1` to log every packet going in and out.

## Writing a root

Depend on `husk-api` as `compileOnly`, implement `lol.simeon.husk.api.Root` and point to your class in the jar manifest:

```kotlin
tasks.jar {
    manifest {
        attributes(
            "Husk-Root-Main" to "com.example.MyRoot",
            "Husk-Root-Name" to "my-root",
            "Husk-Root-Version" to project.version.toString(),
            "Husk-Api-Version" to "1.0",
        )
    }
}
```

Put the jar in `roots/` and start the server. `husk-root-example` is a working minimal root.

## Updating to a new Minecraft version

Two tools in `tools/` do most of the work:

- `generate-packet-ids.sh` builds the packet id tables from [minecraft-data](https://github.com/PrismarineJS/minecraft-data). For versions that aren't in minecraft-data yet, it can also read the `packets.json` report from the vanilla data generator. That's how 26.3 was added.
- `derive-registries.py` rebuilds a version's registry and tag data straight from its client jar. On 26.2 it reproduces a live server capture exactly.

Both scripts explain their usage at the top of the file.

## Roadmap

- Make the player API do something: chat messages, titles, action bar and boss bars through Adventure's `Audience`.
- Chat on versions before 1.19 and titles on versions before 1.17. They use older packets that Husk doesn't send yet.
- Boss bars from 1.9 up. 1.8 has no boss bar packet, so those players just won't see one.
- Join and quit events for roots, and a real player list behind `HuskServer.players()`.
- Send the configured brand and enforce `max-players`.

## Special thanks

- [minecraft-data](https://github.com/PrismarineJS/minecraft-data) by PrismarineJS. Their protocol definitions are where most of the packet ids and field layouts in Husk come from. Supporting 1.8 through 26.3 would have been a lot more painful without them.

## License

MIT, see [LICENSE](LICENSE).
