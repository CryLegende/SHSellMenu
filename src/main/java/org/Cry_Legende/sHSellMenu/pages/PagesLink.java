package org.Cry_Legende.sHSellMenu.pages;

import org.Cry_Legende.sHInventoryPages.api.ISHInventoryPagesAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Rend des objets à un joueur : inventaire porté, puis autres pages d'inventaire si SHInventoryPages est installé. Ce
 * qui ne trouve pas de place tombe aux pieds du joueur.
 */
public final class PagesLink {

    private final boolean pagesInstalled;

    public PagesLink() {
        pagesInstalled = Bukkit.getPluginManager().isPluginEnabled("SHInventoryPages");
    }

    public void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> rest = pagesInstalled ? Pages.give(player, item) : player.getInventory().addItem(item);
        rest.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    /** Classe chargée seulement si SHInventoryPages est installé. L'API est relue à chaque appel (rechargement). */
    private static final class Pages {
        static Map<Integer, ItemStack> give(Player player, ItemStack item) {
            ISHInventoryPagesAPI api = Bukkit.getServicesManager().load(ISHInventoryPagesAPI.class);
            return api == null ? player.getInventory().addItem(item) : api.addItems(player, item);
        }
    }
}
