package com.tropimon.randompvp;

import com.tropimon.randompvp.calc.SmogonDataLoader;
import com.tropimon.randompvp.client.CalcOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomPvpClient implements ClientModInitializer {
    public static final String MOD_ID = "randompvp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        SmogonDataLoader.charger();
        HudRenderCallback.EVENT.register(new CalcOverlay());
        com.tropimon.randompvp.pvp.PvpOverlay.INSTANCE.register();
        com.tropimon.randompvp.pvp.PvpDetector.INSTANCE.register();
        LOGGER.info("RandomPvp chargé - moteur de calcul de dégâts (format Simple uniquement)");
        com.tropimon.randompvp.calc.InferenceCoverageCheck.verifier();
    }
}
