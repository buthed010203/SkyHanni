package at.hannibal2.skyhanni.features.event.diana

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.TitleUtils
import at.hannibal2.skyhanni.events.LorenzTickEvent
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.time.Duration.Companion.seconds

class GriffinPetWarning {
    private var lastWarnTime = SimpleTimeMark.farPast()

    @SubscribeEvent
    fun onTick(event: LorenzTickEvent) {
        if (!event.isMod(20)) return
        if (!IslandType.HUB.isInIsland()) return
        if (!SkyHanniMod.feature.diana.petWarning) return
        if (!DianaAPI.isRitualActive()) return
        if (!DianaAPI.hasSpadeInHand()) return

        if (!DianaAPI.hasGriffinPet()) {
            if (lastWarnTime.passedSince() > 30.seconds) {
                lastWarnTime = SimpleTimeMark.now()
                TitleUtils.sendTitle("§cGriffin Pet!", 3_000)
                LorenzUtils.chat("§e[SkyHanni] Reminder to use a Griffin pet for Mythological Ritual!")
            }
        }
    }
}
