package com.tropimon.randompvp.mixin;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;
import com.tropimon.randompvp.battle.MoveUseTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepte les messages de combat Cobblemon en lecture seule, juste pour
 * en extraire l'identifiant du coup utilisé (via MoveUseTracker) avant que
 * le texte ne soit affiché.
 */
@Mixin(BattleMessageHandler.class)
public class BattleMessageHandlerMixin {

    @Inject(method = "handle", at = @At("HEAD"))
    private void randompvp$onBattleMessage(BattleMessagePacket packet, MinecraftClient client, CallbackInfo ci) {
        if (packet == null) return;
        if (!com.tropimon.randompvp.battle.RandomBattleGate.estActif()) return;
        if (MoveUseTracker.dejaTraite(packet)) return;

        try {
            for (Text message : packet.getMessages()) {
                MoveUseTracker.traiterMessage(message);
            }
        } catch (Throwable erreur) {
            com.tropimon.randompvp.RandomPvpClient.LOGGER.error("[RandomPvp-diag] Exception dans le Mixin", erreur);
        }
    }
}
