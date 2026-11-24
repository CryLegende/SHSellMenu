package org.Cry_Legende.sHSellMenu.menu;

import org.Cry_Legende.sHSellMenu.SHSellMenu;
import org.Cry_Legende.sHSellMenu.pages.PagesLink;
import org.Cry_Legende.sHSellMenu.shop.ShopBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Menu de vente (/sell sans argument, /vendre) : le joueur dépose ses objets comme dans un coffre, voit le prix de
 * chaque pile et le total, et vend en fermant le menu ou par le bouton. Les clics du menu sont tous annulés puis refaits
 * ici sur les vrais objets. Le menu fermé autrement que par le joueur (mort, déconnexion, téléportation, arrêt du
 * serveur) rend tout.
 */
public final class SellMenus implements Listener {

    private final SHSellMenu plugin;
    private final ShopBridge shop;
    private final SaleRunner sales;
    private final PagesLink pages;

    public SellMenus(SHSellMenu plugin, ShopBridge shop, PagesLink pages) {
        this.plugin = plugin;
        this.shop = shop;
        this.sales = new SaleRunner(plugin, shop);
        this.pages = pages;
    }

    public void open(Player player) {
        if (!player.hasPermission(SHSellMenu.USE_PERMISSION)) {
            plugin.texts().send(player, "no-permission");
        } else if (!shop.canSellHere(player)) {
            plugin.texts().send(player, "unavailable-here");
        } else if (!shop.isPlayerLoaded(player)) {
            plugin.texts().send(player, "loading");
        } else {
            player.openInventory(new SellMenu(plugin, shop, player).getInventory());
            plugin.playSound(player, "sounds.open");
        }
    }

    private static SellMenu menuOf(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof SellMenu menu ? menu : null;
    }

    // =========================================================================
    //  CLICS
    // =========================================================================

    /** Clic dans le menu : annulé avant tout autre plugin, puis refait sur les vrais objets. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMenuClick(InventoryClickEvent event) {
        SellMenu menu = menuOf(event.getView());
        if (menu == null || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (menu.closing) {
            event.setCancelled(true);
            return;
        }
        if (slot < 0 || slot >= menu.getInventory().getSize()) return;
        boolean refused = event.isCancelled();
        event.setCancelled(true);
        if (refused) return;
        if (menu.isConfirm(slot)) {
            if (!menu.isEmpty()) close(player, menu, true);
        } else if (menu.isCancel(slot)) {
            close(player, menu, false);
        } else if (menu.isDepositSlot(slot)) {
            switch (event.getClick()) {
                case LEFT -> leftClick(player, menu, slot);
                case RIGHT -> rightClick(player, menu, slot);
                case SHIFT_LEFT, SHIFT_RIGHT -> takeBack(player, menu, slot);
                default -> {
                    // Touches de la barre d'action, main secondaire, jet et double clic : sans effet dans le menu
                }
            }
        }
    }

    /** Shift + clic dans son inventaire : dépose la pile. Après les autres plugins, qui protègent certains objets. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        SellMenu menu = menuOf(event.getView());
        if (menu == null || event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY
            || event.getRawSlot() < menu.getInventory().getSize()) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (menu.closing || item == null || item.isEmpty()) return;
        event.setCurrentItem(menu.deposit(item.clone()));
    }

    /** Glisser dans le menu : annulé, puis refait au tick suivant si le joueur tient toujours le même objet. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        SellMenu menu = menuOf(event.getView());
        if (menu == null || !(event.getWhoClicked() instanceof Player player)) return;
        int size = menu.getInventory().getSize();
        if (event.getRawSlots().stream().noneMatch(slot -> slot < size)) return;
        boolean refused = event.isCancelled() || menu.closing;
        event.setCancelled(true);
        if (refused) return;
        Map<Integer, Integer> amounts = new HashMap<>();
        event.getNewItems().forEach((slot, item) -> {
            if (menu.isDepositSlot(slot) && menu.get(slot) == null) amounts.put(slot, item.getAmount());
        });
        ItemStack held = event.getOldCursor().clone();
        if (amounts.isEmpty() || held.isEmpty()) return;
        player.getScheduler().run(plugin, task -> {
            ItemStack cursor = player.getItemOnCursor().clone();
            if (menu.closing || menuOf(player.getOpenInventory()) != menu || !cursor.equals(held)) return;
            int left = cursor.getAmount();
            for (Map.Entry<Integer, Integer> entry : amounts.entrySet()) {
                int moved = Math.min(left, entry.getValue());
                if (moved <= 0 || menu.get(entry.getKey()) != null) continue;
                menu.set(entry.getKey(), cursor.asQuantity(moved));
                left -= moved;
            }
            player.setItemOnCursor(left == 0 ? null : cursor.asQuantity(left));
        }, null);
    }

    private static void leftClick(Player player, SellMenu menu, int slot) {
        ItemStack cursor = player.getItemOnCursor().clone();
        ItemStack stored = menu.get(slot);
        if (cursor.isEmpty()) {
            if (stored == null) return;
            menu.set(slot, null);
            player.setItemOnCursor(stored);
            return;
        }
        int max = cursor.getMaxStackSize();
        if (stored == null || stored.isSimilar(cursor)) {
            int already = stored == null ? 0 : stored.getAmount();
            int moved = Math.min(cursor.getAmount(), max - already);
            if (moved > 0) move(player, menu, slot, cursor, already, moved);
        } else {
            swap(player, menu, slot, cursor, stored);
        }
    }

    private static void rightClick(Player player, SellMenu menu, int slot) {
        ItemStack cursor = player.getItemOnCursor().clone();
        ItemStack stored = menu.get(slot);
        if (cursor.isEmpty()) {
            if (stored == null) return;
            int taken = (stored.getAmount() + 1) / 2;
            int left = stored.getAmount() - taken;
            menu.set(slot, left == 0 ? null : stored.asQuantity(left));
            player.setItemOnCursor(stored.asQuantity(taken));
            return;
        }
        if (stored == null || stored.isSimilar(cursor)) {
            int already = stored == null ? 0 : stored.getAmount();
            if (already < cursor.getMaxStackSize()) move(player, menu, slot, cursor, already, 1);
        } else {
            swap(player, menu, slot, cursor, stored);
        }
    }

    private static void move(Player player, SellMenu menu, int slot, ItemStack cursor, int already, int moved) {
        menu.set(slot, cursor.asQuantity(already + moved));
        int left = cursor.getAmount() - moved;
        player.setItemOnCursor(left == 0 ? null : cursor.asQuantity(left));
    }

    private static void swap(Player player, SellMenu menu, int slot, ItemStack cursor, ItemStack stored) {
        if (cursor.getAmount() > cursor.getMaxStackSize()) return;
        menu.set(slot, cursor);
        player.setItemOnCursor(stored);
    }

    /** Shift + clic sur un objet déposé : il revient dans l'inventaire porté, dans la limite de la place libre. */
    private static void takeBack(Player player, SellMenu menu, int slot) {
        ItemStack stored = menu.get(slot);
        if (stored == null) return;
        Map<Integer, ItemStack> rest = player.getInventory().addItem(stored);
        menu.set(slot, rest.isEmpty() ? null : rest.values().iterator().next());
    }

    // =========================================================================
    //  FERMETURE
    // =========================================================================

    /** Ferme au tick suivant : un menu ne se ferme pas pendant un clic. */
    private void close(Player player, SellMenu menu, boolean sell) {
        menu.closing = true;
        menu.choice = sell;
        player.getScheduler().run(plugin, task -> {
            if (menuOf(player.getOpenInventory()) == menu) player.closeInventory();
        }, null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        SellMenu menu = menuOf(event.getView());
        if (menu == null || !(event.getPlayer() instanceof Player player)) return;
        boolean sell;
        if (menu.choice != null) {
            sell = menu.choice && event.getReason() != InventoryCloseEvent.Reason.DISCONNECT
                && event.getReason() != InventoryCloseEvent.Reason.DEATH;
        } else {
            sell = event.getReason() == InventoryCloseEvent.Reason.PLAYER && plugin.getConfig().getBoolean("menu.sell-on-close", true);
        }
        settle(player, menu, sell);
    }

    /** Arrêt du plugin ou du serveur : chaque menu ouvert rend ses objets. */
    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SellMenu menu = menuOf(player.getOpenInventory());
            if (menu == null) continue;
            settle(player, menu, false);
            player.closeInventory();
        }
    }

    /** Vend ou rend le contenu du menu, une seule fois. */
    private void settle(Player player, SellMenu menu, boolean sell) {
        if (menu.settled) return;
        menu.settled = true;
        menu.closing = true;
        List<ItemStack> items = menu.takeAll();
        if (items.isEmpty()) return;
        if (!sell || !shop.canSellHere(player) || !shop.isPlayerLoaded(player)) {
            items.forEach(item -> pages.give(player, item));
            return;
        }
        SaleRunner.Sale sale = sales.sell(player, items);
        sale.left.forEach(item -> pages.give(player, item));
        // La vente a envoyé au client un inventaire provisoire
        if (plugin.isEnabled()) player.getScheduler().run(plugin, task -> player.updateInventory(), null);
        report(player, sale);
    }

    private void report(Player player, SaleRunner.Sale sale) {
        int left = sale.left.stream().mapToInt(ItemStack::getAmount).sum();
        if (sale.sold > 0) {
            plugin.texts().send(player, "sold", "%quantity%", plugin.formatAmount(sale.sold), "%price%", plugin.formatMoney(sale.money));
        }
        if (sale.failed) {
            plugin.texts().send(player, "error");
        } else if (left > 0) {
            plugin.texts().send(player, "left", "%quantity%", plugin.formatAmount(left));
        }
    }
}
