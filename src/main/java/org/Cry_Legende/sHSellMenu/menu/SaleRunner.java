package org.Cry_Legende.sHSellMenu.menu;

import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.Cry_Legende.sHSellMenu.SHSellMenu;
import org.Cry_Legende.sHSellMenu.shop.ShopBridge;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;

/**
 * Vend les objets déposés. ShopGUIPlus ne vend que depuis l'inventaire porté : les objets déposés y sont posés seuls,
 * une sorte à la fois, le temps de la vente, puis l'inventaire du joueur est remis tel quel. Tout se passe dans le même
 * tick. Une vente refusée (événement annulé par un autre plugin) rend ses objets.
 */
final class SaleRunner {

    /** Emplacements de stockage d'un joueur (barre d'action comprise). */
    private static final int STORAGE = 36;

    private final SHSellMenu plugin;
    private final ShopBridge shop;

    SaleRunner(SHSellMenu plugin, ShopBridge shop) {
        this.plugin = plugin;
        this.shop = shop;
    }

    /** Résultat d'une vente : objets vendus, somme gagnée par monnaie, objets à rendre. */
    static final class Sale {
        int sold;
        boolean failed;
        final Map<String, Double> money = new TreeMap<>();
        final List<ItemStack> left = new ArrayList<>();
    }

    Sale sell(Player player, List<ItemStack> items) {
        Sale sale = new Sale();
        Map<ShopItem, List<ItemStack>> groups = new LinkedHashMap<>();
        for (ItemStack item : items) {
            ShopItem shopItem = shop.findShopItem(player, item);
            if (shopItem == null) {
                sale.left.add(item);
            } else {
                groups.computeIfAbsent(shopItem, key -> new ArrayList<>()).add(item);
            }
        }
        if (groups.isEmpty()) return sale;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] own = copy(inventory.getContents());
        try {
            for (Map.Entry<ShopItem, List<ItemStack>> group : groups.entrySet()) {
                List<ItemStack> stacks = group.getValue();
                for (int from = 0; from < stacks.size(); from += STORAGE) {
                    sellBatch(player, group.getKey(), stacks.subList(from, Math.min(from + STORAGE, stacks.size())), own.length, sale);
                }
            }
        } finally {
            inventory.setContents(own);
        }
        return sale;
    }

    private void sellBatch(Player player, ShopItem shopItem, List<ItemStack> stacks, int size, Sale sale) {
        if (sale.failed) {
            sale.left.addAll(stacks);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] alone = new ItemStack[size];
        int amount = 0;
        for (int i = 0; i < stacks.size(); i++) {
            alone[i] = stacks.get(i).clone();
            amount += alone[i].getAmount();
        }
        inventory.setContents(alone);
        try {
            ShopTransactionResult result = shop.sell(player, shopItem, amount);
            if (result != null && result.getResult() == ShopTransactionResult.ShopTransactionResultType.SUCCESS) {
                sale.sold += result.getAmount();
                sale.money.merge(ShopBridge.economy(shopItem), result.getPrice(), Double::sum);
            }
        } catch (Throwable error) {
            sale.failed = true;
            plugin.getLogger().log(Level.SEVERE, "Vente de " + amount + " x " + shopItem.getId() + " par " + player.getName() + " interrompue", error);
        }
        for (ItemStack rest : copy(inventory.getContents())) {
            if (rest != null) sale.left.add(rest);
        }
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copies = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].isEmpty()) copies[i] = contents[i].clone();
        }
        return copies;
    }
}
