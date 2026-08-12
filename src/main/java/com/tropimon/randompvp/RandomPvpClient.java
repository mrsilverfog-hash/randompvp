package com.tropimon.randompvp;

import com.tropimon.randompvp.battle.RandomBattleGate;
import com.tropimon.randompvp.calc.SmogonDataLoader;
import com.tropimon.randompvp.client.CalcOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomPvpClient implements ClientModInitializer {
    public static final String MOD_ID = "randompvp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Touche d'activation du mode random battle. F8 par défaut, modifiable
     * dans Options > Contrôles > catégorie RandomPvp.
     */
    private static KeyBinding toucheBascule;

    @Override
    public void onInitializeClient() {
        SmogonDataLoader.charger();
        HudRenderCallback.EVENT.register(new CalcOverlay());
        com.tropimon.randompvp.pvp.PvpOverlay.INSTANCE.register();
        com.tropimon.randompvp.pvp.PvpDetector.INSTANCE.register();
        com.tropimon.randompvp.calc.InferenceCoverageCheck.verifier();

        toucheBascule = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.randompvp.bascule",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "category.randompvp"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toucheBascule.wasPressed()) {
                RandomBattleGate.basculer();
                if (client.player != null) {
                    boolean actif = RandomBattleGate.estActif();
                    client.player.sendMessage(Text.literal(
                        actif
                            ? "[RandomPvp] Mode random battle ACTIF - calculs en 31 IV / 85 EV / nature neutre."
                            : "[RandomPvp] Mode random battle coupe. Aucun affichage."
                    ).formatted(actif ? Formatting.GREEN : Formatting.GRAY), false);
                }
            }
        });

        LOGGER.info("RandomPvp charge - inactif par defaut, touche RandomPvp (F8 par defaut) pour armer");
    }
}
