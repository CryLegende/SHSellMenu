<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHSellMenu

*Menu de vente pour ShopGUIPlus : on dépose, on voit les prix, on ferme, c'est vendu.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

- **`/sell` sans argument** (ou `/vendre`) ouvre un menu où le joueur dépose ses objets comme dans un coffre : clic, shift + clic, glisser. `/sell hand`, `/sell all` et les autres sous-commandes ne changent pas.
- **Prix affichés aussitôt** : chaque pile déposée montre dans sa description ce qu'elle rapporte, et le bouton de validation montre le total.
- **Vendre** : fermer le menu ou cliquer sur le bouton. Un autre bouton rend tout sans vendre. Un objet qui ne se vend pas est signalé et rendu.
- **Les ventes sont celles de ShopGUIPlus** : mêmes prix, multiplicateurs, monnaies, accès aux boutiques, événements de transaction, journal `shop.log`, sons et commandes de vente.
- **Aucun objet modifié** : le menu n'affiche que des copies, les vrais objets restent à part. Une mort, une déconnexion, une téléportation, un autre menu ou un arrêt du serveur rend tout sans vendre.
- **Menu de quantité de ShopGUIPlus** : son bouton « Confirmer » n'a qu'un texte pour l'achat et la vente. À la vente, il prend celui de `messages.yml`.
- **Menu principal de ShopGUIPlus** : ShopGUIPlus ne lit pas PlaceholderAPI. À l'ouverture du menu principal (`/shop`, bouton Retour d'une boutique), les placeholders du nom et de la description des objets de `shopMenuItems` sont remplacés pour le joueur. Un bouton peut donc afficher un classement, un solde ou un compteur sans remplacer ce menu par un autre plugin.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoire : `ShopGUIPlus` (1.113)
- Optionnelles : `SHInventoryPages` (les objets rendus vont dans les autres pages si l'inventaire est plein, sinon au sol), `PlaceholderAPI` (placeholders du menu principal de ShopGUIPlus)

La vente (`ShopManager#handleItemSell`) et les réglages `Settings` de ShopGUIPlus ne font pas partie de son API publiée : ils sont résolus au démarrage. Si une mise à jour de ShopGUIPlus les a changés, le plugin se désactive avec un message dans la console.

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/sell` (sans argument) | Ouvre le menu de vente. | `shsellmenu.use` |
| `/vendre` | Ouvre le menu de vente. | `shsellmenu.use` |
| `/shsellmenu reload` | Recharge `config.yml` et `messages.yml`. Un fichier illisible annule le rechargement. | `shsellmenu.admin.reload` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shsellmenu.use` | Ouvrir le menu de vente. | `true` |
| `shsellmenu.admin` | Toute l'administration (parent de `shsellmenu.admin.reload`). | `op` |
| `shsellmenu.admin.reload` | Recharger `config.yml` et `messages.yml`. | `op` |

## Configuration

<pre>
plugins/SHSellMenu/
├── <a href="src/main/resources/config.yml">config.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.
