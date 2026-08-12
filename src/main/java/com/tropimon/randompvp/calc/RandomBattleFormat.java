package com.tropimon.randompvp.calc;

/**
 * Constantes du format random battle de Tropimon.
 *
 * En random battle, tous les Pokémon (les tiens comme ceux de l'adversaire)
 * partagent le même profil de stats : 31 IV et 85 EV dans chaque statistique,
 * nature neutre. Seul le NIVEAU varie d'un Pokémon à l'autre (typiquement
 * entre 78 et 98) — il est lu depuis Cobblemon et reste la seule inconnue
 * légitime du calcul de stats.
 *
 * C'est LA différence avec TropiCalc, qui doit deviner les EV/nature adverses
 * via les sets Smogon et le moteur d'inférence. Ici il n'y a rien à deviner :
 * on force ces valeurs partout, ce qui rend les stats adverses exactes plutôt
 * qu'estimées.
 */
public final class RandomBattleFormat {

    /** EV appliqués à chaque statistique en random battle. */
    public static final int EV = 85;

    /** IV appliqués à chaque statistique en random battle. */
    public static final int IV = 31;

    /** Nature neutre (aucun multiplicateur de stat). */
    public static final Nature NATURE = Nature.HARDI;

    private RandomBattleFormat() {
    }

    /**
     * Force le profil random battle sur un builder, quelles que soient les
     * valeurs déjà posées. À appeler en DERNIER sur tout ce qui touche aux
     * IV/EV/nature, pour qu'aucune estimation ne puisse repasser derrière.
     */
    public static void appliquer(Pokemon.Builder b) {
        for (Stat s : Stat.values()) {
            b.iv(s, IV);
            b.ev(s, EV);
        }
        b.nature(NATURE);
    }
}
