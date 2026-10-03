package dev.shardwatch.scope;

import dev.shardwatch.Shardwatch;
import dev.shardwatch.echo.Echo;
import dev.shardwatch.echo.EchoQuery;
import dev.shardwatch.flare.Flare;
import dev.shardwatch.glint.GlintAlert;
import dev.shardwatch.storage.LogStore;
import dev.shardwatch.util.Durations;
import dev.shardwatch.util.Text;
import dev.shardwatch.verdict.Verdict;
import dev.shardwatch.verdict.VerdictService;
import dev.shardwatch.verdict.VerdictType;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Shardscope: a "web page" log viewer. Each tab is rendered as a written book with a clickable navbar, table rows
 * with hover cards, and a pager; or, with {@code --chat}, as a wide table in chat.
 */
public final class ScopeViewer {

    public enum Tab {
        OVERVIEW, BLOCKS, CHAT, VERDICTS, FLARES, GLINT, STAFF;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Tab parse(String s) {
            for (Tab t : values()) {
                if (t.id().startsWith(s.toLowerCase(Locale.ROOT))) {
                    return t;
                }
            }
            return null;
        }
    }

    /** One table row: two short lines for the book, one line for chat, a hover card and an optional click. */
    private record Entry(Component line1, Component line2, Component hover, ClickEvent click) {
    }

    /** Parsed filter: {@code u:name r:radius t:2h a:action} plus free text. */
    public record Filter(String user, int radius, long since, String action, String text, String raw) {

        public static Filter parse(String[] tokens) {
            String user = null;
            String action = null;
            int radius = -1;
            long since = 0;
            List<String> free = new ArrayList<>();
            for (String t : tokens) {
                String low = t.toLowerCase(Locale.ROOT);
                if (low.startsWith("u:")) {
                    user = t.substring(2);
                } else if (low.startsWith("r:")) {
                    try {
                        radius = Integer.parseInt(t.substring(2));
                    } catch (NumberFormatException ignored) {
                    }
                } else if (low.startsWith("t:")) {
                    Long d = Durations.parse(t.substring(2));
                    if (d != null && d > 0) {
                        since = System.currentTimeMillis() - d;
                    }
                } else if (low.startsWith("a:")) {
                    action = t.substring(2);
                } else if (!t.isBlank()) {
                    free.add(t);
                }
            }
            return new Filter(user, radius, since, action, free.isEmpty() ? null : String.join(" ", free),
                    String.join(" ", tokens));
        }
    }

    private final Shardwatch plugin;

    public ScopeViewer(Shardwatch plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, Tab tab, int window, Filter filter, boolean chat) {
        int perWindow = chat ? plugin.getConfig().getInt("scope.chat-rows", 12)
                : plugin.getConfig().getInt("scope.rows-per-window", 60);
        int offset = Math.max(0, window - 1) * perWindow;
        CompletableFuture<List<Entry>> rows = switch (tab) {
            case OVERVIEW -> CompletableFuture.completedFuture(List.of());
            case BLOCKS -> blocks(viewer, filter, perWindow, offset);
            case CHAT -> plugin.logs().chats(filter.user(), filter.text(), filter.since(), perWindow, offset)
                    .thenApply(l -> l.stream().map(this::chatEntry).toList());
            case VERDICTS -> plugin.verdicts().store().recent(filter.user() != null ? filter.user() : filter.text(),
                    filter.since(), perWindow, offset).thenApply(l -> l.stream().map(this::verdictEntry).toList());
            case FLARES -> plugin.flares().store().list(null, filter.user() != null ? filter.user() : filter.text(),
                    perWindow, offset).thenApply(l -> l.stream().map(this::flareEntry).toList());
            case GLINT -> plugin.glint().store().recent(filter.user() != null ? filter.user() : filter.text(),
                    filter.since(), perWindow, offset).thenApply(l -> l.stream().map(this::glintEntry).toList());
            case STAFF -> plugin.logs().audits(filter.user() != null ? filter.user() : filter.text(), filter.since(),
                    perWindow, offset).thenApply(l -> l.stream().map(this::auditEntry).toList());
        };
        CompletableFuture<List<Component>> overview = tab == Tab.OVERVIEW ? overview() : CompletableFuture.completedFuture(List.of());
        rows.thenCombine(overview, (r, o) -> new Object[]{r, o}).whenComplete((res, err) -> plugin.sync(() -> {
            if (err != null) {
                plugin.lang().send(viewer, "general.db-error");
                return;
            }
            @SuppressWarnings("unchecked") List<Entry> entries = (List<Entry>) res[0];
            @SuppressWarnings("unchecked") List<Component> stats = (List<Component>) res[1];
            boolean more = entries.size() >= perWindow;
            if (chat) {
                renderChat(viewer, tab, window, filter, entries, stats, more);
            } else {
                viewer.openBook(renderBook(tab, window, filter, entries, stats, more));
            }
            plugin.fx().playFor(viewer, "scope-open");
        }));
    }

    private CompletableFuture<List<Entry>> blocks(Player viewer, Filter f, int limit, int offset) {
        Echo.Action action = f.action() == null ? null : Echo.Action.parse(f.action());
        EchoQuery q = new EchoQuery(f.user(), action, f.radius() >= 0 ? viewer.getWorld().getName() : null,
                viewer.getLocation().getBlockX(), viewer.getLocation().getBlockY(), viewer.getLocation().getBlockZ(),
                f.radius(), f.since(), f.text(), null);
        plugin.echoes().flush();
        return plugin.echoes().store().search(q, limit, offset).thenApply(l -> l.stream().map(this::echoEntry).toList());
    }

    private CompletableFuture<List<Component>> overview() {
        long day = System.currentTimeMillis() - 86_400_000L;
        CompletableFuture<Integer> flares = plugin.flares().store().countOpen();
        CompletableFuture<Integer> encased = plugin.verdicts().store().countActive(VerdictType.ENCASE);
        CompletableFuture<Integer> hushed = plugin.verdicts().store().countActive(VerdictType.HUSH);
        CompletableFuture<Integer> echoes = plugin.logs().countSince("echoes", day);
        CompletableFuture<Integer> chats = plugin.logs().countSince("chat_log", day);
        CompletableFuture<Integer> glints = plugin.logs().countSince("glint", day);
        CompletableFuture<Integer> audits = plugin.logs().countSince("audit", day);
        return CompletableFuture.allOf(flares, encased, hushed, echoes, chats, glints, audits).thenApply(v -> {
            int staffOnline = (int) Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("shardwatch.staff")).count();
            TagResolver r = TagResolver.resolver(Text.p("flares", flares.join()), Text.p("encased", encased.join()),
                    Text.p("hushed", hushed.join()), Text.p("echoes", echoes.join()), Text.p("chats", chats.join()),
                    Text.p("glints", glints.join()), Text.p("audits", audits.join()), Text.p("staff", staffOnline),
                    Text.p("petrified", plugin.verdicts().petrifiedPlayers().size()),
                    Text.p("online", Bukkit.getOnlinePlayers().size()));
            return plugin.lang().getLines("scope.overview", r);
        });
    }

    // ------------------------------------------------------------------ entries

    /** Short forms that fit a 19-character book line. */
    private static TagResolver brief(long time, String name) {
        return TagResolver.resolver(Text.p("ago_s", Durations.format(System.currentTimeMillis() - time, 1)),
                Text.p("name_s", Text.shorten(name, 12)));
    }

    private Entry echoEntry(Echo e) {
        TagResolver r = plugin.echoes().resolvers(e);
        return new Entry(plugin.lang().get("scope.blocks.line1", r), plugin.lang().get("scope.blocks.line2." + e.action().name().toLowerCase(Locale.ROOT), r),
                plugin.lang().get("scope.blocks.hover", r),
                ClickEvent.runCommand("/scope tp " + e.world() + " " + e.x() + " " + e.y() + " " + e.z()));
    }

    private Entry chatEntry(LogStore.ChatRow c) {
        TagResolver r = TagResolver.resolver(Text.p("ago", Durations.ago(c.time())), Text.p("player", c.name()),
                Text.p("kind", c.kind().toLowerCase(Locale.ROOT)), Text.p("message", c.message()),
                Text.p("short", Text.shorten(c.message(), 17)), Text.p("date", VerdictService.formatDate(c.time())),
                brief(c.time(), c.name()));
        return new Entry(plugin.lang().get("scope.chat.line1", r), plugin.lang().get("scope.chat.line2", r),
                plugin.lang().get("scope.chat.hover", r), ClickEvent.runCommand("/scope chat 1 u:" + c.name()));
    }

    private Entry verdictEntry(Verdict v) {
        TagResolver r = plugin.verdicts().resolvers(v);
        r = TagResolver.resolver(r, Text.p("ago", Durations.ago(v.created())), Text.p("short", Text.shorten(v.reason(), 17)),
                brief(v.created(), v.targetName()));
        return new Entry(plugin.lang().get("scope.verdicts.line1", r), plugin.lang().get("scope.verdicts.line2", r),
                plugin.lang().get("scope.verdicts.hover", r), ClickEvent.runCommand("/ledger " + v.targetName()));
    }

    private Entry flareEntry(Flare f) {
        TagResolver r = TagResolver.resolver(plugin.flares().resolvers(f), Text.p("short", Text.shorten(f.reason(), 17)),
                brief(f.created(), f.targetName()));
        return new Entry(plugin.lang().get("scope.flares.line1", r), plugin.lang().get("scope.flares.line2", r),
                plugin.lang().get("scope.flares.hover", r), ClickEvent.runCommand("/flares view " + f.id()));
    }

    private Entry glintEntry(GlintAlert g) {
        TagResolver r = TagResolver.resolver(Text.p("ago", Durations.ago(g.time())), Text.p("player", g.name()),
                Text.p("ore", g.ore()), Text.p("score", String.format("%.0f", g.score())), Text.p("world", g.world()),
                Text.p("x", g.x()), Text.p("y", g.y()), Text.p("z", g.z()),
                Text.p("handled", g.handledBy() == null ? "-" : g.handledBy()), brief(g.time(), g.name()));
        return new Entry(plugin.lang().get("scope.glint.line1", r), plugin.lang().get("scope.glint.line2", r),
                plugin.lang().get("scope.glint.hover", r),
                ClickEvent.runCommand("/scope tp " + g.world() + " " + g.x() + " " + g.y() + " " + g.z()));
    }

    private Entry auditEntry(LogStore.AuditRow a) {
        TagResolver r = TagResolver.resolver(Text.p("ago", Durations.ago(a.time())), Text.p("actor", a.actor()),
                Text.p("action", a.action().toLowerCase(Locale.ROOT).replace('_', ' ')), Text.p("detail", a.detail()),
                Text.p("short", Text.shorten(a.detail(), 17)), Text.p("date", VerdictService.formatDate(a.time())),
                brief(a.time(), a.actor()));
        return new Entry(plugin.lang().get("scope.staff.line1", r), plugin.lang().get("scope.staff.line2", r),
                plugin.lang().get("scope.staff.hover", r), null);
    }

    // ------------------------------------------------------------------ book renderer

    private Component navbar(Tab active, boolean chat) {
        TextComponent.Builder b = Component.text();
        for (Tab t : Tab.values()) {
            String key = "scope.tabs." + t.id();
            String label = plugin.lang().raw(key + (chat ? ".name" : ".icon"));
            String style = t == active ? plugin.lang().raw(chat ? "scope.tab-active-chat" : "scope.tab-active")
                    : plugin.lang().raw(chat ? "scope.tab-idle-chat" : "scope.tab-idle");
            b.append(plugin.lang().parse(style, Text.pp("label", label))
                    .hoverEvent(HoverEvent.showText(plugin.lang().get(key + ".hover")))
                    .clickEvent(ClickEvent.runCommand("/scope " + t.id() + (chat ? " 1 --chat" : ""))));
            b.append(Component.text(chat ? "  " : " "));
        }
        return b.build();
    }

    private Book renderBook(Tab tab, int window, Filter filter, List<Entry> entries, List<Component> stats, boolean more) {
        int perPage = Math.max(1, plugin.getConfig().getInt("scope.rows-per-page", 5));
        List<Component> pages = new ArrayList<>();
        TagResolver tr = TagResolver.resolver(Text.pp("tab", plugin.lang().raw("scope.tabs." + tab.id() + ".name")),
                Text.p("filter", filter.raw().isBlank() ? "-" : filter.raw()), Text.p("window", window));
        if (tab == Tab.OVERVIEW) {
            TextComponent.Builder page = Component.text().append(navbar(tab, false)).appendNewline()
                    .append(plugin.lang().get("scope.book-rule")).appendNewline()
                    .append(plugin.lang().get("scope.book-title", tr, Text.p("page", 1), Text.p("pages", 1))).appendNewline();
            for (Component line : stats) {
                page.append(line).appendNewline();
            }
            pages.add(page.build());
        } else {
            int total = Math.max(1, (entries.size() + perPage - 1) / perPage);
            for (int p = 0; p < total; p++) {
                TextComponent.Builder page = Component.text().append(navbar(tab, false)).appendNewline()
                        .append(plugin.lang().get("scope.book-rule")).appendNewline()
                        .append(plugin.lang().get("scope.book-title", tr, Text.p("page", p + 1), Text.p("pages", total)))
                        .appendNewline();
                if (entries.isEmpty()) {
                    page.append(plugin.lang().get("scope.empty", tr)).appendNewline();
                }
                for (int i = p * perPage; i < Math.min(entries.size(), (p + 1) * perPage); i++) {
                    Entry e = entries.get(i);
                    HoverEvent<Component> hover = HoverEvent.showText(e.hover());
                    Component l1 = e.line1().hoverEvent(hover);
                    Component l2 = e.line2().hoverEvent(hover);
                    if (e.click() != null) {
                        l1 = l1.clickEvent(e.click());
                        l2 = l2.clickEvent(e.click());
                    }
                    page.append(l1).appendNewline().append(l2).appendNewline();
                }
                for (int pad = Math.min(entries.size() - p * perPage, perPage); pad < perPage; pad++) {
                    page.appendNewline().appendNewline();
                }
                page.append(pager(tab, window, filter, p, total, more));
                pages.add(page.build());
            }
        }
        return Book.book(plugin.lang().get("scope.book-name"), Component.text("Shardwatch"), pages);
    }

    private Component pager(Tab tab, int window, Filter filter, int page, int total, boolean more) {
        TextComponent.Builder b = Component.text();
        String q = filter.raw().isBlank() ? "" : " " + filter.raw();
        if (page > 0) {
            b.append(plugin.lang().get("scope.prev").clickEvent(ClickEvent.changePage(page)));
        } else if (window > 1) {
            b.append(plugin.lang().get("scope.prev-window")
                    .clickEvent(ClickEvent.runCommand("/scope " + tab.id() + " " + (window - 1) + q)));
        } else {
            b.append(plugin.lang().get("scope.prev-off"));
        }
        b.append(plugin.lang().get("scope.page-label", Text.p("page", page + 1), Text.p("pages", total)));
        if (page + 1 < total) {
            b.append(plugin.lang().get("scope.next").clickEvent(ClickEvent.changePage(page + 2)));
        } else if (more) {
            b.append(plugin.lang().get("scope.next-window")
                    .clickEvent(ClickEvent.runCommand("/scope " + tab.id() + " " + (window + 1) + q)));
        } else {
            b.append(plugin.lang().get("scope.next-off"));
        }
        return b.build();
    }

    // ------------------------------------------------------------------ chat renderer

    private void renderChat(Player viewer, Tab tab, int window, Filter filter, List<Entry> entries, List<Component> stats,
                            boolean more) {
        int rows = plugin.getConfig().getInt("scope.chat-rows", 12);
        TagResolver tr = TagResolver.resolver(Text.pp("tab", plugin.lang().raw("scope.tabs." + tab.id() + ".name")),
                Text.p("filter", filter.raw().isBlank() ? "-" : filter.raw()), Text.p("window", window));
        viewer.sendMessage(plugin.lang().get("scope.chat-top", tr));
        viewer.sendMessage(navbar(tab, true));
        if (tab == Tab.OVERVIEW) {
            stats.forEach(viewer::sendMessage);
        }
        for (int i = 0; i < Math.min(rows, entries.size()); i++) {
            Entry e = entries.get(i);
            Component line = e.line1().append(Component.space()).append(e.line2()).hoverEvent(HoverEvent.showText(e.hover()));
            viewer.sendMessage(e.click() == null ? line : line.clickEvent(e.click()));
        }
        if (entries.isEmpty() && tab != Tab.OVERVIEW) {
            viewer.sendMessage(plugin.lang().get("scope.empty", tr));
        }
        String q = filter.raw().isBlank() ? "" : " " + filter.raw();
        Component prev = window > 1 ? plugin.lang().get("scope.prev").clickEvent(
                ClickEvent.runCommand("/scope " + tab.id() + " " + (window - 1) + q + " --chat")) : plugin.lang().get("scope.prev-off");
        Component next = more ? plugin.lang().get("scope.next").clickEvent(
                ClickEvent.runCommand("/scope " + tab.id() + " " + (window + 1) + q + " --chat")) : plugin.lang().get("scope.next-off");
        viewer.sendMessage(prev.append(plugin.lang().get("scope.chat-page", Text.p("window", window))).append(next));
        viewer.sendMessage(plugin.lang().get("scope.chat-bottom", tr));
    }
}
