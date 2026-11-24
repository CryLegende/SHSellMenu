package org.Cry_Legende.sHSellMenu;

import org.Cry_Legende.sHSellMenu.menu.SellMenus;
import org.Cry_Legende.sHSellMenu.pages.PagesLink;
import org.Cry_Legende.sHSellMenu.shop.AmountMenuText;
import org.Cry_Legende.sHSellMenu.shop.ShopBridge;
import org.Cry_Legende.sHSellMenu.util.ConfigFiles;
import org.Cry_Legende.sHSellMenu.util.Texts;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/** Menu de vente pour ShopGUIPlus : /sell sans argument (et /vendre) ouvre un menu où l'on dépose ses objets. */
public final class SHSellMenu extends JavaPlugin implements Listener {

    public static final String USE_PERMISSION = "shsellmenu.use";
    private static final String RELOAD_PERMISSION = "shsellmenu.admin.reload";

    private final Texts texts = new Texts(this);
    private final NamespacedKey displayKey = new NamespacedKey(this, "display");
    private SellMenus menus;
    private volatile Set<String> openCommands = Set.of();
    private volatile DecimalFormat priceFormat;
    private volatile DecimalFormat amountFormat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        texts.load();
        loadSettings();
        try {
            menus = new SellMenus(this, ShopBridge.open(), new PagesLink());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "ShopGUIPlus a changé : le menu de vente est désactivé", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(this, this);
        AmountMenuText amountMenu = AmountMenuText.open(this);
        if (amountMenu != null) getServer().getPluginManager().registerEvents(amountMenu, this);
        registerCommands();
    }

    @Override
    public void onDisable() {
        if (menus != null) menus.closeAll();
    }

    private void loadSettings() {
        Set<String> commands = new HashSet<>();
        for (String command : getConfig().getStringList("open-commands")) commands.add(command.trim().toLowerCase(Locale.ROOT));
        openCommands = Set.copyOf(commands);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.forLanguageTag(getConfig().getString("number-locale", "en")));
        priceFormat = new DecimalFormat(getConfig().getString("price-format", "#,##0.##"), symbols);
        amountFormat = new DecimalFormat("#,##0", symbols);
    }

    // =========================================================================
    //  COMMANDES
    // =========================================================================

    /** Commande d'un autre plugin tapée seule (/sell) : ouvre le menu. Avec un argument, elle n'est pas touchée. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String typed = event.getMessage().trim();
        if (typed.length() < 2 || typed.indexOf(' ') >= 0 || !openCommands.contains(typed.substring(1).toLowerCase(Locale.ROOT))) return;
        Player player = event.getPlayer();
        if (!player.hasPermission(USE_PERMISSION)) return;
        event.setCancelled(true);
        menus.open(player);
    }

    private void registerCommands() {
        PluginCommand open = getCommand("vendre");
        if (open != null) {
            open.setExecutor((sender, command, label, args) -> {
                if (sender instanceof Player player) {
                    menus.open(player);
                } else {
                    texts.send(sender, "player-only");
                }
                return true;
            });
            open.setTabCompleter((sender, command, alias, args) -> List.of());
        }
        PluginCommand admin = getCommand("shsellmenu");
        if (admin != null) {
            admin.setExecutor(this::onAdminCommand);
            admin.setTabCompleter((sender, command, alias, args) ->
                args.length == 1 && sender.hasPermission(RELOAD_PERMISSION) && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("reload") : List.of());
        }
    }

    private boolean onAdminCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            texts.send(sender, "no-permission");
        } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadFiles(sender);
        } else {
            texts.send(sender, "admin-usage", "%subcommands%", "reload");
        }
        return true;
    }

    /**
     * Relit config.yml et messages.yml. Les deux fichiers sont lus avant tout remplacement : si l'un est illisible,
     * l'ancienne configuration reste active. Un menu déjà ouvert garde sa disposition.
     */
    private void reloadFiles(CommandSender sender) {
        YamlConfiguration loadedMessages;
        try {
            ConfigFiles.read(this, "config.yml");
            loadedMessages = ConfigFiles.read(this, "messages.yml");
        } catch (ConfigFiles.ReadError e) {
            getLogger().severe("Reload annulé, " + e.file() + " illisible : " + e.detail());
            texts.send(sender, "reload-error", "%file%", e.file(), "%error%", e.detail());
            return;
        }
        reloadConfig();
        texts.apply(loadedMessages);
        loadSettings();
        texts.send(sender, "reloaded");
    }

    // =========================================================================
    //  OUTILS COMMUNS
    // =========================================================================

    public Texts texts() {
        return texts;
    }

    /** Marque des copies affichées dans le menu. */
    public NamespacedKey displayKey() {
        return displayKey;
    }

    /** Son de config.yml (clé du jeu, par exemple minecraft:ui.button.click) ; vide = aucun. */
    public void playSound(Player player, String key) {
        String sound = getConfig().getString(key, "").trim();
        if (!sound.isEmpty()) player.playSound(player, sound.toLowerCase(Locale.ROOT), 1f, 1f);
    }

    /** Somme suivie du nom de sa monnaie (messages.yml : currency-<MONNAIE>). */
    public String formatPrice(String economy, double value) {
        String amount;
        DecimalFormat format = priceFormat;
        synchronized (format) {
            amount = format.format(value);
        }
        return texts.raw("price", "%amount%", amount, "%currency%", texts.raw("currency-" + economy));
    }

    /** Sommes de plusieurs monnaies, séparées par des virgules. */
    public String formatMoney(Map<String, Double> money) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Double> entry : money.entrySet()) {
            if (!text.isEmpty()) text.append(", ");
            text.append(formatPrice(entry.getKey(), entry.getValue()));
        }
        return text.toString();
    }

    public String formatAmount(long value) {
        DecimalFormat format = amountFormat;
        synchronized (format) {
            return format.format(value);
        }
    }
}
