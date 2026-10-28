package uz.sentinel.ac.commands;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.MovementCheckRunner;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import uz.sentinel.ac.SentinelBridge;
import uz.sentinel.ac.checks.Check;
import uz.sentinel.ac.utils.ColorUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SentinelCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final SentinelBridge bridge;

    public SentinelCommand(Plugin plugin, SentinelBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = plugin.getConfig().getString("prefix", "&#FF416Cꜱᴇɴᴛɪɴᴇʟ &#94A3B8»");

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            if (!sender.hasPermission("sentinel.admin")) {
                sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&#EF4444Ruxsat yo'q!")));
                return true;
            }
            bridge.reload();
            String reloadMsg = plugin.getConfig().getString("messages.reload-success", "%prefix% &#10B981Konfiguratsiya yangilandi!");
            sender.sendMessage(ColorUtil.color(reloadMsg.replace("%prefix%", prefix)));
            return true;
        }

        if (sub.equals("alerts")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ColorUtil.color(prefix + " &#EF4444Bu buyruq faqat o'yinchilar uchun!"));
                return true;
            }
            if (!player.hasPermission("sentinel.alerts")) {
                player.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&#EF4444Ruxsat yo'q!")));
                return true;
            }

            boolean enabled = bridge.toggleAlerts(player.getUniqueId());
            String key = enabled ? "messages.alerts-toggle-on" : "messages.alerts-toggle-off";
            String msg = plugin.getConfig().getString(key, "%prefix% Ogohlantirishlar holati o'zgardi.");
            player.sendMessage(ColorUtil.color(msg.replace("%prefix%", prefix)));
            return true;
        }

        if (sub.equals("perf") || sub.equals("performance")) {
            if (!sender.hasPermission("sentinel.admin")) {
                sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&#EF4444Ruxsat yo'q!")));
                return true;
            }
            double millis = MovementCheckRunner.predictionNanos / 1000000.0;
            double longMillis = MovementCheckRunner.longPredictionNanos / 1000000.0;
            sender.sendMessage(ColorUtil.color("&#FF416C--------------------------------------------------"));
            sender.sendMessage(ColorUtil.color("&#FF416C  ꜱᴇɴᴛɪɴᴇʟ &#E2E8F0Fizik Simulyatsiya Unumdorligi:"));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Prediksiya kechikishi (avg. 500): &#10B981" + String.format("%.4f", millis) + " ms"));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Prediksiya kechikishi (avg. 20k): &#10B981" + String.format("%.4f", longMillis) + " ms"));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Holat: " + (millis < 0.1 ? "&#10B981A'lo (0-Lag)" : "&#FFD200Normal")));
            sender.sendMessage(ColorUtil.color("&#FF416C--------------------------------------------------"));
            return true;
        }

        if (sub.equals("profile")) {
            if (!sender.hasPermission("sentinel.admin")) {
                sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&#EF4444Ruxsat yo'q!")));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ColorUtil.color(prefix + " &#EF4444Foydalanish: /sentinel profile <o'yinchi>"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(ColorUtil.color(prefix + " &#EF4444O'yinchi topilmadi yoki oflayn!"));
                return true;
            }
            GrimPlayer gp = null;
            try {
                gp = GrimAPI.INSTANCE.getPlayerDataManager().getPlayer(target.getUniqueId());
            } catch (Throwable ignored) {}

            String brand = (gp != null && gp.getBrand() != null) ? gp.getBrand() : "Vanilla";
            String version = (gp != null && gp.getClientVersion() != null) ? gp.getClientVersion().getReleaseName() : "1.20+";
            String hSens = (gp != null) ? String.format("%.1f", gp.getHorizontalSensitivity() * 100) : "100.0";
            String vSens = (gp != null) ? String.format("%.1f", gp.getVerticalSensitivity() * 100) : "100.0";

            sender.sendMessage(ColorUtil.color("&#FF416C=================================================="));
            sender.sendMessage(ColorUtil.color("&#FF416C  ꜱᴇɴᴛɪɴᴇʟ &#E2E8F0Profil ma'lumotlari: &#FFD200" + target.getName()));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Ping: &#E2E8F0" + target.getPing() + " ms"));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Mijoz klienti (Brand): &#E2E8F0" + brand));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Mijoz versiyasi: &#E2E8F0" + version));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Gorizontal sezgirlik: &#E2E8F0" + hSens + "%"));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Vertikal sezgirlik: &#E2E8F0" + vSens + "%"));
            sender.sendMessage(ColorUtil.color("  &#94A3B8Gamemode: &#E2E8F0" + target.getGameMode().name()));
            sender.sendMessage(ColorUtil.color("&#FF416C=================================================="));
            return true;
        }

        if (sub.equals("info") || sub.equals("stats")) {
            if (!sender.hasPermission("sentinel.admin")) {
                sender.sendMessage(ColorUtil.color(plugin.getConfig().getString("messages.no-permission", "&#EF4444Ruxsat yo'q!")));
                return true;
            }

            sender.sendMessage(ColorUtil.color("&#FF416C--------------------------------------------------"));
            sender.sendMessage(ColorUtil.color("&#FF416C  ꜱᴇɴᴛɪɴᴇʟ &#E2E8F0Anticheat Engine &#94A3B8(v2.3.74-SENTINEL)"));
            sender.sendMessage(ColorUtil.color("&#94A3B8  Muallif: &#FFD200azicoo"));
            sender.sendMessage(ColorUtil.color("&#94A3B8  Ishchi yadro: &#FFD200Sentinel Hybrid Simulation Engine"));
            sender.sendMessage(ColorUtil.color("&#94A3B8  Versiya mosligi: &#10B981Paper 26.x (26.1 - 26.3 Universal)"));
            sender.sendMessage(ColorUtil.color("&#94A3B8  Nazoratdagi o'yinchilar: &#10B981" + Bukkit.getOnlinePlayers().size()));
            sender.sendMessage(ColorUtil.color("&#94A3B8  Faol qo'shimcha modullar:"));
            for (Check check : bridge.getChecks()) {
                sender.sendMessage(ColorUtil.color("   &#10B981✔ &#E2E8F0" + check.getName() + " &#94A3B8- " + check.getDescription()));
            }
            sender.sendMessage(ColorUtil.color("&#FF416C--------------------------------------------------"));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&#FF416C--------------------------------------------------"));
        sender.sendMessage(ColorUtil.color("&#FF416C  ꜱᴇɴᴛɪɴᴇʟ &#E2E8F0Anticheat Boshqaruvi:"));
        sender.sendMessage(ColorUtil.color("  &#FFD200/sentinel alerts &#94A3B8- Ogohlantirishlarni yoqish/o'chirish"));
        sender.sendMessage(ColorUtil.color("  &#FFD200/sentinel reload &#94A3B8- Konfiguratsiyani qayta yuklash"));
        sender.sendMessage(ColorUtil.color("  &#FFD200/sentinel perf   &#94A3B8- Simulyatsiya va unumdorlik statistikasi"));
        sender.sendMessage(ColorUtil.color("  &#FFD200/sentinel profile <o'yinchi> &#94A3B8- O'yinchi holati"));
        sender.sendMessage(ColorUtil.color("  &#FFD200/sentinel info   &#94A3B8- Tizim statistikasi va modullar"));
        sender.sendMessage(ColorUtil.color("&#FF416C--------------------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (String sub : Arrays.asList("alerts", "reload", "perf", "profile", "info", "stats", "help")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    list.add(sub);
                }
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return List.of();
    }
}
