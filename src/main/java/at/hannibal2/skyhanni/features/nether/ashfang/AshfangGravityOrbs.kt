package at.hannibal2.skyhanni.features.nether.ashfang

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.events.LorenzTickEvent
import at.hannibal2.skyhanni.events.LorenzWorldChangeEvent
import at.hannibal2.skyhanni.features.damageindicator.BossType
import at.hannibal2.skyhanni.features.damageindicator.DamageIndicatorManager
import at.hannibal2.skyhanni.utils.*
import at.hannibal2.skyhanni.utils.EntityUtils.hasSkullTexture
import at.hannibal2.skyhanni.utils.RenderUtils.drawString
import net.minecraft.entity.item.EntityArmorStand
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.awt.Color

class AshfangGravityOrbs {

    private val texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV" +
            "0L3RleHR1cmUvMWE2OWNjZjdhZDkwNGM5YTg1MmVhMmZmM2Y1YjRlMjNhZGViZjcyZWQxMmQ1ZjI0Yjc4Y2UyZDQ0YjRhMiJ9fX0="
    private val orbs = mutableListOf<EntityArmorStand>()

    @SubscribeEvent
    fun onTick(event: LorenzTickEvent) {
        if (!isEnabled()) return

        EntityUtils.getEntities<EntityArmorStand>()
            .filter { it !in orbs && it.hasSkullTexture(texture) }
            .forEach { orbs.add(it) }
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (!isEnabled()) return

        val color = Color(SpecialColour.specialToChromaRGB(SkyHanniMod.feature.ashfang.gravityOrbsColor), true)
        val playerLocation = LocationUtils.playerLocation()
        for (orb in orbs) {
            if (orb.isDead) continue
            val orbLocation = orb.getLorenzVec()
            val center = orbLocation.add(-0.5, -2.0, -0.5)
            RenderUtils.drawCylinderInWorld(color, center.x, center.y, center.z, 3.5f, 4.5f, event.partialTicks)

            if (orbLocation.distance(playerLocation) < 15) {
                //TODO find way to dynamically change color
                event.drawString(orbLocation.add(0.0, 2.5, 0.0), "§cGravity Orb")
            }
        }
    }

    @SubscribeEvent
    fun onWorldChange(event: LorenzWorldChangeEvent) {
        orbs.clear()
    }

    private fun isEnabled(): Boolean {
        return LorenzUtils.inSkyBlock && SkyHanniMod.feature.ashfang.gravityOrbs &&
                DamageIndicatorManager.isBossSpawned(BossType.NETHER_ASHFANG)
    }
}