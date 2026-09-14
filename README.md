# MineGames

![0](https://i.imgur.com/NO1MpCA.png)

MineGames is a modern Minecraft `1.21 -> 26.2` casino plugin with five casino game types plus physical dice and portable craps:

1. **MineGame**: reveal safe blocks, avoid mines, cash out at your chosen point.

![1](https://i.imgur.com/DTNVhJG.png)
![2](https://i.imgur.com/NGqeNLs.png)

2. **Roulette**: perpetual rounds where players bet on red/black/green.

![3](https://i.imgur.com/0mcRZ5v.png)
![4](https://i.imgur.com/1oyUPr8.png)

3. **Slots**: lever-driven reels with configurable widths, rows, frames, and payouts.

![5](https://i.imgur.com/cj5fCTH.png)
![Slott](https://i.imgur.com/b0Lhgha.png)

4. **Fights**: wager on randomly equipped mobs battling inside a protected arena.

![Fights](https://i.imgur.com/31TJ2ly.png)

5. **Chicken**: choose a coloured tile, cash out before lightning, and collect multiplier stars while a chicken roams the board.

![6](https://i.imgur.com/IJB96Rp.png)

6. **Physical Dice and Craps**: throw custom-textured, server-simulated dice in the world, then play a portable craps round anywhere with `/craps`.

![Dice](https://i.imgur.com/oZ51gAS.png)

Casino games use Vault economy and support per-station cosmetics, holograms, and casino frame animations. Physical dice use vanilla display entities and do not require a client mod or resource pack.

## Platform compatibility

The same plugin jar targets Bukkit/CraftBukkit, Spigot, Paper, Purpur, and Folia. Folia-aware scheduling is selected at runtime, while the build profiles can be used to verify each API family:

```text
mvn package                 # Paper (default)
mvn -Pspigot package       # Bukkit/CraftBukkit and Spigot API
mvn -Ppurpur package
mvn -Pfolia package
```

## Requirements

- Java 21
- Minecraft 1.21+ on Bukkit/CraftBukkit, Spigot, Paper, Purpur, or Folia
- Vault (for example [VaultUnlocked](https://modrinth.com/plugin/vaultunlocked))
- A Vault-compatible economy plugin (for example [EconomyProvider by ilius](https://modrinth.com/plugin/economyprovider-by-ilius))

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
4. Fights station: stand at the arena origin and run `/fightadmin create [odd-size] [fighters]`

## Casino Settings GUI

Operators can open the inventory-based settings menu with:

```text
/casinogui
```

The GUI includes MineGame, Roulette, Slots, Fights, Chicken, and Dice. Select a game to edit global settings, browse its stations, and edit supported per-station overrides. Global changes override existing station settings. Previous/Next navigate settings pages, and Back/Stations provide navigation. Chicken settings are global; Dice includes physical-dice and craps settings.

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
6. Shelf mode uses Minecraft shelves instead of solid reel blocks. Each shelf item slot is an independent reel; shelves can be placed side-by-side and stacked vertically with the same payouts, wager buttons, hologram, and optional casino frame as regular Slots.

### Fights

1. Stand near a Fights station.
2. Place a bet: `/fighter <fighter-number> <amount>`.
3. The first accepted bet opens the countdown; fighters spawn only when betting closes.
4. Two fighters run as a duel. Three or more fighters run as a free-for-all.
5. Fighters receive random weapons, armor, enchantments, colors, and supported variants, then fight until one remains. The pool includes zombie, husk, drowned, zombie villager, skeleton, stray, bogged, wither skeleton, pillager, piglin, piglin brute, and zombified piglin fighters. Winning bets pay automatically, losing bettors are notified, and the winner is celebrated with fireworks.
6. Set `fights.show-bettor-name-on-fighter` to show the bettor in the mob nameplate. Set `fights.fireworks-for-winning-bettors` to `true` (the default) to add fireworks from all four arena corners for winning bets.
7. All rolled weapons, including tridents, use armor- and enchantment-aware damage calculations.

### Chicken

1. Stand near a Chicken board.
2. Bet on a colour with /chicken <red|blue|gold|green> <amount>.
3. The first bet opens the countdown. At game start, the board reshuffles red, blue, Gold Block, green, and black dead tiles before the chicken drops in.
4. The multiplier rises continuously above the chicken. Nether Star pickups add the configured pickup bonus with sparkle and sound effects.
5. Cash out with /chicken cashout or hit the lamp frame. Uncashed wagers win only on their selected colour; black/dead tiles pay nothing.
6. Lightning timing is pre-rolled between the configured minimum and maximum. The randomness curve favours early/mid strikes by default.

### Physical Dice

1. Run `/dice` to receive the configured number of individual dice (two by default).
2. `/dice` fills only a missing amount. Existing inventory dice are reused, and the command will not issue more while you have active dice on the ground; collect them or wait 30 seconds for them to despawn.
3. Right-click while holding a die to throw it. Dice travel, fall, bounce, tumble, and settle on a server-selected face.
4. Right-click a finished die to pick it up. Dice use vanilla `PLAYER_HEAD` items and `ItemDisplay` entities; no client mod, resource pack, or chat roll is required.
5. Dice are marked with PersistentDataContainer data, so their identity is not based on the display name alone. Each die can be thrown and settled independently, including by multiple players at once. Uncollected dice despawn after 30 seconds.

### Craps

1. Start a portable craps round anywhere with `/craps <bet>`; the bet is withdrawn through Vault and two session-specific physical dice are prepared. Existing dice in the player inventory are reused, with only missing dice created.
2. Throw both dice and wait for them to settle. On the come-out roll, 7 or 11 wins, 2/3/12 loses, and any other total establishes the Point.
3. After a Point is established, throw the same two dice again. Matching the Point wins; rolling 7 loses; other totals continue the round.
4. Dice are returned between rolls and after the round ends. Use `/craps cancel` to cancel an active round and refund its wager. Timed-out or invalid sessions are cleaned up safely.

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

4. **Fights**
   - Each fighter is selected independently from the supported zombie, skeleton, pillager, and piglin variants at round reset.
   - Weapons and armor are selected independently from the configured in-code pools. Leather armor receives a random color.
   - Each rolled item receives a random set of compatible enchantments (up to three), with a random level up to the enchantment maximum.
   - Weapon base damage, Sharpness/Smite, Power, Impaling, Fire Aspect, armor points, Protection, Projectile Protection, and Thorns all influence combat damage or retaliation.
   - Ranged attacks, including tridents, use the same isolated fighter damage path and apply armor/enchantment mitigation before health is reduced.
   - A winning bet pays `bet * fights.payout-multiplier * (fighter-count / 2)`. The configured multiplier and fighter count determine the potential payout shown on the hologram.

5. **Chicken**
   - The colour/dead-tile layout and lightning countdown are pre-rolled before the chicken spawns.
   - Lightning is sampled between the configured minimum and maximum in 0.1-second increments. A randomness curve of 1.0 is uniform; higher values favour earlier strikes.
   - The chicken follows server-controlled roaming paths. Its path is visual only and cannot alter the pre-rolled lightning timing.
   - The live multiplier grows continuously and every Nether Star adds the configured pickup bonus. A cash-out pays the live multiplier; an uncleared bet must also match the final colour and then receives that colour bonus.

6. **Physical Dice and Craps**
   - Each die selects an independent result from 1 to 6 when it is thrown. The animation is server-side and the die rotates toward the known final orientation for that result while settling.
   - Dice use `ThreadLocalRandom` for gameplay results and quaternion-based display transformations for three-dimensional tumbling.
   - Craps uses standard come-out/Point rules: 7 or 11 wins on the come-out roll, 2/3/12 loses, a Point must be repeated before 7, and other totals continue the round.

7. **RNG notes**
   - MineGame uses `Math.random()` for mine placement.
   - Roulette and Slots use a shared `java.util.Random` instance.
   - Fighter types, equipment materials, leather colors, enchantment choices, and enchantment levels use Java `ThreadLocalRandom` (with compatible enchantments shuffled before selection).
   - The random rolls are generated when fighters are spawned and equipped; they are not seeded for replayable outcomes.
   - None of the games use seeded or cryptographic RNG, so results are game-random rather than replay-deterministic.

## Commands

### Player Commands

- Mines:
1. `/minegame <mines 1-24> <wager>`
2. `/minegame cashout`
- MineGame aliases: `/mine`, `/mines`
- Roulette:
1. `/roulette <red|black|green> <amount>`
- Fights:
1. `/fighter <fighter-number> <amount>`
- Chicken:
1. /chicken <red|blue|gold|green> <amount>
2. /chicken cashout
- Physical dice:
1. `/dice`
2. Right-click a die to throw it; right-click a finished die to retrieve it.
- Craps:
1. `/craps <bet>`
2. `/craps cancel`

### Dice Admin (`dice.admin`)

Primary command: `/diceadmin`

- View/reload:
1. `/diceadmin settings`
2. `/diceadmin reload`
- General dice settings:
1. `/diceadmin color <red|black|white|blue|green|yellow|orange|cyan|magenta|lime|light_blue> [craps|all]`
2. `/diceadmin player-alert <on|off> [craps|all]`
3. `/diceadmin server-alert <on|off> [craps|all]`
4. `/diceadmin amount <1-64>`
5. `/diceadmin players <on|off>`
6. `/diceadmin glow <on|off> [craps|all]`
7. `/diceadmin particles <on|off> [craps|all]`
8. `/diceadmin rate-limit <amount|off> [24h|day]`
- Craps:
1. `/diceadmin craps <on|off>`
2. `/diceadmin housebalance`
3. `/diceadmin housewithdraw <amount|all>`

The optional `craps` scope changes only craps. The `all`/default scope changes normal dice; craps settings use `inherit` by default, so they follow the normal dice setting until overridden.

`rate-limit` applies separately to each player. It limits successful `/dice` grants and `/craps` round starts; `off` disables it, `24h` uses a literal rolling 24-hour window, and `day` resets at the next Minecraft game day.

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
- Public result announcements:
1. `/rouletteadmin set global announcements.broadcast-win <true|false>`
2. `/rouletteadmin set global announcements.broadcast-loss <true|false>`
3. `/slotsadmin set global announcements.broadcast-win <true|false>`
4. `/slotsadmin set global announcements.broadcast-loss <true|false>`
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
3. Global settings override existing station overrides; for example, `/rouletteadmin set global announcements.broadcast-win true`
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
2. /slotsadmin create shelves <shelves-wide> <shelves-high> (each shelf item slot is an independent reel)
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
3. Global settings override existing station overrides; for example, `/slotsadmin set global announcements.broadcast-win true`
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

### Chicken Admin (chicken.admin)

- Station lifecycle:
1. /chickenadmin create [odd-size]
2. /chickenadmin remove <number>
3. /chickenadmin regen [number]
4. /chickenadmin list
5. /chickenadmin reload
- Global config:
1. /chickenadmin set <path> <value>
2. /chickenadmin set <path> (shows current value)
- Key settings: min/max lightning seconds, lightning randomness curve, multiplier growth, pickup multiplier, colour bonuses, dead-tile count, board materials, and hologram settings.
### Fights Admin (`fights.admin`)

Primary command: `/fightadmin`

- Station lifecycle:
1. `/fightadmin create [odd-size] [fighters]`
2. `/fightadmin fighters <arena-number> <2-16>`
3. `/fightadmin move <arena-number>` or `/fightadmin move <arena-number> <x> <y> <z>` (relative block offset)
4. `/fightadmin remove <arena-number>`
5. `/fightadmin regen [arena-number]`
6. `/fightadmin list`
7. `/fightadmin holo <arena-number> [reset]`
- Global Fights config:
1. `/fightadmin set <path> <value>`
2. Important paths include `fights.max-fighters`, `fights.betting-seconds`, `fights.payout-multiplier`, `fights.arena-style`, `fights.blocks.*`, `fights.casino-frame-animation.*`, and `fights.hologram.bet-display-mode`.

### Join Gift

- `/minegamesjoin true|false` enables or disables the first-join gift.
- `/minegamesjoin set <amount>` changes the welcome payout.
- Seen players are stored in `join_rewards.yml`, so each player only receives the gift once.
- The welcome message is editable under `messages.join-gift.welcome` in `config.yml`.

## Permissions

- `mine.admin` (default: op)
- `roulette.admin` (default: op)
- `slots.admin` (default: op)
- `fights.admin` (default: op)
- `chicken.admin` (default: op)
- `dice.admin` (default: op)

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
- Slots:
1. `slots.*` (including `slots.shelf-mode.*`)
- Fights:
1. `fights.*`
2. `fights.blocks.*`
3. `fights.casino-frame-animation.*`
- Chicken:
1. `chicken.*`
- Physical dice and Craps:
1. `dice.command-enabled`
2. `dice.default-amount`
3. `dice.color`
4. `dice.player-alert`
5. `dice.server-alert`
6. `dice.glow`
7. `dice.particles`
8. `dice.craps.enabled`
9. `dice.craps.min-bet` and `dice.craps.max-bet`
10. `dice.craps.color`, `dice.craps.player-alert`, `dice.craps.server-alert`, `dice.craps.glow`, and `dice.craps.particles` (`inherit` or an explicit override)
11. `dice.rate-limit.amount` and `dice.rate-limit.period` (`24h` or `day`)

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

- Chicken board activation + hologram visibility:
1. chicken.activation-distance
2. chicken.hologram-view-range
3. chicken.hologram-height
4. chicken.hologram-line-spacing

- Fights arena activation + hologram visibility:
1. `fights.activation-distance`
2. `fights.hologram-view-range`
3. `fights.hologram-height`
4. `fights.hologram-line-spacing`

## Hologram Placement Commands

These commands select a station by the same number shown by the corresponding `list` command. After running a placement command, right-click the block surface where the hologram should appear.

- MineGame: `/minegameadmin hologramsign <number>`
- Slots: `/slotsadmin hologramsign <number>`
- Roulette: `/rouletteadmin hologramsign <number>`
- Restore the original location: add `remove`, for example `/slotsadmin hologramsign remove 2`

Placements are persistent and stored in `plugins/MineGames/holograms.yml`. Holograms use fixed surface orientation and can be moved by running the assignment command again. Set `hologram.see-through-walls` to `true` or `false` to control wall visibility.

## Notes

- `set ...` commands edit the nearest station when the setting is station-local, unless you use `set global ...` to force a global default.
- `setframe/setred/...` and `casinoframe` commands edit station overrides.
- Adding `all` applies cosmetic override commands to every station of that game type.
- Mines, roulette, and slots rebaseline their saved footprints when a size change would otherwise leave old blocks behind.
- `housebalance`/`housewithdraw` are admin-only (`mine.admin` / `roulette.admin` / `dice.admin`).
- `housewithdraw` pays the withdrawn amount directly to the admin executing the command.
- MineGame board height is controlled by `board.frame-one-higher` (settable via `/minegameadmin set board.frame-one-higher <true|false>`).
- Roulette color defaults are percent-based (`48.61 / 48.61 / 2.78`) and auto-scale with board size.
- Changing Roulette selector block clears old selector blocks from the selector layer before placing new ones.
- Removing MineGame/Roulette/Slots stations restores original world blocks for stations created on current versions (snapshot-based restore).
- Roulette station creation anchors the board directly under the admin's feet (replaces floor blocks there).
- Holograms are configured with no-wrap text display behavior for more consistent spacing.
- Fights support `walls` and `fence` arena styles. Fence mode is the default, uses connected spruce fences, and places the casino frame below them.
- Fights fighters target only fighters in their own arena and are isolated from outside damage, projectiles, potions, mobs, portals, pickups, fire, lava, explosions, and despawning. They drop no items or experience.
- Chicken boards block natural mob spawns, fluid flow, explosions, and non-admin building. Hitting the lamp frame cashes out an active Chicken bet.
- All registered games block non-admin building inside their footprint and in the airspace above it. Lava/water buckets and fluid flow into games are cancelled, and protected game blocks cannot be damaged by explosions.
- Removing Fights stations restores captured original blocks; `regen` rebuilds the station from current configuration.


## Storage

- `plugins/MineGames/config.yml` (global settings)
- `plugins/MineGames/stations.yml` (MineGame stations + overrides)
- `plugins/MineGames/holograms.yml` (per-station hologram placement overrides)
- `plugins/MineGames/roulette_stations.yml` (Roulette stations + overrides)
- `plugins/MineGames/slots_stations.yml` (Slots stations + overrides)
- `plugins/MineGames/fights_stations.yml` (Fights stations)
- `plugins/MineGames/chicken_stations.yml` (Chicken boards)
- `plugins/MineGames/mines_restore.yml` (MineGame original-block snapshots for restore on station removal)
- `plugins/MineGames/roulette_restore.yml` (Roulette original-block snapshots for restore on station removal)
- `plugins/MineGames/slots_restore.yml` (Slots original-block snapshots for restore on station removal)
- `plugins/MineGames/fights_restore.yml` (Fights original-block snapshots for restore on station removal)
- `plugins/MineGames/chicken_restore.yml` (Chicken original-block snapshots for board removal)
- `plugins/MineGames/house_balances.yml` (separate MineGame/Roulette/Slots/Fights/Craps house balance + wager/payout totals)
- `plugins/MineGames/dice_rate_limits.yml` (per-player dice/craps rate-limit windows and counts)

## License

GPLv3. See [LICENSE](LICENSE).
