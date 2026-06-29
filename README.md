# MineGames

![0](https://i.imgur.com/NO1MpCA.png)

MineGames is a Paper `1.21+` casino plugin with three game types:

1. **MineGame**: reveal safe blocks, avoid mines, cash out at your chosen point.

![1](https://i.imgur.com/DTNVhJG.png)
![2](https://i.imgur.com/NGqeNLs.png)

2. **Roulette**: perpetual rounds where players bet on red/black/green.

![3](https://i.imgur.com/0mcRZ5v.png)
![4](https://i.imgur.com/1oyUPr8.png)

3. **Slots**: lever-driven reels with configurable widths, rows, frames, and payouts.

![5](https://i.imgur.com/cj5fCTH.png)

All games use Vault economy, support per-station cosmetics, holograms, and casino frame animations.

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
3. Slots station: stand where you want the machine and run `/slotsadmin create [3-8] [1|2]`

Join gifts are also supported. New players can receive a configurable welcome payout once, with the message and amount controlled in `config.yml` and via `/minegamesjoin`.

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

### Slots

1. Stand near a Slots station.
2. Pull the lever to spin and pay the configured wager.
3. Reels animate for a few seconds and then stop from left to right.
4. Matching the winning block pays out based on how many appear or which payline lands.
5. The station can be customized with outer frame, inner frame, winning block, row count, and lever-side frame animation.

## Winner Math & RNG

1. **MineGame**
   - Mine positions are generated once when the round starts with `Math.random()`, using `generateMines(mines, gridSize * gridSize)`.
   - Mines are placed uniformly without replacement, so the same cell cannot become a mine twice.
   - The player wins by revealing enough safe tiles before hitting a mine or timing out.
   - Payout is based on revealed safe tiles. If `minegame.game.max-multiplier` is set above `0`, the multiplier scales linearly from `1x` up to the configured max. Otherwise, it uses the inverse of the survival probability with a house-edge factor applied.
   - The survival math uses combinations: `surviveProbability = C(safe, revealed) / C(total, revealed)`.

2. **Roulette**
   - Each round builds a full board pattern from the configured color percentages: `roulette.red-percent`, `roulette.black-percent`, and `roulette.green-percent`.
   - The counts are normalized to the board size, then green cells are assigned randomly and the remaining cells are shuffled with Java `Random`.
   - When the spin resolves, the selector lands on a random board index and the color stored at that index becomes the winning color.
   - Payout uses the configured color multiplier, then applies the house-edge factor: `effectiveMultiplier = rawMultiplier * (1 - houseEdgePercent / 100)`.

3. **Slots**
   - Each reel cell is filled independently from `slots.blocks.reel-options` using Java `Random`.
   - The configured winning block is always included in the symbol pool, even if it is omitted from the config.
   - A win is counted by matching the station’s `slots.blocks.winning` material across the final grid.
   - For single-row stations, the total number of matching symbols is used directly. For two-row stations, the code checks top line, bottom line, diagonal, reverse diagonal, full screen, and mixed line patterns.
   - Payout is `wager * multiplier`, where the multiplier comes from `slots.payout-multipliers` and is then scaled by station size and the detected pattern.

4. **RNG notes**
   - MineGame uses `Math.random()` for mine placement.
   - Roulette and Slots use a shared `java.util.Random` instance.
   - None of the games use seeded or cryptographic RNG, so results are game-random rather than replay-deterministic.

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
- House accounting:
1. `/minegameadmin housebalance`
2. `/minegameadmin housewithdraw <amount|all>`
- Toggles:
1. `/minegameadmin holo <on|off>`
2. `/minegameadmin debug <on|off>`
- Global config:
1. `/minegameadmin set [global] <path> <value>`
2. `/minegameadmin set [global] <path>` (shows current value)
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
- House accounting:
1. `/rouletteadmin housebalance`
2. `/rouletteadmin housewithdraw <amount|all>`
- Global config:
1. `/rouletteadmin set [global] <path> <value>`
2. `/rouletteadmin set [global] <path>` (shows current value)
3. `/rouletteadmin set global <path> <value>` forces a global change even when standing near a station
- Per-station board cosmetics (or all stations):
1. `/rouletteadmin setframe [all] <BLOCK|reset>`
2. `/rouletteadmin setred [all] <BLOCK|reset>`
3. `/rouletteadmin setblack [all] <BLOCK|reset>`
4. `/rouletteadmin setgreen [all] <BLOCK|reset>`
5. `/rouletteadmin setselector [all] <BLOCK|reset>`
- Per-station board size:
1. `/rouletteadmin set board-size <value>` while standing near a station changes that station only
- Per-station casino frame (or all stations):
1. `/rouletteadmin casinoframe [all] <BLOCK> <pattern 1-10>`
2. `/rouletteadmin casinoframe [all] mode <always|betting_only>`
3. `/rouletteadmin casinoframe [all] <off|reset>`

### Slots Admin (`slots.admin`)

- Station lifecycle:
1. `/slotsadmin create [3-8] [1|2]`
2. `/slotsadmin remove`
3. `/slotsadmin regen`
4. `/slotsadmin list`
5. `/slotsadmin reload`
- House accounting:
1. `/slotsadmin housebalance`
2. `/slotsadmin housewithdraw <amount|all>`
- Global config:
1. `/slotsadmin set [global] <path> <value>`
2. `/slotsadmin set [global] <path>` (shows current value)
3. `/slotsadmin set global <path> <value>` forces a global change even when standing near a station
- Per-station cosmetics (or all stations):
1. `/slotsadmin setouterframe [all] <BLOCK|reset>`
2. `/slotsadmin setinnerframe [all] <BLOCK|reset>`
3. `/slotsadmin setwinning [all] <BLOCK|reset>`
- Per-station price:
1. `/slotsadmin set cost-per-spin <value>` while standing near a station changes that station only
- Per-station casino frame (or all stations):
1. `/slotsadmin casinoframe [all] <BLOCK> <pattern 1-10>`
2. `/slotsadmin casinoframe [all] mode <idle_only|always>`
3. `/slotsadmin casinoframe [all] <off|reset>`

### Join Gift

- `/minegamesjoin true|false` enables or disables the first-join gift.
- `/minegamesjoin set <amount>` changes the welcome payout.
- Seen players are stored in `join_rewards.yml`, so each player only receives the gift once.
- The welcome message is editable under `messages.join-gift.welcome` in `config.yml`.

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
- MineGame frame height toggle:
1. `board.frame-one-higher`
2. `true` = frame/grid one block above beacon
3. `false` = frame/grid at beacon level
- Roulette:
1. `roulette.*`
2. `roulette-frame-animation.*`

## Distance / Activation / Hologram Settings

- Global casino frame activation distance (used by both game types):
1. `casino-frame-activation-distance` (default `20.0`)
2. `frame-animation.interval-ticks` (MineGame frame animation speed)
3. `roulette-frame-animation.interval-ticks` (Roulette frame animation speed)

- MineGame hologram visibility:
1. `hologram.view-range`
2. `hologram.behind-beacon-distance`
3. `hologram.base-height`
4. `hologram.line-spacing`

- Roulette station activation + hologram visibility:
1. `roulette.activation-distance-from-frame` (players must be near frame for active spinning)
2. `roulette.max-bet-distance` (max distance to place bets when not standing directly on board)
3. `roulette.hologram-view-range`
4. `roulette.hologram-height`
5. `roulette.hologram-line-spacing`
6. `roulette.hologram-title-gap`
7. `roulette.hologram-section-gap`

## Notes

- `set ...` commands edit the nearest station when the setting is station-local, unless you use `set global ...` to force a global default.
- `setframe/setred/...` and `casinoframe` commands edit station overrides.
- Adding `all` applies cosmetic override commands to every station of that game type.
- Mines, roulette, and slots rebaseline their saved footprints when a size change would otherwise leave old blocks behind.
- `housebalance`/`housewithdraw` are admin-only (`mine.admin` / `roulette.admin`).
- `housewithdraw` pays the withdrawn amount directly to the admin executing the command.
- MineGame board height is controlled by `board.frame-one-higher` (settable via `/minegameadmin set board.frame-one-higher <true|false>`).
- Roulette color defaults are percent-based (`48.61 / 48.61 / 2.78`) and auto-scale with board size.
- Changing Roulette selector block clears old selector blocks from the selector layer before placing new ones.
- Removing MineGame/Roulette/Slots stations restores original world blocks for stations created on current versions (snapshot-based restore).
- Roulette station creation anchors the board directly under the admin's feet (replaces floor blocks there).
- Holograms are configured with no-wrap text display behavior for more consistent spacing.

## Storage

- `plugins/MineGames/config.yml` (global settings)
- `plugins/MineGames/stations.yml` (MineGame stations + overrides)
- `plugins/MineGames/roulette_stations.yml` (Roulette stations + overrides)
- `plugins/MineGames/mines_restore.yml` (MineGame original-block snapshots for restore on station removal)
- `plugins/MineGames/roulette_restore.yml` (Roulette original-block snapshots for restore on station removal)
- `plugins/MineGames/house_balances.yml` (separate MineGame/Roulette house balance + wager/payout totals)

## License

GPLv3. See [LICENSE](LICENSE).
