package org.Cry_Legende.sHSellMenu.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Messages de messages.yml (codes & et &#RRGGBB), envoyés en composants. */
public final class Texts {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
        .character('&').hexCharacter('#').hexColors().build();

    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public Texts(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * messages.yml du dossier du plugin, complété par celui du jar pour les textes qui y manquent. Un fichier
     * illisible n'empêche pas le démarrage : les textes du jar servent jusqu'au prochain reload.
     */
    public void load() {
        try {
            messages = ConfigFiles.read(plugin, "messages.yml");
        } catch (ConfigFiles.ReadError e) {
            plugin.getLogger().severe(e.file() + " illisible, textes du jar utilisés : " + e.detail());
            messages = ConfigFiles.bundled(plugin, "messages.yml");
        }
    }

    /** Remplace les textes par un messages.yml déjà lu sans erreur. */
    public void apply(YamlConfiguration loaded) {
        messages = loaded;
    }

    /** Texte brut (codes &) avec ses remplacements, par paires : raw("cle", "%a%", "1"). */
    public String raw(String key, String... replacements) {
        String text = messages.getString(key);
        if (text == null) {
            plugin.getLogger().warning("Message manquant dans messages.yml : " + key);
            return key;
        }
        return replace(text, replacements);
    }

    public List<String> rawList(String key, String... replacements) {
        List<String> lines = new ArrayList<>();
        for (String line : messages.getStringList(key)) lines.add(replace(line, replacements));
        return lines;
    }

    public Component get(String key, String... replacements) {
        return AMPERSAND.deserialize(raw(key, replacements));
    }

    /** Envoie un message ; un message vide dans messages.yml n'est pas envoyé. */
    public void send(CommandSender target, String key, String... replacements) {
        String text = raw(key, replacements);
        if (target != null && !text.isEmpty()) target.sendMessage(AMPERSAND.deserialize(text));
    }

    /** Nom d'objet de menu, jamais en italique. */
    public Component itemName(String key, String... replacements) {
        return item(raw(key, replacements));
    }

    /** Lignes de description d'un objet de menu, jamais en italique sauf code &o. */
    public List<Component> itemLore(String key, String... replacements) {
        List<Component> lines = new ArrayList<>();
        for (String line : rawList(key, replacements)) lines.add(item(line));
        return lines;
    }

    private static Component item(String raw) {
        return AMPERSAND.deserialize(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private static String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            if (replacements[i] != null && replacements[i + 1] != null) text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }
}
