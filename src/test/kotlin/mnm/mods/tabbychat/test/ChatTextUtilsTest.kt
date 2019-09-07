package mnm.mods.tabbychat.test

import mnm.mods.tabbychat.util.ChatTextUtils
import mnm.mods.tabbychat.util.style
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.StringTextComponent
import net.minecraft.util.text.TextFormatting
import net.minecraft.util.text.event.ClickEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTextUtilsTest {

    @Test
    fun testSubChat() {
        assertEquals(makeChat(false), ChatTextUtils.subChat(makeChat(true), 7))
    }

    private fun makeChat(tag: Boolean): ITextComponent {

        return StringTextComponent(if (tag) "[test] " else "").style { bold = true }
                .appendSibling(StringTextComponent("This should be green").style { color = TextFormatting.GREEN })
                .appendText(" ")
                .appendSibling(StringTextComponent("This is a link.").style {
                    clickEvent = ClickEvent(ClickEvent.Action.OPEN_URL, "http://google.com")
                })

    }
}
