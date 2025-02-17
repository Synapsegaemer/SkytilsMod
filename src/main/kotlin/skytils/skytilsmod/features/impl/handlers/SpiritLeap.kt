/*
 * Skytils - Hypixel Skyblock Quality of Life Mod
 * Copyright (C) 2022 Skytils
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package skytils.skytilsmod.features.impl.handlers

import com.google.gson.JsonObject
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.init.Items
import net.minecraft.inventory.ContainerChest
import net.minecraftforge.client.event.ClientChatReceivedEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import skytils.hylin.skyblock.dungeons.DungeonClass
import skytils.skytilsmod.Skytils
import skytils.skytilsmod.core.PersistentSave
import skytils.skytilsmod.events.impl.GuiContainerEvent
import skytils.skytilsmod.listeners.DungeonListener
import skytils.skytilsmod.utils.RenderUtil.highlight
import skytils.skytilsmod.utils.Utils
import skytils.skytilsmod.utils.graphics.ScreenRenderer
import skytils.skytilsmod.utils.graphics.SmartFontRenderer
import skytils.skytilsmod.utils.stripControlCodes
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.*

class SpiritLeap : PersistentSave(File(Skytils.modDir, "spiritleap.json")) {

    private val playerPattern = Regex("(?:\\[.+?] )?(?<name>\\w+)")
    private val doorOpenedPattern = Regex("^(?:\\[.+?] )?(?<name>\\w+) opened a WITHER door!$")
    private val bloodOpenedString = "§r§cThe §r§c§lBLOOD DOOR§r§c has been opened!§r"
    private var doorOpener: String? = null

    companion object {
        val names = HashMap<String, Boolean>()
        val classes = DungeonClass.values()
            .associateWithTo(EnumMap(DungeonClass::class.java)) { false }
        val shortenedNameCache = WeakHashMap<String, String>()
        val nameSlotCache = HashMap<Int, String>()
    }

    @SubscribeEvent
    fun onGuiDrawPost(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!Utils.inDungeons) return
        if (event.container is ContainerChest) {
            if ((Skytils.config.spiritLeapNames && event.chestName == "Spirit Leap") || (Skytils.config.reviveStoneNames && event.chestName == "Revive A Teammate") || (Skytils.config.ghostTeleportMenuNames && event.chestName == "Teleport to Player")) {
                val fr = ScreenRenderer.fontRenderer
                var people = 0
                GlStateManager.disableLighting()
                GlStateManager.enableBlend()
                for (slot in event.container.inventorySlots) {
                    if (slot.inventory == mc.thePlayer.inventory) continue
                    if (!slot.hasStack || slot.stack.item != Items.skull) continue
                    val item = slot.stack
                    people++

                    val x = slot.xDisplayPosition.toFloat()
                    val y = slot.yDisplayPosition + if (people % 2 != 0) -15f else 20f
                    val name = nameSlotCache[slot.slotNumber]
                    if (name == null || name == "Unknown") {
                        nameSlotCache[slot.slotNumber] =
                            playerPattern.find(item.displayName.stripControlCodes())?.groups?.get("name")?.value
                                ?: continue
                        continue
                    }
                    val teammate = DungeonListener.team[name] ?: continue
                    val dungeonClass = teammate.dungeonClass
                    val text = shortenedNameCache.getOrPut(name) {
                        fr.trimStringToWidth(item.displayName.substring(0, 2) + name, 32)
                    }
                    val scale = 0.9f
                    val scaleReset = 1 / scale
                    GlStateManager.pushMatrix()
                    if (name == doorOpener) {
                        slot highlight 1174394112
                    } else if (names.getOrDefault(name, false)) {
                        slot highlight 1174339584
                    } else if (classes.getOrDefault(dungeonClass, false)) {
                        slot highlight 1157693184
                    }
                    GlStateManager.translate(0f, 0f, 299f)
                    Gui.drawRect(
                        (x - 2 - fr.getStringWidth(text) / 2).toInt(),
                        (y - 2).toInt(),
                        (x + fr.getStringWidth(text) / 2 + 2).toInt(),
                        (y + fr.FONT_HEIGHT + 2).toInt(),
                        -13686744
                    )
                    fr.drawString(
                        text,
                        x,
                        y,
                        alignment = SmartFontRenderer.TextAlignment.MIDDLE,
                        shadow = SmartFontRenderer.TextShadow.OUTLINE
                    )
                    GlStateManager.scale(scale, scale, 1f)
                    fr.drawString(
                        dungeonClass.className.first().uppercase(),
                        scaleReset * x,
                        scaleReset * slot.yDisplayPosition,
                        -256,
                        true
                    )
                    GlStateManager.popMatrix()
                }
                GlStateManager.disableBlend()
            }
        }
    }

    @SubscribeEvent
    fun onChatReceived(event: ClientChatReceivedEvent) {
        if (!Skytils.config.highlightDoorOpener || !Utils.inDungeons || event.type == 2.toByte()) return
        doorOpener = if (event.message.formattedText == bloodOpenedString) null
        else (doorOpenedPattern.find(event.message.unformattedText)?.groups?.get("name")?.value ?: return)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        nameSlotCache.clear()
    }

    override fun read(reader: InputStreamReader) {
        val obj = gson.fromJson(reader, JsonObject::class.java)
        for (entry in obj["classes"].asJsonObject.entrySet()) {
            classes[DungeonClass.getClassFromName(entry.key)] =
                entry.value.asJsonObject["enabled"].asBoolean
        }
        for (entry in obj["users"].asJsonObject.entrySet()) {
            names[entry.key] =
                entry.value.asJsonObject["enabled"].asBoolean
        }
    }

    override fun write(writer: OutputStreamWriter) {
        val obj = JsonObject()
        val classes = JsonObject()
        for (dClass in Companion.classes) {
            val classObj = JsonObject()
            classObj.addProperty("enabled", dClass.value)
            classes.add(dClass.key.name, classObj)
        }

        val users = JsonObject()
        for (name in names) {
            val userObj = JsonObject()
            userObj.addProperty("enabled", name.value)
            users.add(name.key, userObj)
        }

        obj.add("classes", classes)
        obj.add("users", users)
        gson.toJson(obj, writer)
    }

    override fun setDefault(writer: OutputStreamWriter) {
        val e = JsonObject()
        e.add("classes", JsonObject())
        e.add("users", JsonObject())
        gson.toJson(e, writer)
    }
}