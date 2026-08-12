package com.tropimon.randompvp.battle;

import com.tropimon.randompvp.RandomPvpClient;

/**
 * Interrupteur général du mod.
 *
 * RandomPvp calcule tout en supposant 31 IV / 85 EV / nature neutre. Ces
 * valeurs ne sont vraies QU'EN random battle : appliquées à un combat classé
 * ou à un combat sauvage, elles produisent des chiffres faux sans le dire.
 *
 * Le mod est donc **inactif par défaut** et ne fait strictement rien tant qu'il
 * n'a pas été armé explicitement. Aucun overlay, aucune lecture de message de
 * combat, aucune accumulation d'observation.
 *
 * L'état n'est PAS persisté : à chaque lancement du jeu, le mod repart désarmé.
 * C'est volontaire — un état sauvegardé qui traîne d'une session à l'autre est
 * exactement le scénario où le mod s'activerait sur un combat classé sans que
 * personne le remarque.
 *
 * La détection automatique du format viendra remplacer l'armement manuel une
 * fois le signal identifié (voir DetectionDebugLogger).
 */
public final class RandomBattleGate {

    private static volatile boolean arme = false;

    private RandomBattleGate() {
    }

    /** Vrai si le mod a le droit d'agir. Tout point d'entrée doit tester ceci. */
    public static boolean estActif() {
        return arme;
    }

    public static void armer(boolean valeur) {
        if (arme == valeur) return;
        arme = valeur;
        if (!arme) {
            // Désarmement : on repart d'une ardoise vierge, pour qu'un futur
            // combat random ne réutilise pas des observations d'un autre format.
            try {
                ObservationCollector.reinitialiser();
            } catch (Throwable e) {
                RandomPvpClient.LOGGER.warn("[RandomPvp] Reset à l'extinction impossible : {}", e.getMessage());
            }
        }
        RandomPvpClient.LOGGER.info("[RandomPvp] Mode random battle : {}", arme ? "ACTIF" : "inactif");
    }

    public static void basculer() {
        armer(!arme);
    }
}
