package com.tropimon.randompvp;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Bascule manuelle du mod (F8 par défaut, modifiable dans Options > Contrôles).
 *
 * RandomPvp calcule tout en supposant 31 IV / 85 EV / nature neutre. Ces
 * valeurs ne sont vraies QU'EN random battle : appliquées à un combat classé
 * ou sauvage, elles produiraient des chiffres faux sans le signaler. Le mod
 * démarre donc DÉSACTIVÉ, contrairement à TropiCalc.
 *
 * L'état n'est pas persisté : chaque lancement du jeu repart désactivé. Un
 * état sauvegardé qui traîne d'une session à l'autre est exactement le
 * scénario où le mod s'allumerait sur un classé sans qu'on le remarque.
 */
public final class ModToggle {

    private ModToggle() {
    }

    private static boolean actif = false;
    private static KeyBinding touche;
    private static KeyBinding toucheAnnuler;

    /** Nom de la touche d'annulation tel que configuré par le joueur (F7 par défaut). */
    public static String nomToucheAnnuler() {
        try {
            return toucheAnnuler.getBoundKeyLocalizedText().getString();
        } catch (Exception e) {
            return "F7";
        }
    }

    public static boolean estActif() {
        return actif;
    }

    public static void enregistrer() {
        touche = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.randompvp.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.randompvp"
        ));

        // Annule la confirmation d'objet de l'adversaire actif (faux positif) :
        // elle ne pourra plus revenir pendant ce combat.
        toucheAnnuler = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.randompvp.annuler_objet",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.randompvp"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toucheAnnuler.wasPressed()) {
                String msg = com.tropimon.randompvp.battle.ObservationCollector.annulerConfirmationAdversaire();
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("§e[RandomPvp] " + msg), true);
                }
            }
            while (touche.wasPressed()) {
                actif = !actif;
                if (!actif) {
                    // Repartir d'une ardoise vierge : aucune observation d'un
                    // autre format ne doit survivre a l'extinction.
                    try {
                        com.tropimon.randompvp.battle.ObservationCollector.reinitialiser();
                    } catch (Throwable ignore) {
                    }
                }
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal(actif
                            ? "§a[RandomPvp] Mode random battle activé (31 IV / 85 EV / nature neutre)"
                            : "§c[RandomPvp] Désactivé"),
                        true
                    );
                }
            }
        });
    }
}
