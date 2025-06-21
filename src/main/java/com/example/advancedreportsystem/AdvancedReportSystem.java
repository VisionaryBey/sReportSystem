package com.example.advancedreportsystem;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

public class AdvancedReportSystem extends JavaPlugin implements Listener {

    private JDA jda;
    private TextChannel reportChannel;
    private final Map<UUID, ReportData> pendingReports = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_TIME = 300000; // 5 dakika cooldown

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupDiscord();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("report").setExecutor(this);
        getLogger().info("Rapor Sistemi aktif edildi!");
    }

    private void setupDiscord() {
        try {
            jda = JDABuilder.createDefault(getConfig().getString("discord.bot-token"))
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new DiscordButtonListener())
                    .build();
            jda.awaitReady();
            reportChannel = jda.getTextChannelById(getConfig().getString("discord.report-channel-id"));
        } catch (Exception e) {
            getLogger().severe("Discord bağlantısı kurulamadı: " + e.getMessage());
        }
    }

    public class DiscordButtonListener extends ListenerAdapter {
        @Override
        public void onButtonInteraction(@Nonnull ButtonInteractionEvent event) {
            if (event.getButton() == null || event.getButton().getId() == null) return;
            
            String buttonIdRaw = event.getButton().getId();
            if (buttonIdRaw == null) return; // Ekstra güvenlik için tekrar kontrol
            String[] buttonId = buttonIdRaw.split(":");
            String action = buttonId[0];
            UUID playerId = UUID.fromString(buttonId[1]);
            
            Player target = Bukkit.getPlayer(playerId);
            if (target == null) {
                event.reply("Oyuncu artık çevrimiçi değil!").setEphemeral(true).queue();
                return;
            }

            String adminName = event.getUser().getName();
            switch (action) {
                case "ban":
                    Bukkit.getScheduler().runTask(AdvancedReportSystem.this, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + target.getName() + " Rapor sistemi ile banlandı - Yetkili: " + adminName);
                    });
                    event.reply(target.getName() + " başarıyla banlandı!").queue();
                    break;
                case "kick":
                    Bukkit.getScheduler().runTask(AdvancedReportSystem.this, () -> {
                        target.kickPlayer("Rapor sistemi ile atıldınız - Yetkili: " + adminName);
                    });
                    event.reply(target.getName() + " başarıyla kicklendi!").queue();
                    break;
                case "warn":
                    Bukkit.getScheduler().runTask(AdvancedReportSystem.this, () -> {
                        target.sendMessage(ChatColor.RED + "UYARI: Rapor sistemi ile uyarı aldınız - Yetkili: " + adminName);
                    });
                    event.reply(target.getName() + " başarıyla uyarıldı!").queue();
                    break;
                case "ignore":
                    event.reply("Rapor yoksayıldı.").queue();
                    break;
            }

            event.getMessage().editMessageComponents().queue();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Sadece oyuncular kullanabilir!");
            return true;
        }

        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (cooldowns.get(player.getUniqueId()) + COOLDOWN_TIME) - System.currentTimeMillis();
            if (timeLeft > 0) {
                player.sendMessage(ChatColor.RED + String.format("Rapor göndermek için %d saniye beklemelisiniz!", timeLeft / 1000));
                return true;
            }
        }

        openPlayerSelectionGUI(player);
        return true;
    }

    private void openPlayerSelectionGUI(Player reporter) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Rapor Edilecek Oyuncu Seçin");

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(reporter))
                .forEach(player -> {
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    meta.setOwningPlayer(player);
                    meta.setDisplayName(ChatColor.GREEN + player.getName());
                    meta.setLore(Arrays.asList(
                            ChatColor.GRAY + "Seviye: " + player.getLevel(),
                            ChatColor.GRAY + "Dünya: " + player.getWorld().getName(),
                            ChatColor.GRAY + "Ping: " + player.getPing() + "ms",
                            "",
                            ChatColor.YELLOW + "Tıklayarak bu oyuncuyu rapor et"
                    ));
                    head.setItemMeta(meta);
                    gui.addItem(head);
                });

        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.setDisplayName(" ");
        pane.setItemMeta(paneMeta);

        for (int i = 45; i < 54; i++) {
            gui.setItem(i, pane);
        }

        reporter.openInventory(gui);
    }

    private void openReasonSelectionGUI(Player reporter, Player target) {
        Inventory gui = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Rapor Sebebi: " + target.getName());

        addReportReason(gui, 10, Material.DIAMOND_SWORD, "Hile", Arrays.asList(
                ChatColor.RED + "Oyunda hile kullanıyor",
                ChatColor.GRAY + "Aimbot, Fly, Speed vb."
        ));
        
        addReportReason(gui, 11, Material.PAPER, "Küfür", Arrays.asList(
                ChatColor.RED + "Rahatsız edici dil kullanıyor",
                ChatColor.GRAY + "Hakaret, argo vb."
        ));
        
        addReportReason(gui, 12, Material.BARRIER, "Takılma", Arrays.asList(
                ChatColor.RED + "Oyunda takılmaya çalışıyor",
                ChatColor.GRAY + "Bug kullanma, exploit vb."
        ));
        
        addReportReason(gui, 13, Material.BOOK, "Kural İhlali", Arrays.asList(
                ChatColor.RED + "Sunucu kurallarını ihlal ediyor",
                ChatColor.GRAY + "Spam, reklam, troll vb."
        ));
        
        addReportReason(gui, 14, Material.PLAYER_HEAD, "Diğer", Arrays.asList(
                ChatColor.RED + "Diğer sebepler",
                ChatColor.GRAY + "Açıklamayı sonra gireceksiniz"
        ));

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Geri Dön");
        back.setItemMeta(backMeta);
        gui.setItem(16, back);

        reporter.openInventory(gui);
    }

    private void addReportReason(Inventory gui, int slot, Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.RED + name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String title = event.getView().getTitle();
        ItemStack clicked = event.getCurrentItem();
        
        if (clicked == null || !clicked.hasItemMeta()) return;
        
        if (title.startsWith(ChatColor.DARK_RED + "Rapor Edilecek Oyuncu Seçin")) {
            event.setCancelled(true);
            
            if (clicked.getType() == Material.PLAYER_HEAD) {
                String playerName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                Player target = Bukkit.getPlayer(playerName);
                
                if (target != null) {
                    pendingReports.put(player.getUniqueId(), new ReportData(target.getUniqueId()));
                    openReasonSelectionGUI(player, target);
                }
            }
        }
        else if (title.startsWith(ChatColor.DARK_RED + "Rapor Sebebi: ")) {
            event.setCancelled(true);
            
            if (clicked.getType() == Material.ARROW) {
                openPlayerSelectionGUI(player);
                return;
            }
            
            ReportData reportData = pendingReports.get(player.getUniqueId());
            if (reportData != null && clicked.hasItemMeta()) {
                String reason = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                reportData.setReason(reason);
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Lütfen raporunuz için detaylı açıklama yazın:");
                player.sendMessage(ChatColor.GRAY + "İptal etmek için 'iptal' yazabilirsiniz.");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ReportData reportData = pendingReports.get(player.getUniqueId());
        
        if (reportData != null) {
            event.setCancelled(true);
            
            if (event.getMessage().equalsIgnoreCase("iptal")) {
                pendingReports.remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "Rapor işlemi iptal edildi.");
                return;
            }
            
            Player target = Bukkit.getPlayer(reportData.getTargetId());
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Rapor edilen oyuncu artık çevrimiçi değil!");
                pendingReports.remove(player.getUniqueId());
                return;
            }
            
            sendDiscordReport(player, target, reportData.getReason(), event.getMessage());
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            pendingReports.remove(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Raporunuz başarıyla gönderildi! Teşekkür ederiz.");
        }
    }

    private void sendDiscordReport(Player reporter, Player target, String reason, String description) {
        if (reportChannel == null) return;

        // Botun kendi avatarını al
        String botAvatarUrl = jda.getSelfUser().getEffectiveAvatarUrl();

        MessageEmbed embed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("🚨 Yeni Rapor Bildirimi")
                .setColor(new Color(255, 0, 0))
                .setThumbnail(botAvatarUrl)
                .addField("👤 Rapor Eden", 
                    "`" + reporter.getName() + "`\n" +
                    "UUID: `" + reporter.getUniqueId() + "`\n" +
                    "IP: `" + reporter.getAddress().getAddress().getHostAddress() + "`", true)
                .addField("🎯 Rapor Edilen", 
                    "`" + target.getName() + "`\n" +
                    "UUID: `" + target.getUniqueId() + "`\n" +
                    "Ping: `" + target.getPing() + "ms`", true)
                .addField("📌 Sebep", "```" + reason + "```", false)
                .addField("📝 Açıklama", "```" + (description.isEmpty() ? "Açıklama yok" : description) + "```", false)
                .addField("🌍 Sunucu", "`" + Bukkit.getServer().getName() + "`", true)
                .addField("⏰ Zaman", "<t:" + Instant.now().getEpochSecond() + ":R>", true)
              
                
                .build();

        reportChannel.sendMessageEmbeds(embed)
            .setActionRow(
                Button.danger("ban:" + target.getUniqueId(), "⛔ Banla"),
                Button.secondary("kick:" + target.getUniqueId(), "👢 Kickle"),
                Button.success("warn:" + target.getUniqueId(), "⚠️ Uyarı Ver"),
                Button.primary("ignore:" + target.getUniqueId(), "❌ Yoksay")
            ).queue();
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
        }
        getLogger().info("Rapor Sistemi kapatıldı!");
    }

    private static class ReportData {
        private final UUID targetId;
        private String reason;
        
        public ReportData(UUID targetId) {
            this.targetId = targetId;
        }
        
        public UUID getTargetId() {
            return targetId;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}