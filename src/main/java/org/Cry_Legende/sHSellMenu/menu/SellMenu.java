package org.Cry_Legende.sHSellMenu.menu;

import net.brcdev.shopgui.shop.item.ShopItem;
import net.kyori.adventure.text.Component;
import org.Cry_Legende.sHSellMenu.SHSellMenu;
import org.Cry_Legende.sHSellMenu.shop.ShopBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Menu de vente d'un joueur. Les vrais objets déposés sont gardés ici, à part : l'inventaire affiché ne contient que
 * des copies qui portent leur prix, marquées pour ne jamais s'empiler avec un vrai objet et qui ne quittent jamais le
 * menu. Un vrai objet n'est donc jamais modifié.
 */
final class SellMenu implements InventoryHolder {

    private final SHSellMenu plugin;
    private final ShopBridge shop;
    private final Player player;
    private final Inventory inventory;
    private final ItemStack[] items;
    private final ShopItem[] shopItems;
    private final double[] prices;
    private final int confirmSlot;
    private final int cancelSlot;

    /** Fermeture demandée : plus aucun clic n'est accepté. */
    boolean closing;
    /** Choix fait par un bouton : vrai = vendre, faux = reprendre, null = aucun. */
    Boolean choice;
    /** Les objets ont été vendus ou rendus. */
    boolean settled;

    SellMenu(SHSellMenu plugin, ShopBridge shop, Player player) {
        this.plugin = plugin;
        this.shop = shop;
        this.player = player;
        int rows = Math.clamp(plugin.getConfig().getInt("menu.rows", 6), 2, 6);
        int deposit = (rows - 1) * 9;
        items = new ItemStack[deposit];
        shopItems = new ShopItem[deposit];
        prices = new double[deposit];
        confirmSlot = deposit + Math.clamp(plugin.getConfig().getInt("menu.confirm.column", 5), 1, 9) - 1;
        cancelSlot = deposit + Math.clamp(plugin.getConfig().getInt("menu.cancel.column", 1), 1, 9) - 1;
        inventory = Bukkit.createInventory(this, rows * 9, plugin.texts().get("menu-title"));
        ItemStack filler = button("menu.filler", "");
        for (int slot = deposit; slot < rows * 9; slot++) inventory.setItem(slot, filler);
        ItemStack cancel = button("menu.cancel.material", "PAPER");
        if (cancel != null) inventory.setItem(cancelSlot, label(cancel, "cancel-name", "cancel-lore"));
        refreshTotal();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    boolean isDepositSlot(int slot) {
        return slot >= 0 && slot < items.length;
    }

    boolean isConfirm(int slot) {
        return slot == confirmSlot;
    }

    boolean isCancel(int slot) {
        return slot == cancelSlot;
    }

    boolean isEmpty() {
        for (ItemStack item : items) {
            if (item != null) return false;
        }
        return true;
    }

    /** Vrai objet de cet emplacement (une copie), ou null. */
    ItemStack get(int slot) {
        return items[slot] == null ? null : items[slot].clone();
    }

    /** Pose ce vrai objet, ou vide l'emplacement (null). L'objet du shop n'est recherché que si la sorte d'objet change. */
    void set(int slot, ItemStack item) {
        ItemStack previous = items[slot];
        items[slot] = item == null || item.isEmpty() ? null : item.clone();
        if (items[slot] == null) {
            shopItems[slot] = null;
        } else if (previous == null || !previous.isSimilar(items[slot])) {
            shopItems[slot] = shop.findShopItem(player, items[slot]);
        }
        prices[slot] = shopItems[slot] == null ? 0 : shop.price(player, shopItems[slot], items[slot].getAmount());
        inventory.setItem(slot, items[slot] == null ? null : display(slot));
        refreshTotal();
    }

    /** Dépose autant que possible : piles du même objet d'abord, puis emplacements vides. Rend ce qui reste, ou null. */
    ItemStack deposit(ItemStack item) {
        int max = item.getMaxStackSize();
        int left = item.getAmount();
        for (int slot = 0; slot < items.length && left > 0; slot++) {
            if (items[slot] == null || items[slot].getAmount() >= max || !items[slot].isSimilar(item)) continue;
            int moved = Math.min(left, max - items[slot].getAmount());
            set(slot, items[slot].asQuantity(items[slot].getAmount() + moved));
            left -= moved;
        }
        for (int slot = 0; slot < items.length && left > 0; slot++) {
            if (items[slot] != null) continue;
            int moved = Math.min(left, max);
            set(slot, item.asQuantity(moved));
            left -= moved;
        }
        return left == 0 ? null : item.asQuantity(left);
    }

    /** Sort tous les vrais objets du menu. */
    List<ItemStack> takeAll() {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < items.length; slot++) {
            if (items[slot] != null) taken.add(items[slot]);
            items[slot] = null;
            shopItems[slot] = null;
        }
        inventory.clear();
        return taken;
    }

    /** Copie affichée : l'objet et, dans sa description, ce que cette pile rapporte. */
    private ItemStack display(int slot) {
        ItemStack copy = items[slot].clone();
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) lore.addAll(meta.lore());
        ShopItem shopItem = shopItems[slot];
        if (shopItem == null) {
            lore.addAll(plugin.texts().itemLore("unsellable-lore"));
        } else {
            lore.addAll(plugin.texts().itemLore("stack-lore", "%price%", plugin.formatPrice(ShopBridge.economy(shopItem), prices[slot])));
        }
        meta.lore(lore);
        meta.setHideTooltip(false);
        meta.getPersistentDataContainer().set(plugin.displayKey(), PersistentDataType.INTEGER, slot);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Bouton de validation : total de tout ce qui est déposé et vendable. */
    private void refreshTotal() {
        Map<String, Double> money = new TreeMap<>();
        for (int slot = 0; slot < items.length; slot++) {
            if (shopItems[slot] == null) continue;
            money.merge(ShopBridge.economy(shopItems[slot]), prices[slot], Double::sum);
        }
        boolean nothing = money.isEmpty();
        ItemStack confirm = button(nothing ? "menu.confirm.material-empty" : "menu.confirm.material", nothing ? "GRAY_DYE" : "LIME_DYE");
        if (confirm == null) confirm = new ItemStack(Material.LIME_DYE);
        inventory.setItem(confirmSlot, nothing
            ? label(confirm, "empty-name", "empty-lore")
            : label(confirm, "confirm-name", "confirm-lore", "%total%", plugin.formatMoney(money)));
    }

    private ItemStack label(ItemStack item, String nameKey, String loreKey, String... replacements) {
        ItemMeta meta = item.getItemMeta();
        meta.customName(plugin.texts().itemName(nameKey, replacements));
        meta.lore(plugin.texts().itemLore(loreKey, replacements));
        item.setItemMeta(meta);
        return item;
    }

    /** Objet de menu sans nom affiché, ou null si la clé est vide ou n'est pas un objet. */
    private ItemStack button(String key, String fallback) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(key, fallback));
        if (material == null || material.isAir() || !material.isItem()) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setHideTooltip("menu.filler".equals(key));
        item.setItemMeta(meta);
        return item;
    }
}
