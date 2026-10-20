package com.tropimon.randompvp.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.tropimon.randompvp.RandomPvpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que le générateur random de Tropimon donne à chaque espèce, appris au
 * fil de tes combats : attaques et objets vus (adversaires et ta propre
 * équipe, qui est un échantillon sans biais du générateur).
 *
 * Ce ne sont PAS les sets d'un adversaire précis : rien ne relie deux combats
 * entre eux, on ne compte que combien de fois chaque attaque et chaque objet
 * sortent pour une espèce. Fichier : config/randompvp-sets-random.json.
 */
public final class StatsRandom {

    private StatsRandom() {
    }

    /** Pour une espèce : nombre de Pokémon vus, et compte par attaque / par objet. */
    static final class Espece {
        int vus = 0;
        Map<String, Integer> capacites = new HashMap<>();
        Map<String, Integer> objets = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Espece> donnees = null;

    private static Path fichier() {
        return FabricLoader.getInstance().getConfigDir().resolve("randompvp-sets-random.json");
    }

    private static synchronized Map<String, Espece> donnees() {
        if (donnees != null) return donnees;
        donnees = new HashMap<>();
        try {
            Path f = fichier();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    Map<String, Espece> lu = GSON.fromJson(r, new TypeToken<Map<String, Espece>>() { }.getType());
                    if (lu != null) donnees.putAll(lu);
                }
            }
        } catch (Exception e) {
            RandomPvpClient.LOGGER.warn("[RandomPvp] Statistiques random illisibles : {}", e.getMessage());
        }
        return donnees;
    }

    private static String cle(String espece) {
        return espece == null ? null : espece.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Un Pokémon de plus de cette espèce, avec ce qu'on sait de lui (objet null si inconnu). */
    static synchronized void enregistrer(String espece, Iterable<String> capacites, String objet) {
        String c = cle(espece);
        if (c == null || c.isEmpty()) return;
        Espece e = donnees().computeIfAbsent(c, k -> new Espece());
        e.vus++;
        if (capacites != null) {
            for (String m : capacites) {
                if (m != null && !m.isEmpty()) e.capacites.merge(m, 1, Integer::sum);
            }
        }
        if (objet != null && !objet.isEmpty()) e.objets.merge(objet, 1, Integer::sum);
    }

    static synchronized void sauvegarder() {
        if (donnees == null) return;
        try {
            Path f = fichier();
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(donnees, w);
            }
        } catch (Exception e) {
            RandomPvpClient.LOGGER.warn("[RandomPvp] Statistiques random non sauvegardées : {}", e.getMessage());
        }
    }

    /** Attaques déjà vues pour cette espèce, de la plus fréquente à la moins fréquente. */
    public static synchronized List<String> capacites(String espece) {
        Espece e = donnees().get(cle(espece));
        if (e == null) return List.of();
        List<Map.Entry<String, Integer>> l = new ArrayList<>(e.capacites.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        List<String> r = new ArrayList<>();
        for (Map.Entry<String, Integer> x : l) r.add(x.getKey());
        return r;
    }

    /** Objets déjà vus pour cette espèce (nom français -> nombre de fois). */
    static synchronized Map<String, Integer> objets(String espece) {
        Espece e = donnees().get(cle(espece));
        return e == null ? Map.of() : e.objets;
    }
}
