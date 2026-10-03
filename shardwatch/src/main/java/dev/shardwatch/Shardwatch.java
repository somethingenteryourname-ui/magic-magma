package dev.shardwatch;

import dev.shardwatch.api.ShardwatchActionEvent;
import dev.shardwatch.api.StaffAction;
import dev.shardwatch.command.EchoCommands;
import dev.shardwatch.command.FacetCommands;
import dev.shardwatch.command.FlareCommands;
import dev.shardwatch.command.GlintCommand;
import dev.shardwatch.command.LedgerCommand;
import dev.shardwatch.command.ScopeCommand;
import dev.shardwatch.command.SwCommand;
import dev.shardwatch.command.VerdictCommands;
import dev.shardwatch.config.Lang;
import dev.shardwatch.echo.EchoRecorder;
import dev.shardwatch.echo.RewindEngine;
import dev.shardwatch.facet.FacetService;
import dev.shardwatch.flare.FlareService;
import dev.shardwatch.fx.Animations;
import dev.shardwatch.fx.Fx;
import dev.shardwatch.glint.GlintTracker;
import dev.shardwatch.gui.ChatPrompt;
import dev.shardwatch.gui.Icons;
import dev.shardwatch.gui.MenuListener;
import dev.shardwatch.gui.MenuService;
import dev.shardwatch.profile.ProfileService;
import dev.shardwatch.progress.AuraTask;
import dev.shardwatch.progress.LustreService;
import dev.shardwatch.progress.SigilService;
import dev.shardwatch.scope.ScopeViewer;
import dev.shardwatch.storage.Database;
import dev.shardwatch.storage.LogStore;
import dev.shardwatch.tool.ToolListener;
import dev.shardwatch.tool.ToolService;
import dev.shardwatch.util.Durations;
import dev.shardwatch.verdict.VerdictListener;
import dev.shardwatch.verdict.VerdictService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/** Shardwatch — crystal staff suite. */
public final class Shardwatch extends JavaPlugin {

    private Lang lang;
    private Database db;
    private LogStore logs;
    private Fx fx;
    private Animations animations;
    private ProfileService profiles;
    private VerdictService verdicts;
    private FlareService flares;
    private EchoRecorder echoes;
    private RewindEngine rewind;
    private GlintTracker glint;
    private FacetService facets;
    private ScopeViewer scope;
    private ToolService tools;
    private ToolListener toolListener;
    private Icons icons;
    private MenuService menus;
    private ChatPrompt prompts;
    private LustreService lustre;
    private SigilService sigils;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lang = new Lang(this);
        db = new Database(getDataFolder(), getLogger());
        try {
            db.open(getConfig().getString("storage.file", "shardwatch.db"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not open the database; disabling Shardwatch.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        logs = new LogStore(db);
        fx = new Fx(this);
        animations = new Animations(this);
        profiles = new ProfileService(this);
        facets = new FacetService(this);
        verdicts = new VerdictService(this);
        flares = new FlareService(this);
        echoes = new EchoRecorder(this);
        rewind = new RewindEngine(this);
        glint = new GlintTracker(this);
        scope = new ScopeViewer(this);
        tools = new ToolService(this);
        toolListener = new ToolListener(this);
        icons = new Icons(this);
        menus = createMenus();
        prompts = new ChatPrompt(this);
        lustre = new LustreService(this);
        sigils = new SigilService(this);

        // Prompts first: a typed answer must be swallowed before any other chat handling.
        listen(prompts, profiles, new VerdictListener(this), facets, echoes, glint, toolListener, new MenuListener(), lustre);
        Bukkit.getScheduler().runTaskTimer(this, new AuraTask(this), 40L, 3L);

        VerdictCommands verdictCommands = new VerdictCommands(this);
        for (String c : new String[]{"chip", "hush", "unhush", "eject", "encase", "unencase", "petrify"}) {
            command(c, verdictCommands);
        }
        FlareCommands flareCommands = new FlareCommands(this);
        command("flare", flareCommands);
        command("flares", flareCommands);
        command("ledger", new LedgerCommand(this));
        EchoCommands echoCommands = new EchoCommands(this);
        command("echo", echoCommands);
        command("rewind", echoCommands);
        command("glint", new GlintCommand(this));
        FacetCommands facetCommands = new FacetCommands(this);
        command("facet", facetCommands);
        command("hum", facetCommands);
        command("veil", facetCommands);
        command("scope", new ScopeCommand(this));
        command("shardwatch", new SwCommand(this));

        reload();
        // Players already online after a /reload: their profiles were never loaded at login.
        for (Player p : Bukkit.getOnlinePlayers()) {
            profiles.load(p.getUniqueId()).thenAccept(profile -> sync(() -> {
                if (profile != null) {
                    dev.shardwatch.profile.Profile live = profiles.get(p);
                    live.facet(profile.facet());
                    live.veiled(profile.veiled());
                    live.alertsOn(profile.alertsOn());
                    live.fxOn(profile.fxOn());
                    live.lustre(profile.lustre());
                    live.lifetime(profile.lifetime());
                    live.refinements().putAll(profile.refinements());
                    live.keepsakes().addAll(profile.keepsakes());
                    live.activeAura(profile.activeAura());
                    live.activeSigil(profile.activeSigil());
                    live.daily(profile.dailyDay(), profile.dailyEarned());
                }
                facets.apply(p);
            }));
        }
        Bukkit.getScheduler().runTaskTimer(this, this::prune, 20L * 60, 20L * 60 * 60 * 6);
        getLogger().info("Shardwatch is watching.");
    }

    @Override
    public void onDisable() {
        if (animations != null) {
            animations.shutdown();
        }
        if (echoes != null) {
            echoes.flush();
        }
        if (profiles != null) {
            profiles.saveAll();
        }
        if (db != null) {
            db.close();
        }
    }

    /** Re-reads config.yml and lang.yml and refreshes every service that caches settings. */
    public void reload() {
        reloadConfig();
        lang.reload();
        fx.reload();
        facets.reload();
        flares.reload();
        echoes.reload();
        glint.reload();
    }

    private void prune() {
        long now = System.currentTimeMillis();
        logs.prune(cutoff(now, "storage.retention.echoes"), cutoff(now, "storage.retention.chat"),
                cutoff(now, "storage.retention.audit"));
    }

    private long cutoff(long now, String path) {
        Long d = Durations.parse(getConfig().getString(path, "perm"));
        return d == null || d == 0 ? 0 : now - d;
    }

    private void listen(Listener... listeners) {
        for (Listener l : listeners) {
            Bukkit.getPluginManager().registerEvents(l, this);
        }
    }

    private void command(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    /** Runs on the main thread (immediately if already there). */
    public void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else if (isEnabled()) {
            Bukkit.getScheduler().runTask(this, r);
        }
    }

    /** Records a staff action in the audit log and fires {@link ShardwatchActionEvent}. Main thread only. */
    public void action(CommandSender actor, StaffAction action, String target, String detail) {
        CommandSender who = actor == null ? Bukkit.getConsoleSender() : actor;
        logs.audit(who, action.name(), (target == null || target.isEmpty() ? "" : target + " | ") + detail);
        Bukkit.getPluginManager().callEvent(new ShardwatchActionEvent(actor instanceof Player p ? p : null, action,
                target == null ? "" : target, detail));
    }

    public Lang lang() {
        return lang;
    }

    public Database db() {
        return db;
    }

    public LogStore logs() {
        return logs;
    }

    public Fx fx() {
        return fx;
    }

    public Animations animations() {
        return animations;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public VerdictService verdicts() {
        return verdicts;
    }

    public FlareService flares() {
        return flares;
    }

    public EchoRecorder echoes() {
        return echoes;
    }

    public RewindEngine rewind() {
        return rewind;
    }

    public GlintTracker glint() {
        return glint;
    }

    public FacetService facets() {
        return facets;
    }

    public ScopeViewer scope() {
        return scope;
    }

    public ToolService tools() {
        return tools;
    }

    public ToolListener toolListener() {
        return toolListener;
    }

    /** The menu service; later stages extend it with more menus. */
    private MenuService createMenus() {
        return new MenuService(this);
    }

    public Icons icons() {
        return icons;
    }

    public MenuService menus() {
        return menus;
    }

    public ChatPrompt prompts() {
        return prompts;
    }

    public LustreService lustre() {
        return lustre;
    }

    public SigilService sigils() {
        return sigils;
    }
}
