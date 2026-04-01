# MineGames

MineGames is a Paper `1.21+` casino plugin with two game types:

1. **MineGame**: reveal safe blocks, avoid mines, cash out at your chosen point.

![1](https://i.imgur.com/DTNVhJG.png)
![2](https://i.imgur.com/NGqeNLs.png)

2. **Roulette**: perpetual rounds where players bet on red/black/green.

![3](https://i.imgur.com/0mcRZ5v.png)
![4](https://i.imgur.com/1oyUPr8.png)

It uses Vault economy, supports per-station cosmetics, holograms, and casino frame animations.

## Requirements

- Java 21
- Paper 1.21+
- Vault
- A Vault-compatible economy plugin (for example EssentialsX Economy)

## Build

```powershell
mvn -DskipTests package
```

Output jar: `target/minegames-1.x.x.jar`

## Install

1. Place `minegames-1.x.x.jar` in `plugins/`.
2. Ensure Vault + economy plugin are installed.
3. Start/restart server.
4. Edit `plugins/MineGames/config.yml` as needed.
5. Create stations:
1. MineGame station: stand on your station block and run `/minegameadmin create`
2. Roulette station: stand at board center and run `/rouletteadmin create`

## Gameplay

### Mines

1. Stand on a MineGame station block.
2. Start a game: `/minegame <mines> <wager>`
3. Break/click tiles to reveal safe blocks.
4. Cash out with `/minegame cashout` or by breaking a frame block.
5. Lose by hitting a mine or timeout, win by clearing all safe tiles.

### Roulette

1. Stand near a Roulette station.
2. Place bet: `/roulette <red|black|green> <amount>`
3. Bets close when countdown ends, then spin/result phase resolves.
4. Payouts apply automatically for winning color bets.
5. Round resets and repeats continuously.

## Commands

### Player Commands

- Mines:
1. `/minegame <mines 1-24> <wager>`
2. `/minegame cashout`
- MineGame aliases: `/mine`, `/mines`
- Roulette:
1. `/roulette <red|black|green> <amount>`

### MineGame Admin (`mine.admin`)

Primary command: `/minegameadmin` (legacy alias: `/mineadmin`)

- Station lifecycle:
1. `/minegameadmin create`
2. `/minegameadmin remove`
3. `/minegameadmin regen`
4. `/minegameadmin list`
5. `/minegameadmin reload`
- Toggles:
1. `/minegameadmin holo <on|off>`
2. `/minegameadmin debug <on|off>`
- Global config:
1. `/minegameadmin set <path> <value>`
- Per-station cosmetics (or all stations):
1. `/minegameadmin setframe [all] <BLOCK|reset>`
2. `/minegameadmin sethidden [all] <BLOCK|reset>`
3. `/minegameadmin setsafe [all] <BLOCK|reset>`
4. `/minegameadmin setmine [all] <BLOCK|reset>`
- Per-station casino frame (or all stations):
1. `/minegameadmin casinoframe [all] <BLOCK> <pattern 1-10>`
2. `/minegameadmin casinoframe [all] mode <idle_only|always>`
3. `/minegameadmin casinoframe [all] <off|reset>`

### Roulette Admin (`roulette.admin`)

- Station lifecycle:
1. `/rouletteadmin create`
2. `/rouletteadmin remove`
3. `/rouletteadmin regen`
4. `/rouletteadmin list`
5. `/rouletteadmin reload`
- Global config:
1. `/rouletteadmin set <path> <value>`
- Per-station board cosmetics (or all stations):
1. `/rouletteadmin setframe [all] <BLOCK|reset>`
2. `/rouletteadmin setred [all] <BLOCK|reset>`
3. `/rouletteadmin setblack [all] <BLOCK|reset>`
4. `/rouletteadmin setgreen [all] <BLOCK|reset>`
5. `/rouletteadmin setselector [all] <BLOCK|reset>`
- Per-station casino frame (or all stations):
1. `/rouletteadmin casinoframe [all] <BLOCK> <pattern 1-10>`
2. `/rouletteadmin casinoframe [all] mode <always|betting_only>`
3. `/rouletteadmin casinoframe [all] <off|reset>`

## Permissions

- `mine.admin` (default: op)
- `roulette.admin` (default: op)

## Config Layout

- Mines:
1. `board.*`
2. `game.*`
3. `announcements.*`
4. `effects.*`
5. `hologram.*`
6. `messages.*`
7. `frame-animation.*`
- Roulette:
1. `roulette.*`
2. `roulette-frame-animation.*`

## Notes

- `set ...` commands edit global defaults.
- `setframe/setred/...` and `casinoframe` commands edit station overrides.
- Adding `all` applies cosmetic override commands to every station of that game type.
- Roulette color defaults are percent-based (`49.31 / 49.31 / 1.39`) and auto-scale with board size.
- Changing Roulette selector block clears old selector blocks from the selector layer before placing new ones.
- Removing MineGame/Roulette stations restores original world blocks for stations created on current versions (snapshot-based restore).
- Roulette station creation anchors the board directly under the admin's feet (replaces floor blocks there).
- Holograms are configured with no-wrap text display behavior for more consistent spacing.

## Storage

- `plugins/MineGames/config.yml` (global settings)
- `plugins/MineGames/stations.yml` (MineGame stations + overrides)
- `plugins/MineGames/roulette_stations.yml` (Roulette stations + overrides)
- `plugins/MineGames/mines_restore.yml` (MineGame original-block snapshots for restore on station removal)
- `plugins/MineGames/roulette_restore.yml` (Roulette original-block snapshots for restore on station removal)

## License

GPLv3. See [LICENSE](LICENSE).
