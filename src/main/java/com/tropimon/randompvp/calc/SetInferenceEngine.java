package com.tropimon.randompvp.calc;

import java.util.Set;

public final class SetInferenceEngine {

    private SetInferenceEngine() {
    }

    public static final Set<String> OBJETS_OFFENSIFS = Set.of(
        "Bandeau Choix", "Lunettes Choix", "Mouchoir Choix", "Orbe Vie", "Ceinture Pro",
        "Bandeau Muscles", "Lunettes Savantes", "Gant de Boxe"
    );

    public static final Set<String> TALENTS_OFFENSIFS = Set.of(
        "Cran", "Agitation", "Adaptabilité", "Technicien", "Poing de Fer",
        "Mâchoire Brute", "Force Sable", "Verres Teintés", "Sans Limite", "Télécharge",
        "Coloforce", "Force Pure", "Griffe Dure", "Rage Poison", "Rage Brûlure",
        "Pouls Orichalque", "Moteur Hadron", "Dent de Dragon", "Œil Révélateur",
        "Aquabulle", "Porte-Roche", "Seigneur Suprême", "Tranchant", "Transistor",
        "Infiltration"
    );

    public static final Set<String> OBJETS_DEFENSIFS = Set.of(
        "Veste de Combat", "Évoluroc", "Grosses Bottes", "Restes", "Boue Noire",
        "Baie Sitrus", "Baie Agava", "Baie Iapapa", "Baie Wiki", "Baie Mago", "Casque Brut",
        "Ceinture Focus", "Garde-Talent"
    );

    public static final Set<String> TALENTS_DEFENSIFS = Set.of(
        "Isograisse", "Filtre", "Solide Roc", "Multi-écailles", "Spectro-Bouclier",
        "Régé-Force", "Médic Nature", "Sel Purificateur", "Bien Cuit", "Prisme-Armure",
        "Absorb'Eau", "Absorb'Volt", "Absorbe-Terre", "Anti-Bruit", "Aquabulle",
        "Boule de Poils", "Garde Mystik", "Herbivore", "Lavabo", "Lucidité",
        "Lévitation", "Paratonnerre", "Pare-Balles", "Peau Sèche", "Toison Épaisse",
        "Torche", "Écailles Glacées", "Robuste", "Fantômasque"
    );

    public static void narrow(StatHypothesis hypothese, Stat statCible, boolean estStatAttaquant,
                               Pokemon pokemonInconnuPartiel, Pokemon pokemonConnu, Move capacite,
                               Field terrain, double pourcentageObserveMin, double pourcentageObserveMax) {

        Set<String> objetsCandidats = hypothese.objetsPossibles;
        Set<String> talentsCandidats = hypothese.talentsPossibles;

        java.util.Set<String> nouveauxObjets = new java.util.HashSet<>();
        java.util.Set<String> nouveauxTalents = new java.util.HashSet<>();
        boolean auMoinsUneCombinaisonValide = false;

        // Random battle : les EV (85) et la nature (neutre) sont connus, il n'y a
        // rien a enumerer de ce cote. L'inference ne fait plus varier que l'objet
        // et le talent — ce qui la rend plus tranchante : tout ecart de degats
        // residuel ne peut plus venir que de l'un des deux.
        for (String objet : avecAucun(objetsCandidats)) {
            for (String talent : avecAucun(talentsCandidats)) {

                Pokemon hypothetique =
                    construirePokemonHypothetique(pokemonInconnuPartiel, objet, talent);

                Pokemon attaquant = estStatAttaquant ? hypothetique : pokemonConnu;
                Pokemon defenseur = estStatAttaquant ? pokemonConnu : hypothetique;

                DamageCalculator.Resultat resultat =
                    DamageCalculator.calculer(attaquant, defenseur, capacite, terrain, null, false);

                if (resultat.immunise) continue;

                boolean chevauche = resultat.pourcentageMin <= pourcentageObserveMax
                    && resultat.pourcentageMax >= pourcentageObserveMin;

                if (chevauche) {
                    auMoinsUneCombinaisonValide = true;
                    nouveauxObjets.add(objet);
                    nouveauxTalents.add(talent);
                }
            }
        }

        if (!auMoinsUneCombinaisonValide) return;

        // evMin/evMax et les drapeaux de nature ne bougent plus : fixes a 85/neutre.
        hypothese.objetsPossibles.retainAll(nouveauxObjets);
        hypothese.talentsPossibles.retainAll(nouveauxTalents);
        hypothese.nombreObservations++;
    }

    private static java.util.Set<String> avecAucun(Set<String> base) {
        java.util.Set<String> resultat = new java.util.HashSet<>(base);
        resultat.add(StatHypothesis.AUCUN);
        return resultat;
    }
}
