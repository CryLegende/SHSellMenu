package org.Cry_Legende.sHSellMenu.shop;

import net.brcdev.shopgui.ShopGuiPlusApi;
import org.Cry_Legende.sHSellMenu.SHSellMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

/**
 * Le menu de quantité de ShopGUIPlus n'a qu'un bouton « Confirmer », le même à l'achat et à la vente. À l'ouverture
 * d'un menu de vente, son texte est remplacé par celui de messages.yml. Un menu de vente se reconnaît à son titre
 * (DIALOG.AMOUNTSELECTION.SELL.NAME de lang.yml, qui doit commencer autrement que celui de l'achat).
 */
public final class AmountMenuText implements Listener {

    private static final String CONFIRM = "confirm";

    private final SHSellMenu plugin;
    private final Class<?> holderClass;
    private final Method getTitle;
    private final Object sellTitle;
    private final Object buyTitle;
    private final Field amountSettings;
    private final Method getButton;
    private final Method getSlots;

    private AmountMenuText(SHSellMenu plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        ClassLoader loader = ShopGuiPlusApi.getPlugin().getClass().getClassLoader();
        holderClass = Class.forName("net.brcdev.shopgui.inventory.AmountSelectionGuiInventoryHolder", true, loader);
        getTitle = holderClass.getMethod("getTitle");
        Class<?> lang = Class.forName("net.brcdev.shopgui.config.Lang", true, loader);
        sellTitle = lang.getField("DIALOG_AMOUNTSELECTION_SELL_NAME").get(null);
        buyTitle = lang.getField("DIALOG_AMOUNTSELECTION_BUY_NAME").get(null);
        amountSettings = Class.forName("net.brcdev.shopgui.config.Settings", true, loader).getField("amountSelectionGUISettings");
        getButton = amountSettings.getType().getMethod("getButton", String.class);
        getSlots = getButton.getReturnType().getMethod("getSlots");
    }

    /** Écouteur prêt à enregistrer, ou null si une mise à jour de ShopGUIPlus a changé son menu de quantité. */
    public static AmountMenuText open(SHSellMenu plugin) {
        try {
            return new AmountMenuText(plugin);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            plugin.getLogger().log(Level.WARNING, "Menu de quantité de ShopGUIPlus non reconnu : son bouton garde son texte", e);
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder(false);
        if (holder == null || holder.getClass() != holderClass || !plugin.getConfig().getBoolean("amount-menu.sell-text", true)) return;
        try {
            String sell = prefix(sellTitle);
            if (sell.isEmpty() || sell.equals(prefix(buyTitle)) || !String.valueOf(getTitle.invoke(holder)).startsWith(sell)) return;
            Object button = getButton.invoke(amountSettings.get(null), CONFIRM);
            if (button == null) return;
            for (Object slot : (List<?>) getSlots.invoke(button)) relabel(inventory, (Integer) slot);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Texte du bouton de vente impossible à poser", e);
        }
    }

    private void relabel(Inventory inventory, int slot) {
        ItemStack item = slot < 0 || slot >= inventory.getSize() ? null : inventory.getItem(slot);
        if (item == null || item.isEmpty()) return;
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (!plugin.texts().raw("amount-sell-name").isEmpty()) meta.customName(plugin.texts().itemName("amount-sell-name"));
        meta.lore(plugin.texts().itemLore("amount-sell-lore"));
        copy.setItemMeta(meta);
        inventory.setItem(slot, copy);
    }

    /** Début du titre, avant le nom de l'objet. */
    private static String prefix(Object title) {
        String text = title.toString();
        int item = text.indexOf("%item%");
        return item < 0 ? text : text.substring(0, item);
    }
}
