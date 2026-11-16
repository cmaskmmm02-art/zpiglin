# ZPiglin Watch (Fabric, Minecraft 1.21.11)

If no zombified piglin is on your screen (loaded but off-screen or behind blocks does not count),
a chat message that only you can see says "closing in 15 seconds" and a countdown starts.
Seeing a zombified piglin cancels it. If the countdown ends, you are disconnected.

Settings are the constants at the top of `ZPiglinWatchClient.java`:
`COUNTDOWN_SECONDS`, `DISCONNECT_AT_END`, `ONLY_IN_NETHER`, `MAX_DISTANCE`, `REQUIRE_LINE_OF_SIGHT`.
