package com.tropimon.randompvp.battle;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger de diagnostic TEMPORAIRE, dédié à une seule question : quel signal
 * client permet de reconnaître une random battle d'un combat classé ?
 *
 * Il enregistre dans config/randompvp-detection-debug.txt :
 *   - le titre de chaque écran d'inventaire ouvert (les combats PvP du serveur
 *     passent par des menus : "Sélection de l'équipe" en classé, inconnu en
 *     random) ;
 *   - chaque message reçu en chat et en barre d'action ;
 *   - les transitions entrée/sortie de combat Cobblemon.
 *
 * Actif en permanence, indépendamment de RandomBattleGate — c'est justement
 * quand le mod est désarmé qu'on a besoin de voir passer le signal.
 *
 * À RETIRER une fois la détection automatique implémentée.
 */
public final class DetectionDebugLogger {

    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long TAILLE_MAX = 500_000L;

    private static boolean sature = false;
    private static boolean dernierEtatCombat = false;

    private DetectionDebugLogger() {
    }

    private static Path fichier() {
        return FabricLoader.getInstance().getConfigDir().resolve("randompvp-detection-debug.txt");
    }

    public static void ecran(String titre) {
        ecrire("ECRAN   | " + titre);
    }

    public static void message(String source, String texte) {
        if (texte == null || texte.isBlank()) return;
        ecrire("MSG " + source + " | " + texte);
    }

    /** Appelé chaque tick : journalise les transitions de combat uniquement. */
    public static void tickCombat() {
        boolean enCombat = BattleStateTracker.estEnCombat();
        if (enCombat != dernierEtatCombat) {
            dernierEtatCombat = enCombat;
            ecrire("COMBAT  | " + (enCombat ? "DEBUT" : "FIN"));
        }
    }

    private static synchronized void ecrire(String ligne) {
        if (sature) return;
        try {
            Path f = fichier();
            if (Files.exists(f) && Files.size(f) > TAILLE_MAX) {
                sature = true;
                return;
            }
            String s = "[" + LocalTime.now().format(HEURE) + "] " + ligne + System.lineSeparator();
            Files.writeString(f, s, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
