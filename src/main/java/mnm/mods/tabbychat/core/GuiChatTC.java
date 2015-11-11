package mnm.mods.tabbychat.core;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.google.common.collect.Lists;
import com.google.common.collect.ObjectArrays;

import mnm.mods.tabbychat.ChatManager;
import mnm.mods.tabbychat.TabbyChat;
import mnm.mods.tabbychat.api.Channel;
import mnm.mods.tabbychat.util.BackgroundChatThread;
import mnm.mods.util.gui.GuiComponent;
import mnm.mods.util.gui.GuiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;

public class GuiChatTC extends GuiChat {

    protected Minecraft mc = Minecraft.getMinecraft();
    protected List<GuiComponent> componentList = Lists.newArrayList();
    protected GuiNewChatTC chatGui = GuiNewChatTC.getInstance();
    protected ChatManager chat;

    private String defaultInputFieldText;
    private int sentHistoryIndex;
    private String sentHistoryBuffer = "";

    protected GuiText textBox;

    private boolean waitingOnAutocomplete = false;
    private boolean playerNamesFound;
    private List<String> foundPlayerNames = Lists.newArrayList();
    private int autocompleteIndex;

    private boolean opened = false;

    private TabbyChat tc = TabbyChat.getInstance();

    public GuiChatTC() {
        this("");

    }

    public GuiChatTC(String text) {
        super(text);
        this.defaultInputFieldText = text;
        sentHistoryIndex = chatGui.getSentMessages().size();
        chat = chatGui.getChatManager();
        textBox = chat.getChatBox().getChatInput().getTextField();
        if (defaultInputFieldText.isEmpty()
                && !chat.getActiveChannel().isPrefixHidden()
                && !chat.getActiveChannel().getPrefix().isEmpty()) {
            defaultInputFieldText = chat.getActiveChannel().getPrefix() + " ";
        }

        this.componentList.add(chat.getChatBox());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        tc.getEventManager().onInitScreen(this);
        if (!opened) {
            textBox.setValue("");
            textBox.getTextField().writeText(defaultInputFieldText);
            this.opened = true;
            updateScreen();
        }
    }

    @Override
    public void updateScreen() {
        for (GuiComponent comp : this.componentList) {
            comp.updateComponent();
        }
        tc.getEventManager().onUpdateScreen();
    }

    @Override
    public void onGuiClosed() {
        tc.getEventManager().onCloseScreen();
        this.sentHistoryBuffer = "";
        for (GuiComponent comp : this.componentList) {
            comp.onClosed();
        }
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        for (GuiComponent comp : this.componentList) {
            comp.handleMouseInput();
        }
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        for (GuiComponent comp : this.componentList) {
            comp.handleKeyboardInput();
        }
    }

    @Override
    protected void keyTyped(char key, int code) {
        if (tc.getEventManager().onKeyTyped(key, code)) {
            return;
        }
        this.waitingOnAutocomplete = false;
        if (code != Keyboard.KEY_TAB) {
            this.playerNamesFound = false;
        }
        switch (code) {
        case Keyboard.KEY_RETURN:
        case Keyboard.KEY_NUMPADENTER:
            // send chat
            sendCurrentChat(false);
            break;
        case Keyboard.KEY_TAB:
            // auto-complete
            autocompletePlayerNames();
            break;
        case Keyboard.KEY_DOWN:
            // next send
            if (this.sentHistoryIndex < chatGui.getSentMessages().size() - 1) {
                this.sentHistoryIndex++;
                this.textBox.setValue(chatGui.getSentMessages().get(this.sentHistoryIndex));
            } else {
                this.sentHistoryIndex = chatGui.getSentMessages().size();
                this.textBox.setValue(sentHistoryBuffer);
            }
            break;
        case Keyboard.KEY_UP:
            // previous send
            if (this.sentHistoryIndex > 0) {
                this.sentHistoryIndex--;
                this.textBox.setValue(chatGui.getSentMessages().get(this.sentHistoryIndex));
            }
            break;
        case Keyboard.KEY_ESCAPE:
            // close chat
            mc.displayGuiScreen(null);
            break;
        case Keyboard.KEY_PRIOR:
            // Page up
            this.chatGui.getChatManager().getChatBox().getChatArea().scroll(chatGui.getLineCount() + 1);
            break;
        case Keyboard.KEY_NEXT:
            // Page down
            this.chatGui.getChatManager().getChatBox().getChatArea().scroll(-chatGui.getLineCount() - 1);
            break;
        default:
            // type
            this.textBox.getTextField().textboxKeyTyped(key, code);
        }
        if (code != Keyboard.KEY_UP && code != Keyboard.KEY_DOWN) {
            sentHistoryBuffer = textBox.getValue();
            sentHistoryIndex = chatGui.getSentMessages().size();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (tc.getEventManager().onMouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        if (mouseButton == 0) {
            IChatComponent chat = chatGui.getChatComponent(Mouse.getX(), Mouse.getY());
            this.handleComponentClick(chat);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float tick) {

        // Draw the components
        for (GuiComponent component : componentList) {
            if (component.isVisible()) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(component.getScale(), component.getScale(), 0);
                GlStateManager.translate(component.getBounds().x, component.getBounds().y, 0);
                component.drawComponent(mouseX, mouseY);
                GlStateManager.popMatrix();
            }
        }
        tc.getEventManager().onRenderChatScreen(mouseX, mouseY, tick);

        IChatComponent chat = chatGui.getChatComponent(Mouse.getX(), Mouse.getY());
        this.handleComponentHover(chat, mouseX, mouseY);

    }

    protected void sendCurrentChat(boolean keepOpen) {
        String message = this.textBox.getValue().trim();
        // send the outbound message to ChatSent modules.
        message = tc.getEventManager().onChatSent(message);
        Channel active = chat.getActiveChannel();
        String[] toSend = processSends(message, active.getPrefix(), active.isPrefixHidden());
        // time to wait between each send
        long wait = tc.settings.advanced.msgDelay.getValue();
        new BackgroundChatThread(this, toSend, wait).start();

        // add to sent chat manually
        chatGui.addToSentMessages(message);
        this.sentHistoryIndex = chatGui.getSentMessages().size();
        this.sentHistoryBuffer = "";

        chatGui.resetScroll();
        textBox.setValue("");

        if (!keepOpen) {
            mc.displayGuiScreen(null);
        } else {
            this.textBox.setValue("");
        }
    }

    public static String[] processSends(String msg, String prefix, boolean hidden) {
        if (StringUtils.isEmpty(msg)) {
            return null;
        }
        int len = 100 - prefix.length();
        if (!hidden && msg.startsWith(prefix)) {
            msg = msg.substring(prefix.length()).trim();
        }
        String[] sends = WordUtils.wrap(msg, len).split("\r?\n");

        // is command && (no prefix || not right prefix)
        if (sends[0].startsWith("/") && (StringUtils.isEmpty(prefix) || !sends[0].startsWith(prefix))) {
            // limit commands to 1 send.
            return new String[] { sends[0] };
        }
        if (StringUtils.isEmpty(prefix)) {
            return sends;
        }

        for (int i = 0; i < sends.length; i++) {
            sends[i] = prefix + " " + sends[i];
        }

        return sends;

    }

    private static String getCleanText(String dirty) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(dirty);
    }

    private void printListToChat(List<String> list, int id) {
        // TODO Pagination

        // Limit number of items to 50.
        if (list.size() > 50) {
            list = list.subList(0, 49);
        }
        StringBuilder sb = new StringBuilder();

        Iterator<String> iter = list.iterator();
        while (iter.hasNext()) {
            String next = iter.next();
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(next);
        }
        chatGui.printChatMessageWithOptionalDeletion(new ChatComponentText(sb.toString()), id);
    }

    @Override
    public void autocompletePlayerNames() {
        String s1;
        if (this.playerNamesFound) {
            this.textBox.getTextField().deleteFromCursor(this.textBox.getTextField().func_146197_a(-1,
                    this.textBox.getTextField().getCursorPosition(), false)
                    - this.textBox.getTextField().getCursorPosition());

            if (this.autocompleteIndex >= this.foundPlayerNames.size()) {
                this.autocompleteIndex = 0;
            }
        } else {
            int i = this.textBox.getTextField().func_146197_a(-1, this.textBox.getTextField().getCursorPosition(),
                    false);
            this.foundPlayerNames.clear();
            this.autocompleteIndex = 0;
            String s = this.textBox.getValue().substring(i).toLowerCase();

            s1 = this.textBox.getValue().substring(0, this.textBox.getTextField().getCursorPosition());
            if (chat.getActiveChannel().isPrefixHidden() && s1.startsWith("/")) {
                s1 = chat.getActiveChannel().getPrefix() + " " + s1;
            }
            this.sendAutocompleteRequest(s1, s);

            if (foundPlayerNames.isEmpty()) {
                return;
            }

            this.playerNamesFound = true;
            this.textBox.getTextField().deleteFromCursor(i - this.textBox.getTextField().getCursorPosition());

        }

        if (this.foundPlayerNames.size() > 1) {
            this.printListToChat(foundPlayerNames, 1);
        }

        textBox.getTextField().writeText(getCleanText(this.foundPlayerNames.get(this.autocompleteIndex++)));
    }

    private void sendAutocompleteRequest(String word, String s1) {
        if (word.length() >= 1) {
            // Forge auto complete
            tc.getForgeProxy().autoComplete(word, s1);

            BlockPos blockpos = null;
            if (this.mc.objectMouseOver != null
                    && this.mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                blockpos = this.mc.objectMouseOver.getBlockPos();
            }

            this.mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(word, blockpos));
            this.waitingOnAutocomplete = true;
        }
    }

    @Override
    public void onAutocompleteResponse(String[] array) {
        if (this.waitingOnAutocomplete) {
            this.playerNamesFound = false;
            this.foundPlayerNames.clear();

            // Forge auto complete
            String[] complete = tc.getForgeProxy().getLatestAutoComplete();
            if (complete != null) {
                array = ObjectArrays.concat(complete, array, String.class);
            }

            // Put completions in list
            for (String string : array) {
                foundPlayerNames.add(string);
            }

            String s1 = this.textBox.getValue().substring(
                    this.textBox.getTextField().func_146197_a(-1, this.textBox.getTextField().getCursorPosition(),
                            false));
            String s2 = StringUtils.getCommonPrefix(array);

            if (s2.length() > 0 && !s1.equalsIgnoreCase(s2)) {
                this.textBox.getTextField().deleteFromCursor(this.textBox.getTextField().func_146197_a(-1,
                        this.textBox.getTextField().getCursorPosition(), false)
                        - this.textBox.getTextField().getCursorPosition());

                this.textBox.getTextField().writeText(s2);
            } else if (this.foundPlayerNames.size() > 0) {
                this.playerNamesFound = true;
                this.autocompletePlayerNames();
            }
        }
    }

    @Override
    protected void setText(String text, boolean overwrite) {
        if (overwrite) {
            textBox.getTextField().setText(text);
        } else {
            textBox.getTextField().writeText(text);
        }
    }
}