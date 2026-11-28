package org.Cry_Legende.sHSellMenu.shop;

import me.clip.placeholderapi.PlaceholderAPI;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHSellMenu.SHSellMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ShopGUIPlus ne lit pas PlaceholderAPI. À l'ouverture de son menu principal (/shop, bouton Retour d'une boutique), les
 * placeholders du nom et de la description de chaque objet sont remplacés pour le joueur qui l'ouvre. Seules les copies
 * posées dans ce menu changent, les objets chargés par ShopGUIPlus restent tels quels.
 */
public final class MainMenuPlaceholders implements Listener {

    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
        .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern CODE = Pattern.compile("&([0-9a-fA-Fk-oK-OrR])");

    private final SHSellMenu plugin;
    private final Class<?> holderClass;

    private MainMenuPlaceholders(SHSellMenu plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        ClassLoader loader = ShopGuiPlusApi.getPlugin().getClass().getClassLoader();
        holderClass = Class.forName("net.brcdev.shopgui.inventory.MainMenuInventoryHolder", true, loader);
    }

    /** Écouteur prêt à enregistrer, ou null sans PlaceholderAPI ou si une mise à jour de ShopGUIPlus a changé son menu. */
    public static MainMenuPlaceholders open(SHSellMenu plugin) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            plugin.getLogger().warning("PlaceholderAPI absent : les placeholders du menu principal de ShopGUIPlus restent affichés tels quels");
            return null;
        }
        try {
            return new MainMenuPlaceholders(plugin);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "Menu principal de ShopGUIPlus non reconnu : ses placeholders restent affichés tels quels", e);
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder(false);
        if (holder == null || holder.getClass() != holderClass || !plugin.getConfig().getBoolean("main-menu.placeholders", true)) return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.isEmpty() || !item.hasItemMeta()) continue;
            ItemStack resolved = resolve(player, item);
            if (resolved != null) inventory.setItem(slot, resolved);
        }
    }

    /** Copie de l'objet avec ses placeholders remplacés, ou null s'il n'en a pas. */
    private static ItemStack resolve(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        Component name = meta.displayName();
        if (name != null) {
            Component replaced = replace(player, name);
            if (replaced != null) {
                meta.displayName(replaced);
                changed = true;
            }
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            List<Component> lines = new ArrayList<>(lore.size());
            boolean loreChanged = false;
            for (Component line : lore) {
                Component replaced = replace(player, line);
                loreChanged |= replaced != null;
                lines.add(replaced != null ? replaced : line);
            }
            if (loreChanged) {
                meta.lore(lines);
                changed = true;
            }
        }
        if (!changed) return null;
        ItemStack copy = item.clone();
        copy.setItemMeta(meta);
        return copy;
    }

    /** Ligne avec ses placeholders remplacés (codes & et &#RRGGBB du résultat compris), ou null si elle n'en a pas. */
    private static Component replace(Player player, Component line) {
        String text = SECTION.serialize(line);
        if (text.indexOf('%') < 0) return null;
        String replaced = PlaceholderAPI.setPlaceholders(player, text);
        if (replaced.equals(text)) return null;
        return SECTION.deserialize(colors(replaced)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String colors(String text) {
        Matcher hex = HEX.matcher(text);
        StringBuilder out = new StringBuilder();
        while (hex.find()) {
            StringBuilder code = new StringBuilder("§x");
            for (char c : hex.group(1).toCharArray()) code.append('§').append(c);
            hex.appendReplacement(out, Matcher.quoteReplacement(code.toString()));
        }
        hex.appendTail(out);
        return CODE.matcher(out).replaceAll(code -> "§" + code.group(1).toLowerCase(java.util.Locale.ROOT));
    }
}
