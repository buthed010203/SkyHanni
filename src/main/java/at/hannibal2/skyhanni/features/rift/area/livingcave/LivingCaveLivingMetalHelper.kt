package at.hannibal2.skyhanni.features.rift.area.livingcave

import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.events.BlockClickEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.ServerBlockChangeEvent
import at.hannibal2.skyhanni.events.TitleReceivedEvent
import at.hannibal2.skyhanni.features.rift.RiftAPI
import at.hannibal2.skyhanni.test.GriffinUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.LorenzVec
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class LivingCaveLivingMetalHelper {
    private val config get() = RiftAPI.config.area.livingCaveConfig.livingCaveLivingMetalConfig
    private var lastClicked: LorenzVec? = null
    private var pair: Pair<LorenzVec, LorenzVec>? = null
    private var startTime = 0L

    @SubscribeEvent
    fun onBlockClick(event: BlockClickEvent) {
        if (!isEnabled()) return
        if (event.clickType == ClickType.LEFT_CLICK) {
            val name = event.getBlockState.block.toString()
            if (name.contains("lapis_ore")) {
                lastClicked = event.position
            }
        }
    }

    @SubscribeEvent
    fun onBlockChange(event: ServerBlockChangeEvent) {
        if (!isEnabled()) return
        val location = event.location
        if (location.distanceToPlayer() >= 7) return

        if (event.old == "lapis_ore") {
            pair?.let {
                if (it.second == location) {
                    pair = null
                }
            }
        }

        if (event.new != "lapis_ore") return

        lastClicked?.let {
            val distance = location.distance(it)
            if (distance < 2) {
                pair = Pair(it, location)
                startTime = System.currentTimeMillis()
            }
        }
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!isEnabled()) return
        val (a, b) = pair ?: return
        if (System.currentTimeMillis() > startTime + 5_000) return

        val maxTime = 500
        val diff = startTime + maxTime - System.currentTimeMillis()
        val location = if (diff > 0) {
            val percentage = diff.toDouble() / maxTime
            a.slope(b, 1 - percentage)
        } else b
        event.drawWaypointFilled(location, LorenzColor.AQUA.toColor(), seeThroughBlocks = location.distanceToPlayer() < 10)
    }

    @SubscribeEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!isEnabled()) return
        if (!config.hideParticles) return

        pair?.let {
            if (it.second.distance(event.location) < 3) {
                event.isCanceled = true
            }
        }
    }

    @SubscribeEvent
    fun onTitleReceived(event: TitleReceivedEvent) {
        if (!isEnabled()) return
        if (event.title.contains("Living Metal")) {
            pair = null
        }
    }

    fun isEnabled() = RiftAPI.inRift() && (RiftAPI.inLivingCave() || RiftAPI.inLivingStillness()) && config.enabled
}
