package uz.sentinel.ac.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import uz.sentinel.ac.SentinelPlugin;
import uz.sentinel.ac.diagnostics.Finding;
import uz.sentinel.ac.utils.ColorUtil;
import java.util.List;
import java.util.Locale;

public final class SentinelCommand implements CommandExecutor, TabCompleter {
    private final SentinelPlugin plugin;
    public SentinelCommand(SentinelPlugin plugin) { this.plugin = plugin; }
    private void send(CommandSender sender, String text) { sender.sendMessage(ColorUtil.color(plugin.settings().prefix() + text)); }
    private boolean permission(CommandSender sender, String suffix) {
        if (sender.hasPermission("sentinel." + suffix)) return true;
        sender.sendMessage(ColorUtil.color(plugin.settings().noPermission().replace("%prefix%", plugin.settings().prefix())));
        return false;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "help" -> {
                    if (!permission(sender, "info")) return true;
                    send(sender, "/sentinel info | alerts | profile <player> | channels <player> | history <player> | perf | reload");
                    send(sender, "Grim sozlamalari va to'liq tarix: /grim help");
                }
                case "info", "stats" -> {
                    if (!permission(sender, "info")) return true;
                    send(sender, "Sentinel " + plugin.getPluginMeta().getVersion() + " | Paper 26.2 | Grim " + plugin.grim().version());
                    send(sender, "ViaVersion: " + Bukkit.getPluginManager().isPluginEnabled("ViaVersion")
                            + " | ViaBackwards: " + Bukkit.getPluginManager().isPluginEnabled("ViaBackwards"));
                    send(sender, "Channel policy=" + plugin.settings().modEnabled() + ", kick=" + plugin.settings().kickOnChannel()
                            + ", rate policy=" + plugin.settings().limiterEnabled());
                    send(sender, "Diagnostika: processed=" + plugin.bridge().processed() + " queued=" + plugin.bridge().queued() + " dropped=" + plugin.bridge().dropped());
                }
                case "alerts" -> {
                    if (!permission(sender, "alerts")) return true;
                    if (!(sender instanceof Player player)) { send(sender, "Faqat o'yinchilar uchun."); return true; }
                    send(sender, "Grim va Sentinel alertlari: " + (plugin.grim().toggleAlerts(player) ? "yoqildi" : "o'chirildi"));
                }
                case "profile" -> {
                    if (!permission(sender, "profile")) return true;
                    if (args.length != 2) { send(sender, "/sentinel profile <player>"); return true; }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { send(sender, "O'yinchi topilmadi."); return true; }
                    var user = plugin.grim().user(target.getUniqueId());
                    send(sender, target.getName() + " | ping=" + target.getPing() + "ms | gamemode=" + target.getGameMode());
                    if (user == null) send(sender, "Grim tracking: unavailable/exempt; klient haqida taxmin qilinmaydi.");
                    else send(sender, "Client=" + Finding.clean(user.getVersionName(), 64) + " | brand=" + Finding.clean(user.getBrand(), 128));
                }
                case "channels" -> {
                    if (!permission(sender, "profile")) return true;
                    if (args.length != 2) { send(sender, "/sentinel channels <player>"); return true; }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) { send(sender, "O'yinchi topilmadi."); return true; }
                    send(sender, "Klient e'lon qilgan kanallar (to'liq mod ro'yxati emas):");
                    target.getListeningPluginChannels().stream().sorted().limit(30)
                            .forEach(channel -> send(sender, Finding.clean(channel, 193) + plugin.settings().channels().match(channel).map(rule -> " [BLOCK: " + rule + "]").orElse("")));
                    if (target.getListeningPluginChannels().isEmpty()) send(sender, "Kanal e'lon qilinmagan.");
                }
                case "history" -> {
                    if (!permission(sender, "history")) return true;
                    if (args.length != 2) { send(sender, "/sentinel history <player>"); return true; }
                    var findings = plugin.bridge().history(args[1]);
                    if (findings.isEmpty()) send(sender, "Yaqin tarixda yozuv yo'q. Doimiy loglar: SentinelAC/logs; Grim tarixi: /grim history");
                    for (Finding f : findings) send(sender, java.time.Instant.ofEpochMilli(f.timeMillis()) + " | " + f.check() + " | " + f.detail());
                }
                case "perf", "performance" -> {
                    if (!permission(sender, "performance")) return true;
                    send(sender, "Diagnostika: queued=" + plugin.bridge().queued() + " dropped=" + plugin.bridge().dropped());
                    Bukkit.dispatchCommand(sender, "grim perf");
                }
                case "reload" -> {
                    if (!permission(sender, "reload")) return true;
                    send(sender, "Konfiguratsiya tekshirilmoqda...");
                    plugin.reloadSettings().whenComplete((success, error) -> {
                        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                            if (error != null) send(sender, "Reload bajarilmadi: " + Finding.clean(error.getMessage(), 256));
                            else send(sender, Boolean.TRUE.equals(success) ? "Grim va Sentinel konfiguratsiyasi yangilandi." : "Grim reload xatosi. Sentinel sozlamalari saqlandi; konsolni tekshiring.");
                        });
                    });
                }
                default -> send(sender, "Noma'lum buyruq. /sentinel help");
            }
        } catch (IllegalArgumentException | IllegalStateException e) { send(sender, Finding.clean(e.getMessage(), 256)); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("help", "info", "alerts", "profile", "channels", "history", "perf", "reload").stream()
                .filter(s -> sender.hasPermission("sentinel." + switch (s) { case "help" -> "info"; case "channels" -> "profile"; case "perf" -> "performance"; default -> s; }))
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && List.of("profile", "channels", "history").contains(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission("sentinel." + (args[0].equalsIgnoreCase("history") ? "history" : "profile"))) {
            return Bukkit.getOnlinePlayers().stream().filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p)).map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
