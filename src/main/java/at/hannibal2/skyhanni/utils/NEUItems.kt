package at.hannibal2.skyhanni.utils

import at.hannibal2.skyhanni.config.ConfigManager
import at.hannibal2.skyhanni.test.command.CopyErrorCommand
import at.hannibal2.skyhanni.utils.ItemBlink.checkBlinkItem
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.NumberUtil.romanToDecimal
import at.hannibal2.skyhanni.utils.StringUtils.matchMatcher
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.github.moulberry.notenoughupdates.NEUManager
import io.github.moulberry.notenoughupdates.NEUOverlay
import io.github.moulberry.notenoughupdates.NotEnoughUpdates
import io.github.moulberry.notenoughupdates.overlays.AuctionSearchOverlay
import io.github.moulberry.notenoughupdates.overlays.BazaarSearchOverlay
import io.github.moulberry.notenoughupdates.recipes.CraftingRecipe
import io.github.moulberry.notenoughupdates.recipes.NeuRecipe
import io.github.moulberry.notenoughupdates.util.ItemResolutionQuery
import io.github.moulberry.notenoughupdates.util.Utils
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.util.regex.Pattern

object NEUItems {
    val manager: NEUManager get() = NotEnoughUpdates.INSTANCE.manager
    private val itemNameCache = mutableMapOf<String, NEUInternalName>() // item name -> internal name
    private val multiplierCache = mutableMapOf<String, Pair<String, Int>>()
    private val recipesCache = mutableMapOf<String, Set<NeuRecipe>>()
    private val enchantmentNamePattern = Pattern.compile("^(?<format>(?:§.)+)(?<name>[^§]+) (?<level>[IVXL]+)$")
    var allItemsCache = mapOf<String, NEUInternalName>() // item name -> internal name
    var allInternalNames = mutableListOf<NEUInternalName>()

    private val fallbackItem by lazy {
        Utils.createItemStack(
            ItemStack(Blocks.barrier).item,
            "§cMissing Repo Item",
            "§cYour NEU repo seems to be out of date"
        )
    }

    fun getInternalName(itemName: String): String {
        return getInternalNameOrNull_new(itemName)?.asString() ?: throw Error("getInternalName is null for '$itemName'")
    }

    fun getInternalName_new(itemName: String): NEUInternalName {
        return getInternalNameOrNull_new(itemName) ?: throw Error("getInternalName is null for '$itemName'")
    }

    fun getInternalNameOrNullIgnoreCase(itemName: String): NEUInternalName? {
        val lowercase = itemName.removeColor().lowercase()
        if (itemNameCache.containsKey(lowercase)) {
            return itemNameCache[lowercase]!!
        }

        if (allItemsCache.isEmpty()) {
            allItemsCache = readAllNeuItems()
        }
        allItemsCache[lowercase]?.let {
            itemNameCache[lowercase] = it
            return it
        }

        return null
    }

    fun readAllNeuItems(): Map<String, NEUInternalName> {
        allInternalNames.clear()
        val map = mutableMapOf<String, NEUInternalName>()
        for (internalNameRaw in manager.itemInformation.keys) {
            val name = manager.createItem(internalNameRaw).displayName.removeColor().lowercase()
            val internalName = NEUInternalName.from(internalNameRaw)
            map[name] = internalName
            allInternalNames.add(internalName)
        }
        return map
    }

    fun getInternalNameOrNull(itemName: String) = getInternalNameOrNull_new(itemName)?.asString()

    fun getInternalNameOrNull_new(itemName: String): NEUInternalName? {
        val lowercase = itemName.lowercase()
        if (itemNameCache.containsKey(lowercase)) {
            return itemNameCache[lowercase]!!
        }

        if (itemName == "§cmissing repo item") {
            val name = NEUInternalName.from("MISSING_ITEM")
            itemNameCache[lowercase] = name
            return name
        }

        resolveEnchantmentByName(itemName)?.let {
            val enchantmentName = fixEnchantmentName(it)
            itemNameCache[itemName] = enchantmentName
            return enchantmentName
        }
        val internalNameRaw = ItemResolutionQuery.findInternalNameByDisplayName(itemName, false) ?: return null

        // This fixes a NEU bug with §9Hay Bale (cosmetic item)
        // TODO remove workaround when this is fixed in neu
        val internalName = if (internalNameRaw == "HAY_BALE") {
            NEUInternalName.from("HAY_BLOCK")
        } else {
            NEUInternalName.from(internalNameRaw)
        }

        itemNameCache[lowercase] = internalName
        return internalName
    }

    private fun fixEnchantmentName(originalName: String): NEUInternalName {
        // Workaround for duplex
        "ULTIMATE_DUPLEX;(?<tier>.*)".toPattern().matchMatcher(originalName) {
            val tier = group("tier")
            return NEUInternalName.from("ULTIMATE_REITERATE;$tier")
        }

        return NEUInternalName.from(originalName)
    }

    private fun turboCheck(text: String): String {
        if (text == "Turbo-Cocoa") return "Turbo-Coco"
        if (text == "Turbo-Cacti") return "Turbo-Cactus"
        return text
    }

    fun getInternalName(itemStack: ItemStack) = ItemResolutionQuery(manager)
        .withCurrentGuiContext()
        .withItemStack(itemStack)
        .resolveInternalName() ?: ""

    fun getInternalNameOrNull(nbt: NBTTagCompound) =
        ItemResolutionQuery(manager).withItemNBT(nbt).resolveInternalName()

    fun getPriceOrNull(internalName: String, useSellingPrice: Boolean = false): Double? {
        val price = getPrice(internalName, useSellingPrice)
        if (price == -1.0) {
            return null
        }
        return price
    }

    fun getPrice(internalName: NEUInternalName): Double {
        if (internalName.asString() == "WISP_POTION") {
            return 20_000.0
        }
        return getPrice(internalName, false)
    }

    fun getPrice(internalName: String) = getPrice(NEUInternalName.from(internalName))

    fun transHypixelNameToInternalName(hypixelId: String): NEUInternalName {
        val name = manager.auctionManager.transformHypixelBazaarToNEUItemId(hypixelId)
        return NEUInternalName.from(name)
    }

    fun getPrice(internalName: NEUInternalName, useSellingPrice: Boolean): Double {
        val result = manager.auctionManager.getBazaarOrBin(internalName.asString(), useSellingPrice)
        if (result == -1.0) {
            if (internalName.asString() == "JACK_O_LANTERN") {
                return getPrice("PUMPKIN", useSellingPrice) + 1
            }
            if (internalName.asString() == "GOLDEN_CARROT") {
                // 6.8 for some players
                return 7.0 // NPC price
            }
        }
        return result
    }

    fun getPrice(internalName: String, useSellingPrice: Boolean): Double {
        return getPrice(NEUInternalName.from(internalName), useSellingPrice)
    }

    fun getItemStackOrNull(internalName: NEUInternalName) = ItemResolutionQuery(manager)
        .withKnownInternalName(internalName.asString())
        .resolveToItemStack()?.copy()

    fun getItemStackOrNull(internalName: String) = getItemStackOrNull(NEUInternalName.from(internalName))

    fun getItemStack(internalName: String, definite: Boolean = false): ItemStack =
        getItemStack(NEUInternalName.from(internalName), definite)

    fun getItemStack(internalName: NEUInternalName, definite: Boolean = false): ItemStack =
        getItemStackOrNull(internalName) ?: run {
            if (definite) {
                Utils.showOutdatedRepoNotification()
            }
            CopyErrorCommand.logError(
                IllegalStateException("Something went wrong!"),
                "Encountered an error getting the item for §7$internalName§c. " +
                        "This may be because your NEU repo is outdated. Please ask in the SkyHanni " +
                        "Discord if this is the case"
            )
            fallbackItem
        }

    fun isVanillaItem(item: ItemStack) = manager.auctionManager.isVanillaItem(item.getInternalName())

    fun ItemStack.renderOnScreen(x: Float, y: Float, scaleMultiplier: Double = 1.0) {
        val item = checkBlinkItem()
        val isSkull = item.item === Items.skull

        val baseScale = (if (isSkull) 0.8f else 0.6f)
        val finalScale = baseScale * scaleMultiplier
        val diff = ((finalScale - baseScale) * 10).toFloat()

        val translateX: Float
        val translateY: Float
        if (isSkull) {
            translateX = x - 2 - diff
            translateY = y - 2 - diff
        } else {
            translateX = x - diff
            translateY = y - diff
        }

        GlStateManager.pushMatrix()

        GlStateManager.translate(translateX, translateY, 1F)
        GlStateManager.scale(finalScale, finalScale, 1.0)

        RenderHelper.enableGUIStandardItemLighting()
        Minecraft.getMinecraft().renderItem.renderItemIntoGUI(item, 0, 0)
        RenderHelper.disableStandardItemLighting()

        GlStateManager.popMatrix()
    }

    fun getMultiplier(internalName: String, tryCount: Int = 0): Pair<String, Int> {
        if (multiplierCache.contains(internalName)) {
            return multiplierCache[internalName]!!
        }
        if (tryCount == 10) {
            val message = "Error reading getMultiplier for item '$internalName'"
            Error(message).printStackTrace()
            LorenzUtils.error(message)
            return Pair(internalName, 1)
        }
        for (recipe in getRecipes(internalName)) {
            if (recipe !is CraftingRecipe) continue

            val map = mutableMapOf<String, Int>()
            for (ingredient in recipe.ingredients) {
                val count = ingredient.count.toInt()
                var internalItemId = ingredient.internalItemId
                // ignore cactus green
                if (internalName == "ENCHANTED_CACTUS_GREEN") {
                    if (internalItemId == "INK_SACK-2") {
                        internalItemId = "CACTUS"
                    }
                }

                // ignore wheat in enchanted cookie
                if (internalName == "ENCHANTED_COOKIE") {
                    if (internalItemId == "WHEAT") {
                        continue
                    }
                }

                // ignore golden carrot in enchanted golden carrot
                if (internalName == "ENCHANTED_GOLDEN_CARROT") {
                    if (internalItemId == "GOLDEN_CARROT") {
                        continue
                    }
                }

                // ignore rabbit hide in leather
                if (internalName == "LEATHER") {
                    if (internalItemId == "RABBIT_HIDE") {
                        continue
                    }
                }

//                println("")
//                println("rawId: $rawId")
//                println("internalItemId: $internalItemId")

                val old = map.getOrDefault(internalItemId, 0)
                map[internalItemId] = old + count
            }
            if (map.size != 1) continue
            val current = map.iterator().next().toPair()
            val id = current.first
            return if (current.second > 1) {
                val child = getMultiplier(id, tryCount + 1)
                val result = Pair(child.first, child.second * current.second)
                multiplierCache[internalName] = result
                result
            } else {
                Pair(internalName, 1)
            }
        }

        val result = Pair(internalName, 1)
        multiplierCache[internalName] = result
        return result
    }

    fun getRecipes(minionId: String): Set<NeuRecipe> {
        if (recipesCache.contains(minionId)) {
            return recipesCache[minionId]!!
        }
        val recipes = manager.getRecipesFor(minionId)
        recipesCache[minionId] = recipes
        return recipes
    }

    fun neuHasFocus(): Boolean {
        if (AuctionSearchOverlay.shouldReplace()) return true
        if (BazaarSearchOverlay.shouldReplace()) return true
        if (InventoryUtils.inStorage()) return true
        if (NEUOverlay.searchBarHasFocus) return true

        return false
    }

    // Taken and edited from NEU
    private fun resolveEnchantmentByName(enchantmentName: String): String? {
        return enchantmentNamePattern.matchMatcher(enchantmentName) {
            val name = group("name").trim { it <= ' ' }
            val ultimate = group("format").lowercase().contains("§l")
            ((if (ultimate && name != "Ultimate Wise") "ULTIMATE_" else "")
                    + turboCheck(name).replace(" ", "_").replace("-", "_").uppercase()
                    + ";" + group("level").romanToDecimal())
        }
    }

    //Uses NEU
    fun saveNBTData(item: ItemStack, removeLore: Boolean = true): String {
        val jsonObject = manager.getJsonForItem(item)
        if (!jsonObject.has("internalname")) {
            jsonObject.add("internalname", JsonPrimitive("_"))
        }
        if (removeLore) {
            if (jsonObject.has("lore")) jsonObject.remove("lore")
        }
        val jsonString = jsonObject.toString()
        return StringUtils.encodeBase64(jsonString)
    }

    fun loadNBTData(encoded: String): ItemStack {
        val jsonString = StringUtils.decodeBase64(encoded)
        val jsonObject = ConfigManager.gson.fromJson(jsonString, JsonObject::class.java)
        return manager.jsonToStack(jsonObject, false)
    }
}