package com.tropimon.randompvp.battle;

import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

public final class MoveUseTracker {

    private MoveUseTracker() {
    }

    /**
     * Paquets deja traites, pour ne jamais compter un message deux fois.
     *
     * Deux mixins alimentent ce tracker : BattleMessagePacketMixin au decodage
     * reseau, BattleMessageHandlerMixin a l'affichage. Le second peut etre
     * court-circuite par un mod qui remplace l'interface de combat (Cobblemon
     * Extended Battle UI), le premier non — on garde donc les deux points
     * d'entree, mais le MEME paquet passe par les deux. Sans ce garde-fou,
     * chaque capacite consommait 2 PP au lieu d'un (et 4 avec Pression).
     *
     * Comparaison par identite d'objet (==) et non par hachage : le paquet
     * decode est litteralement la meme instance que celle passee au handler,
     * et une egalite exacte evite tout faux positif qui ferait perdre un
     * paquet legitime. File bornee : on ne retient que les plus recents.
     */
    private static final java.util.ArrayDeque<Object> PAQUETS_TRAITES = new java.util.ArrayDeque<>();
    private static final int MEMOIRE_PAQUETS = 32;

    /**
     * Marque ce paquet comme traite et indique s'il l'avait deja ete.
     * Le premier des deux mixins a le voir gagne ; l'autre se retire.
     */
    public static synchronized boolean dejaTraite(Object paquet) {
        if (paquet == null) return false;
        for (Object vu : PAQUETS_TRAITES) {
            if (vu == paquet) return true;
        }
        PAQUETS_TRAITES.addLast(paquet);
        while (PAQUETS_TRAITES.size() > MEMOIRE_PAQUETS) {
            PAQUETS_TRAITES.removeFirst();
        }
        return false;
    }

    private static final String CLE_PREFIXE_COUP = "cobblemon.move.";
    private static final String CLE_UTILISE_COUP = "cobblemon.battle.used_move";
    private static final String CLE_UTILISE_COUP_SUR = "cobblemon.battle.used_move_on";
    private static final String CLE_PROPRIETAIRE = "cobblemon.battle.owned_pokemon";
    private static final String CLE_NOUVEAU_TOUR = "cobblemon.battle.turn";

    public record CoupDetecte(String showdownId, String proprietaire) {
    }

    /** Extrait le nom du propriétaire (Minecraft) depuis un arg owned_pokemon imbriqué. */
    private static String extraireProprietaire(Object arg) {
        if (!(arg instanceof Text texteArg) || !(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) return null;
        if (!CLE_PROPRIETAIRE.equals(sousContenu.getKey())) return null;
        Object[] sousArgs = sousContenu.getArgs();
        if (sousArgs.length > 0 && sousArgs[0] instanceof String nom) return nom;
        return null;
    }

    public static void traiterMessage(Text message) {
        if (message == null) return;

        // Diagnostic temporaire : capture la clé + les arguments de CHAQUE
        // message dans config/randompvp-messages-debug.txt. À retirer une
        // fois la vraie clé du changement de type (Protéen/Libéro) confirmée
        // par observation en jeu - actuellement une hypothèse non vérifiée.
        MessageDebugLogger.log(message);

        BoostTracker.traiterMessage(message);
        FieldTracker.traiterMessage(message);
        TypeTracker.traiterMessage(message);

        // Switch explicite (confirmé par observation réelle en jeu, format
        // "cobblemon.battle.switch.self"/".other") : reset immédiat et fiable
        // des trackers du camp concerné, en complément (pas remplacement) de
        // la détection indirecte par comparaison d'espèce déjà en place.
        if (!(message.getContent() instanceof TranslatableTextContent contenu)) return;
        String cle = contenu.getKey();

        if ("cobblemon.battle.switch.self".equals(cle)) {
            BoostTracker.reinitialiserJoueur();
            TypeTracker.reinitialiserJoueur();
            return;
        }
        if ("cobblemon.battle.switch.other".equals(cle)) {
            BoostTracker.reinitialiserAdversaire();
            TypeTracker.reinitialiserAdversaire();
            return;
        }

        if (CLE_NOUVEAU_TOUR.equals(cle)) {
            ObservationCollector.signalerNouveauTour();
            return;
        }

        // Confirmation DIRECTE et fiable par message explicite du jeu -
        // remplace/renforce les heuristiques par seuils de dégâts existantes.
        if ("cobblemon.battle.ability.generic".equals(cle)) {
            Object[] args = contenu.getArgs();
            if (args.length >= 2 && args[1] instanceof String talentAnglais) {
                String proprietaire = extraireProprietaire(args[0]);
                ObservationCollector.confirmerTalentParMessage(proprietaire, talentAnglais);
            }
            return;
        }
        if (cle.startsWith("cobblemon.battle.damage.")) {
            String source = cle.substring("cobblemon.battle.damage.".length());
            Object[] args = contenu.getArgs();
            if (args.length >= 2) {
                // arg[1] = le PORTEUR de la source de dégâts (celui qui inflige le recul)
                String porteur = extraireProprietaire(args[1]);
                if ("rockyhelmet".equals(source)) {
                    ObservationCollector.confirmerObjetParMessage(porteur, source);
                } else if ("ironbarbs".equals(source) || "roughskin".equals(source)) {
                    ObservationCollector.confirmerTalentParMessage(porteur, source);
                }
            }
            return;
        }
        if (cle.startsWith("cobblemon.battle.heal.") && !cle.endsWith(".generic")) {
            Object[] args = contenu.getArgs();
            if (args.length >= 2 && args[1] instanceof String objetAnglais) {
                String proprietaire = extraireProprietaire(args[0]);
                ObservationCollector.confirmerObjetParMessageNomAnglais(proprietaire, objetAnglais);
            }
            return;
        }

        if (!CLE_UTILISE_COUP.equals(cle) && !CLE_UTILISE_COUP_SUR.equals(cle)) return;

        String proprietaire = null;
        String coupId = null;

        for (Object arg : contenu.getArgs()) {
            if (!(arg instanceof Text texteArg) || !(texteArg.getContent() instanceof TranslatableTextContent sousContenu)) continue;
            String sousCle = sousContenu.getKey();
            if (sousCle == null) continue;
            if (sousCle.startsWith(CLE_PREFIXE_COUP)) {
                if (coupId == null) {
                    coupId = sousCle.substring(CLE_PREFIXE_COUP.length());
                }
            } else if (CLE_PROPRIETAIRE.equals(sousCle)) {
                if (proprietaire == null) {
                    Object[] sousArgs = sousContenu.getArgs();
                    if (sousArgs.length > 0 && sousArgs[0] instanceof String nom) {
                        proprietaire = nom;
                    }
                }
            }
        }

        if (coupId != null) {
            ObservationCollector.signalerCoupUtilise(new CoupDetecte(coupId, proprietaire));
        }
    }
}
