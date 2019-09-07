package mnm.mods.tabbychat.client.gui

import com.mojang.blaze3d.platform.GlStateManager
import mnm.mods.tabbychat.api.Channel
import mnm.mods.tabbychat.api.ChannelStatus
import mnm.mods.tabbychat.client.AbstractChannel
import mnm.mods.tabbychat.client.DefaultChannel
import mnm.mods.tabbychat.client.TabbyChatClient
import mnm.mods.tabbychat.client.gui.component.GuiComponent
import mnm.mods.tabbychat.client.gui.component.GuiPanel
import mnm.mods.tabbychat.client.gui.component.layout.BorderLayout
import mnm.mods.tabbychat.client.gui.component.layout.FlowLayout
import mnm.mods.tabbychat.util.*
import mnm.mods.tabbychat.util.config.Value

class ChatTray internal constructor() : GuiPanel(BorderLayout()) {

    private val tabList = GuiPanel(FlowLayout())
    private val handle = ChatHandle()

    private val map = HashMap<Channel, GuiComponent>()

    override var minimumSize: Dim
        get() = tabList.layout?.layoutSize ?: super.minimumSize
        set(value) {
            super.minimumSize = value
        }

    init {
        this.add(tabList, BorderLayout.Position.CENTER)
        val controls = ChatPanel(FlowLayout())
        controls.add(ToggleButton(), null)
        controls.add<GuiComponent>(handle, null)
        this.add(controls, BorderLayout.Position.EAST)

    }

    override fun render(mouseX: Int, mouseY: Int, parTicks: Float) {
        if (mc.ingameGUI.chatGUI.chatOpen) {
            GlStateManager.enableBlend()
            GlStateManager.color4f(1f, 1f, 1f, mc.gameSettings.chatOpacity.toFloat())
            drawModalCorners(MODAL)
            GlStateManager.disableBlend()
        }
        super.render(mouseX, mouseY, parTicks)
    }

    override fun tick() {
        super.tick()
        val panel = parent
        if (panel != null) {
            val (red, green, blue, alpha) = panel.secondaryColorProperty
            secondaryColor = Color(red, green, blue, alpha / 4 * 3)
        }
    }

    fun addChannel(channel: AbstractChannel) {
        val gc = ChatTab(channel)
        map[channel] = gc
        tabList.add<GuiComponent>(gc, null)
    }

    fun removeChannel(channel: Channel) {
        if (channel in map) {
            val gc = map.remove(channel)!!
            this.tabList.remove(gc)
        }
    }

    fun clearMessages() {
        this.tabList.clear()

        addChannel(DefaultChannel)
        ChatBox.status[DefaultChannel] = ChannelStatus.ACTIVE
    }

    internal fun isHandleHovered(x: Double, y: Double): Boolean {
        return handle.location.contains(x, y)
    }

    private class ToggleButton internal constructor() : GuiComponent() {

        private val value: Value<Boolean> = TabbyChatClient.settings.advanced.keepChatOpen

        override var location: ILocation
            get() = super.location.copy().move(0, 2)
            set(value) {
                super.location = value
            }

        override var minimumSize: Dim
            get() = Dim(8, 8)
            set(value) {
                super.minimumSize = value
            }

        override fun render(mouseX: Int, mouseY: Int, parTicks: Float) {
            GlStateManager.enableBlend()
            val loc = location
            val opac = (mc.gameSettings.chatOpacity * 255).toInt() shl 24
            renderBorders(loc.xPos + 2, loc.yPos + 2, loc.xWidth - 2, loc.yHeight - 2, 0x999999 or opac)
            if (value.value) {
                fill(loc.xPos + 3, loc.yPos + 3, loc.xWidth - 3, loc.yHeight - 3, 0xaaaaaa or opac)
            }
        }

        override fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
            if (location.contains(x, y) && button == 0) {
                value.value = !value.value
                return true
            }
            return false
        }
    }

    companion object {
        private val MODAL = TexturedModal(ChatBox.GUI_LOCATION, 0, 14, 254, 202)
    }
}
