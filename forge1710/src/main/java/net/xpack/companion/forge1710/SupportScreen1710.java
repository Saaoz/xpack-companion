package net.xpack.companion.forge1710;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.xpack.companion.core.XPackCore;
import org.lwjgl.input.Keyboard;

/**
 * Contact du support (contester une détection) : le joueur voit ce qui part (détections, modpack,
 * nombre de mods ; jamais le chat, le journal ni ses fichiers), ajoute un message et envoie. Il faut
 * un compte lié : la réponse arrive à l'e-mail du compte XPack. Message sur une ligne en 1.7.10.
 */
final class SupportScreen1710 extends GuiScreen {
    private static final int MAX_MESSAGE = 2000;
    private static final int SEND = 1, DONE = 2;
    private final GuiScreen parent;
    private GuiTextField message;
    private String draft = "";
    /** null : pas encore envoyé ; sinon clé du résultat affiché. */
    private String result;
    private boolean sending;

    SupportScreen1710(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        XPackCore core = Client1710.core();
        int x = width / 2 - 155;
        if (core == null || !core.canContactSupport()) {
            buttonList.add(new GuiButton(DONE, x + 80, height / 2 + 10, 150, 20, I18n.format("gui.done")));
            return;
        }
        int boxY = 40 + summary(core).size() * 10 + 8;
        message = new GuiTextField(fontRendererObj, x, boxY, 310, 20);
        message.setMaxStringLength(MAX_MESSAGE);
        message.setText(draft);
        message.setFocused(true);
        GuiButton send = new GuiButton(SEND, x, boxY + 28, 150, 20, Lang.tr(sending ? "xpack.support.sending" : "xpack.support.send"));
        send.enabled = !sending && !"xpack.support.sent".equals(result);
        buttonList.add(send);
        buttonList.add(new GuiButton(DONE, x + 160, boxY + 28, 150, 20, I18n.format("gui.done")));
    }

    /** Ce qui sera envoyé, en clair. */
    private List<String> summary(XPackCore core) {
        JsonObject r = core.supportReport();
        List<String> lines = new ArrayList<>();
        lines.add(EnumChatFormatting.GRAY + Lang.tr("xpack.support.sentTitle"));
        for (JsonElement d : r.getAsJsonArray("detections")) {
            JsonObject o = d.getAsJsonObject();
            String detail = o.has("detail") ? " (" + o.get("detail").getAsString() + ")" : "";
            lines.add(EnumChatFormatting.WHITE + "• " + o.get("reason").getAsString() + detail);
        }
        JsonObject m = r.has("modpack") ? r.getAsJsonObject("modpack") : new JsonObject();
        String pack = m.has("name") ? m.get("name").getAsString() : m.has("source") ? m.get("source").getAsString() : "?";
        lines.add(EnumChatFormatting.DARK_GRAY + Lang.tr("xpack.support.context", pack, r.getAsJsonArray("mods").size()));
        return lines;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        XPackCore core = Client1710.core();
        if (button.id == DONE || core == null) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == SEND) {
            sending = true;
            initGui();
            core.sendSupport(draft, ok -> {
                sending = false;
                result = ok ? "xpack.support.sent" : "xpack.support.failed";
                initGui();
            });
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (message != null && message.textboxKeyTyped(typedChar, keyCode)) {
            draft = message.getText();
            return;
        }
        super.keyTyped(typedChar, keyCode); // Échap
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (message != null) message.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        if (message != null) message.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, Lang.tr("xpack.support.title"), width / 2, 15, 0xFFFFFF);
        XPackCore core = Client1710.core();
        if (core == null || !core.canContactSupport()) {
            drawCenteredString(fontRendererObj, Lang.tr("xpack.support.needLink"), width / 2, height / 2 - 10, 0xFFD24A);
        } else {
            int y = 40;
            for (String line : summary(core)) {
                drawString(fontRendererObj, line, width / 2 - 155, y, 0xFFFFFF);
                y += 10;
            }
            if (message != null) {
                message.drawTextBox();
                if (message.getText().isEmpty() && !message.isFocused()) {
                    drawString(fontRendererObj, EnumChatFormatting.DARK_GRAY + fontRendererObj.trimStringToWidth(Lang.tr("xpack.support.placeholder"), 300), message.xPosition + 4, message.yPosition + 6, 0x777777);
                }
                if (result != null) drawCenteredString(fontRendererObj, Lang.tr(result), width / 2, message.yPosition + 56, result.equals("xpack.support.sent") ? 0x55FF55 : 0xFF5555);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
