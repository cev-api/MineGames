package dev.minegame.mines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class CasinoGuiCommand implements CommandExecutor {
    private record Setting(String path, String label, String kind) {}
    private static final double DEFAULT_PERCENT = 25.0;
    private final MinegamePlugin plugin;
    private final MinesManager mines;
    private final RouletteManager roulette;
    private final SlotsManager slots; private final FightsManager fights;

    public CasinoGuiCommand(MinegamePlugin plugin, MinesManager mines, RouletteManager roulette, SlotsManager slots, FightsManager fights) {
        this.plugin = plugin; this.mines = mines; this.roulette = roulette; this.slots = slots; this.fights = fights;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!player.isOp()) { player.sendMessage(ChatColor.RED + "Operators only."); return true; }
        openHome(player); return true;
    }

    public void openHome(Player player) {
        Inventory inv = plugin.getServer().createInventory(new CasinoGuiHolder("home"), 27, ChatColor.DARK_AQUA + "Casino settings");
        inv.setItem(11, item(Material.DIAMOND, ChatColor.AQUA + "MineGame", "Configure global settings and stations"));
        inv.setItem(13, item(Material.RED_CONCRETE, ChatColor.RED + "Roulette", "Configure global settings and stations"));
        inv.setItem(15, item(Material.GOLD_INGOT, ChatColor.GOLD + "Slots", "Configure global settings and stations"));
        inv.setItem(17, item(Material.IRON_SWORD, ChatColor.GOLD + "Fights", "Configure arenas, combat, blocks, and betting"));
        player.openInventory(inv);
    }

    public void openGame(Player player, String game) { openGame(player, game, 0); }

    public void openGame(Player player, String game, int page) {
        List<Setting> settings = settings(game, false);
        int maxPage = Math.max(0, (settings.size() - 1) / 36);
        page = Math.max(0, Math.min(page, maxPage));
        Inventory inv = plugin.getServer().createInventory(new CasinoGuiHolder(game, null, page), 54,
                ChatColor.DARK_AQUA + "Casino / " + title(game) + " (" + (page + 1) + "/" + (maxPage + 1) + ")");
        inv.setItem(4, item(Material.NETHER_STAR, ChatColor.WHITE + title(game) + " global settings",
                "Click a setting to cycle it.", "Global changes override station overrides."));
        int start = page * 36;
        for (int i = 0; i < 36 && start + i < settings.size(); i++) {
            Setting setting = settings.get(start + i);
            inv.setItem(9 + (i % 9) + (i / 9) * 9, settingItem(setting, globalValue(game, setting.path())));
        }
        inv.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Previous settings", "Page " + Math.max(1, page) + " of " + (maxPage + 1)));
        inv.setItem(53, item(Material.ARROW, ChatColor.YELLOW + "Next settings", "Page " + Math.min(maxPage + 1, page + 2) + " of " + (maxPage + 1)));
        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Back", "Return to game selection"));
        inv.setItem(50, item(Material.CHEST, ChatColor.AQUA + "Stations", "Browse and edit individual stations"));
        player.openInventory(inv);
    }

    public void openStations(Player player, String game) {
        Inventory inv = plugin.getServer().createInventory(new CasinoGuiHolder("stations:" + game), 54,
                ChatColor.DARK_AQUA + title(game) + " / Stations");
        inv.setItem(4, item(Material.CHEST, ChatColor.AQUA + title(game) + " stations",
                "Choose a station to edit its local overrides."));
        int slot = 10;
        if (game.equals("minegame")) {
            for (StationData s : mines.stations()) {
                if (slot >= 44) break;
                inv.setItem(slot++, item(Material.BEACON, ChatColor.AQUA + "Mine station #" + number("minegame", s.key()),
                        s.worldName() + "  " + s.x() + ", " + s.y() + ", " + s.z(), "Click to edit local overrides"));
            }
        } else if (game.equals("roulette")) {
            for (RouletteStationData s : roulette.stations()) {
                if (slot >= 44) break;
                inv.setItem(slot++, item(Material.RED_CONCRETE, ChatColor.RED + "Roulette station #" + number("roulette", s.key()),
                        s.worldName() + "  " + s.x() + ", " + s.y() + ", " + s.z(), "Click to edit local overrides"));
            }
        } else {
            for (SlotStationData s : slots.stations()) {
                if (slot >= 44) break;
                inv.setItem(slot++, item(Material.IRON_BLOCK, ChatColor.GOLD + "Slots station #" + number("slots", s.key()),
                        s.worldName() + "  " + s.x() + ", " + s.y() + ", " + s.z(), "Click to edit local overrides"));
            }
        }
        inv.setItem(45, item(Material.ARROW, ChatColor.DARK_GRAY + "Station list", "Use the center controls below"));
        inv.setItem(53, item(Material.ARROW, ChatColor.DARK_GRAY + "More stations", "Station paging is not available yet"));
        inv.setItem(49, item(Material.BARRIER, ChatColor.RED + "Back", "Return to global settings"));
        inv.setItem(50, item(Material.NETHER_STAR, ChatColor.WHITE + "Global settings", "Return to this game's global settings"));
        player.openInventory(inv);
    }

    public void openStationBySlot(Player player, String game, int slot) {
        int index = slot - 10;
        if (index < 0) return;
        if (game.equals("minegame")) {
            List<StationData> list = new ArrayList<>(mines.stations());
            if (index < list.size()) openStation(player, game, list.get(index).key());
        } else if (game.equals("roulette")) {
            List<RouletteStationData> list = new ArrayList<>(roulette.stations());
            if (index < list.size()) openStation(player, game, list.get(index).key());
        } else {
            List<SlotStationData> list = new ArrayList<>(slots.stations());
            if (index < list.size()) openStation(player, game, list.get(index).key());
        }
    }


    public void openStation(Player p,String game,String key){List<Setting> ss=settings(game,true);Inventory inv=plugin.getServer().createInventory(new CasinoGuiHolder(game,key),54,ChatColor.DARK_AQUA+title(game)+" / station");inv.setItem(4,item(Material.COMPASS,ChatColor.WHITE+"Station settings","Click a setting to cycle it.","These overrides affect this station only."));for(int i=0;i<Math.min(36,ss.size());i++){Setting s=ss.get(i);inv.setItem(9+i%9+i/9*9,settingItem(s,stationValue(game,key,s.path())));}inv.setItem(45,item(Material.ARROW,ChatColor.DARK_GRAY+"Station list","Use the center controls below"));inv.setItem(53,item(Material.ARROW,ChatColor.DARK_GRAY+"More stations","Station paging is not available yet"));inv.setItem(49,item(Material.BARRIER,ChatColor.RED+"Back","Return to station list"));inv.setItem(50,item(Material.NETHER_STAR,ChatColor.WHITE+"Global settings","Return to this game's global settings"));p.openInventory(inv);}
    public void handleSettingClick(Player p,CasinoGuiHolder h,int raw,boolean inc){if(raw<9||raw>=45)return;int row=raw/9,col=raw%9;if(row<1||row>4)return;int i=(row-1)*9+col;List<Setting> ss=settings(h.game(),h.stationKey()!=null);if(i>=ss.size())return;Setting s=ss.get(i);Object old=h.stationKey()==null?globalValue(h.game(),s.path()):stationValue(h.game(),h.stationKey(),s.path());String next=nextValue(s,old,inc);if(h.stationKey()==null)setGlobal(p,h.game(),s.path(),next);else setStation(p,h.game(),h.stationKey(),s.path(),next);if(h.stationKey()==null)openGame(p,h.game(),h.page());else openStation(p,h.game(),h.stationKey());}
    private List<Setting> settings(String game,boolean local){List<Setting> a=new ArrayList<>();if(game.equals("minegame")){addPaths(a,"int","Grid size","minegame.board.grid-size","Wall distance","minegame.board.wall-distance","Reset delay","minegame.board.reset-delay-seconds","Game duration","minegame.game.duration-seconds","Firework count","minegame.effects.firework-count");addPaths(a,"bool","Announce wins","minegame.announcements.broadcast-win","Announce losses","minegame.announcements.broadcast-loss","Frame one higher","minegame.board.frame-one-higher","Win fireworks","minegame.effects.fireworks-on-win","Hologram enabled","minegame.hologram.enabled","Affix hologram","minegame.hologram.affix-to-wall");addPaths(a,"material","Station block","minegame.board.station-block","Hidden block","minegame.board.hidden-block","Safe reveal block","minegame.board.safe-reveal-block","Mine reveal block","minegame.board.mine-reveal-block","Frame block","minegame.board.frame-block");addPaths(a,"double","House edge","minegame.game.house-edge-percent","Max multiplier","minegame.game.max-multiplier","Hologram spacing","minegame.hologram.line-spacing","Hologram range","minegame.hologram.view-range");addPaths(a,"money","Minimum bet","minegame.game.min-bet","Maximum bet","minegame.game.max-bet","Max payout","minegame.game.max-payout");}else if(game.equals("roulette")){addPaths(a,"int","Board size","roulette.board-size","Betting seconds","roulette.betting-seconds","Spin seconds","roulette.spin-seconds","Result seconds","roulette.result-seconds");addPaths(a,"percent","Red percent","roulette.red-percent","Black percent","roulette.black-percent","Green percent","roulette.green-percent","House edge","roulette.house-edge-percent");addPaths(a,"money","Minimum bet","roulette.min-bet","Maximum bet","roulette.max-bet","Max payout","roulette.max-payout");addPaths(a,"double","Red multiplier","roulette.red-multiplier","Black multiplier","roulette.black-multiplier","Green multiplier","roulette.green-multiplier","Hologram range","roulette.hologram-view-range");addPaths(a,"bool","Announce wins","roulette.announcements.broadcast-win","Announce losses","roulette.announcements.broadcast-loss","Frame animation","roulette.frame-animation.enabled","Broadcast winner","roulette.broadcast-top-winner","Hologram enabled","roulette.hologram.enabled");addPaths(a,"material","Frame block","roulette.blocks.frame","Red block","roulette.blocks.red","Black block","roulette.blocks.black","Green block","roulette.blocks.green","Selector block","roulette.blocks.selector");addPaths(a,"mode","Animation mode","roulette.frame-animation.mode");}else if(game.equals("fights")){addPaths(a,"int","Betting seconds","fights.betting-seconds","Celebration seconds","fights.celebration-seconds","Max fighters","fights.max-fighters","Fireworks per winner","fights.fireworks-per-winner","Casino animation pattern","fights.casino-frame-animation.pattern");addPaths(a,"money","Minimum bet","fights.min-bet","Maximum bet","fights.max-bet","Maximum payout","fights.max-payout");addPaths(a,"double","Payout multiplier","fights.payout-multiplier","Activation distance","fights.activation-distance","Hologram height","fights.hologram-height","Hologram spacing","fights.hologram-line-spacing","Hologram range","fights.hologram-view-range");addPaths(a,"mode","Arena style","fights.arena-style","Bet display position","fights.hologram.bet-display-mode","Casino animation mode","fights.casino-frame-animation.mode");addPaths(a,"bool","Casino frame animation","fights.casino-frame-animation.enabled","Bettor name on fighter","fights.show-bettor-name-on-fighter","Winner bettor corner fireworks","fights.fireworks-for-winning-bettors","Announce wins","fights.announcements.broadcast-win","Announce losses","fights.announcements.broadcast-loss","Hologram enabled","fights.hologram.enabled");addPaths(a,"material","Floor block","fights.blocks.fill-block","Wall block","fights.blocks.frame-block","Fence block","fights.blocks.fence-block","Casino frame block","fights.blocks.casino-frame-block","Animation block","fights.casino-frame-animation.block");}else{addPaths(a,"int","Reels / width","slots.reel-count","Rows / height","slots.row-count","Spin seconds","slots.spin-seconds","Stop interval","slots.stop-interval-ticks","Result seconds","slots.result-seconds","Win fireworks","slots.fireworks-per-win");addPaths(a,"money","Default spin bet","slots.cost-per-spin","Minimum bet","slots.min-bet","Maximum bet","slots.max-bet","Maximum payout","slots.max-payout");addPaths(a,"double","Activation distance","slots.activation-distance-from-frame","Hologram height","slots.hologram-height","Hologram spacing","slots.hologram-line-spacing","Hologram range","slots.hologram-view-range");addPaths(a,"mode","Lever placement","slots.lever-placement","Spin light mode","slots.spin-light-mode","Animation mode","slots.frame-animation.mode");addPaths(a,"bool","Announce wins","slots.announcements.broadcast-win","Announce losses","slots.announcements.broadcast-loss","Frame animation","slots.frame-animation.enabled","Bet buttons","slots.bet-buttons.enabled","Hologram enabled","slots.hologram.enabled");addPaths(a,"percent","Bet adjustment","slots.bet-buttons.adjust-percent");addPaths(a,"material","Outer frame","slots.blocks.outer-frame","Inner frame","slots.blocks.inner-frame","Winning block","slots.blocks.winning","Button material","slots.bet-buttons.material");for(int i=1;i<=16;i++)add(a,"slots.payout-multipliers."+i,"Payout "+i+" matches","double");}if(!local)return a;return a.stream().filter(s->stationPaths(game).contains(s.path())).toList();}
    private void addPaths(List<Setting> a,String kind,String... x){for(int i=0;i+1<x.length;i+=2)add(a,x[i+1],x[i],kind);}
    private List<String> stationPaths(String g){if(g.equals("minegame"))return List.of("minegame.board.grid-size");if(g.equals("fights"))return List.of("fights.fighter-count");if(g.equals("roulette"))return List.of("roulette.board-size","roulette.blocks.frame","roulette.blocks.red","roulette.blocks.black","roulette.blocks.green","roulette.blocks.selector","roulette.frame-animation.enabled","roulette.frame-animation.pattern","roulette.frame-animation.mode");return List.of("slots.reel-count","slots.row-count","slots.cost-per-spin","slots.blocks.outer-frame","slots.blocks.inner-frame","slots.blocks.winning","slots.frame-animation.enabled","slots.frame-animation.pattern","slots.frame-animation.mode","slots.bet-buttons.enabled","slots.bet-buttons.material","slots.bet-buttons.adjust-percent");}
    private Object stationValue(String g,String k,String path){if(g.equals("fights"))for(FightStationData s:fights.stations())if(s.key().equals(k)&&path.equals("fights.fighter-count"))return s.fighterCount();if(g.equals("minegame"))for(StationData s:mines.stations())if(s.key().equals(k)&&path.equals("minegame.board.grid-size")&&s.boardSize()!=null)return s.boardSize();if(g.equals("roulette"))for(RouletteStationData s:roulette.stations())if(s.key().equals(k)){Object value=switch(path){case "roulette.board-size"->s.boardSize();case "roulette.blocks.frame"->s.boardFrameBlock();case "roulette.blocks.red"->s.boardRedBlock();case "roulette.blocks.black"->s.boardBlackBlock();case "roulette.blocks.green"->s.boardGreenBlock();case "roulette.blocks.selector"->s.boardSelectorBlock();case "roulette.frame-animation.enabled"->s.frameAnimEnabled();case "roulette.frame-animation.pattern"->s.frameAnimPattern();case "roulette.frame-animation.mode"->s.frameAnimMode();default->null;};return value!=null?value:globalValue(g,path);}if(g.equals("slots"))for(SlotStationData s:slots.stations())if(s.key().equals(k)){Object value=switch(path){case "slots.reel-count"->s.reelCount();case "slots.row-count"->s.rowCount();case "slots.cost-per-spin"->s.costPerSpin();case "slots.blocks.outer-frame"->s.outerFrameBlock();case "slots.blocks.inner-frame"->s.innerFrameBlock();case "slots.blocks.winning"->s.winningBlock();case "slots.frame-animation.enabled"->s.frameAnimEnabled();case "slots.frame-animation.pattern"->s.frameAnimPattern();case "slots.frame-animation.mode"->s.frameAnimMode();case "slots.bet-buttons.enabled"->s.betButtonsEnabled();case "slots.bet-buttons.material"->s.betButtonMaterial();case "slots.bet-buttons.adjust-percent"->s.betAdjustPercent();default->null;};return value!=null?value:globalValue(g,path);}return globalValue(g,path);}    private Object globalValue(String g,String path){if(g.equals("minegame"))return mines.getCurrentConfigValue(path);if(g.equals("roulette"))return roulette.getCurrentConfigValue(path);if(g.equals("fights"))return fights.getCurrentConfigValue(path);return slots.getCurrentConfigValue(path);}
    private boolean setGlobal(Player p,String g,String path,String v){if(g.equals("minegame"))mines.setConfigValue(p,path,v,true);else if(g.equals("roulette"))roulette.setConfigValue(p,path,v,true);else if(g.equals("fights"))fights.setConfigValue(p,path,v);else slots.setConfigValue(p,path,v,true);return true;}
    private boolean setStation(Player p,String g,String k,String path,String v){if(g.equals("minegame"))return mines.setStationConfigValue(k,path,v);if(g.equals("roulette"))return roulette.setStationConfigValue(k,path,v);if(g.equals("fights"))return fights.setStationConfigValue(p,k,path,v);return slots.setStationConfigValue(k,path,v);}
    private String nextValue(Setting s,Object old,boolean inc){if(s.kind().equals("bool"))return String.valueOf(!(old instanceof Boolean b&&b));if(s.kind().equals("material")){Material c=Material.matchMaterial(String.valueOf(old));Material[] v=Arrays.stream(Material.values()).filter(Material::isBlock).toArray(Material[]::new);int i=0;if(c!=null)for(int j=0;j<v.length;j++)if(v[j]==c){i=j;break;}return v[Math.floorMod(i+(inc?1:-1),v.length)].name();}if(s.kind().equals("mode")){String[] v=s.path().equals("fights.arena-style")?new String[]{"walls","fence"}:s.path().contains("casino-frame-animation.mode")?new String[]{"betting_only","always"}:s.path().contains("lever-placement")?new String[]{"front_right_middle","front_middle","right_middle","middle"}:s.path().contains("spin-light")?new String[]{"flashing_fast","flashing_slow","cycle_slow"}:new String[]{"idle_only","always"};int i=Math.max(0,Arrays.asList(v).indexOf(String.valueOf(old)));return v[Math.floorMod(i+(inc?1:-1),v.length)];}double n=old instanceof Number x?x.doubleValue():1,step=s.kind().equals("money")?25:s.kind().equals("percent")?1:s.path().contains("seconds")?5:1,next=n+(inc?step:-step);if(s.path().contains("max-payout")||s.path().contains("max-bet")){if(next<0)next=-1;}else if(next<0)next=0;return s.kind().equals("int")?String.valueOf(Math.max(1,Math.round(next))):String.valueOf(next);}
    private void add(List<Setting> a,String p,String n,String k){a.add(new Setting(p,n,k));}private ItemStack settingItem(Setting s,Object v){return item(Material.PAPER,ChatColor.YELLOW+s.label(),"Value: "+v,"Left/right click to change");}private String title(String g){return g.substring(0,1).toUpperCase(Locale.ROOT)+g.substring(1);}private int number(String t,String k){return plugin.stationNumberStorage().number(t,k);}private ItemStack item(Material m,String n,String... l){ItemStack x=new ItemStack(m);ItemMeta q=x.getItemMeta();q.setDisplayName(n);q.setLore(Arrays.asList(l));x.setItemMeta(q);return x;}
}
