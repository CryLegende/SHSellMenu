package org.Cry_Legende.sHSellMenu.shop;

import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.ShopGuiPlugin;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.shop.ShopManager;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.item.ShopItem;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Accès à ShopGUIPlus. La vente d'une quantité ({@code ShopManager#handleItemSell}) et les réglages des boutiques
 * ({@code Settings}) ne font pas partie de son API publiée : ils sont résolus une fois au démarrage. Passer par
 * handleItemSell garde tout ce que fait /sell hand : prix et multiplicateurs du joueur, événements de transaction,
 * journal shop.log, son et commandes de vente des objets.
 */
public final class ShopBridge {

    /** Monnaie d'une boutique sans monnaie propre : la première de economyTypes. */
    public static final String DEFAULT_ECONOMY = "DEFAULT";

    private static final List<String> SETTINGS = List.of("sellAllExcludeFreeItems", "disableShopsInWorlds", "disableShopsInGamemodes");

    private final ShopGuiPlugin shopPlugin;
    private final MethodHandle handleItemSell;
    /** Champs statiques de Settings, relus à chaque usage : un /shop reload les met à jour. */
    private final Map<String, Field> settings;

    private ShopBridge(ShopGuiPlugin shopPlugin, MethodHandle handleItemSell, Map<String, Field> settings) {
        this.shopPlugin = shopPlugin;
        this.handleItemSell = handleItemSell;
        this.settings = settings;
    }

    public static ShopBridge open() throws ReflectiveOperationException {
        ShopGuiPlugin shopPlugin = ShopGuiPlusApi.getPlugin();
        if (shopPlugin == null) throw new IllegalStateException("ShopGUIPlus n'est pas chargé");
        MethodHandle sell = MethodHandles.publicLookup().findVirtual(ShopManager.class, "handleItemSell",
            MethodType.methodType(ShopTransactionResult.class, Player.class, ShopItem.class, int.class, boolean.class, boolean.class));
        Class<?> settingsClass = Class.forName("net.brcdev.shopgui.config.Settings", true, shopPlugin.getClass().getClassLoader());
        Map<String, Field> fields = new HashMap<>();
        for (String name : SETTINGS) {
            Field field = settingsClass.getField(name);
            // Lecture d'essai : un champ renommé est vu au démarrage, pas à la première vente
            field.get(null);
            fields.put(name, field);
        }
        return new ShopBridge(shopPlugin, sell, Map.copyOf(fields));
    }

    public boolean isPlayerLoaded(Player player) {
        return shopPlugin.getPlayerManager().isPlayerLoaded(player);
    }

    /** Objet du shop qui achète {@code item} à ce joueur, ou null s'il ne se vend pas. */
    public ShopItem findShopItem(Player player, ItemStack item) {
        return shopPlugin.getShopManager().findShopItemByItemStack(player, item, Boolean.TRUE.equals(value("sellAllExcludeFreeItems")));
    }

    /** Ce que rapporte cette quantité à ce joueur, multiplicateurs compris. */
    public double price(Player player, ShopItem shopItem, int amount) {
        return shopItem.getSellPriceForAmount(player, amount);
    }

    /** Vend cette quantité de l'inventaire porté, sans message au joueur ; la vente est écrite dans shop.log. */
    public ShopTransactionResult sell(Player player, ShopItem shopItem, int amount) throws Throwable {
        return (ShopTransactionResult) handleItemSell.invoke(shopPlugin.getShopManager(), player, shopItem, amount, false, true);
    }

    /** Clé de la monnaie d'un objet du shop : nom de l'EconomyType, ou {@link #DEFAULT_ECONOMY}. */
    public static String economy(ShopItem shopItem) {
        EconomyType type = shopItem.getShop().getEconomyType();
        return type == null ? DEFAULT_ECONOMY : type.name();
    }

    /** Faux dans un monde ou un mode de jeu où ShopGUIPlus ferme ses boutiques. */
    public boolean canSellHere(Player player) {
        GameMode mode = player.getGameMode();
        String world = player.getWorld().getName();
        return !(value("disableShopsInGamemodes") instanceof Collection<?> modes && modes.contains(mode))
            && !(value("disableShopsInWorlds") instanceof Collection<?> worlds && worlds.contains(world));
    }

    private Object value(String name) {
        try {
            return settings.get(name).get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
