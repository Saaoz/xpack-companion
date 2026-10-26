package net.xpack.companion.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.XPackCore;

/**
 * Contacter le support depuis le jeu (contestation d'une détection). Le joueur voit exactement ce
 * qui part — détections, modpack, versions, nombre de mods — et ajoute un message. La réponse
 * arrive par e-mail, à l'adresse de son compte XPack.
 */
public final class SupportScreen extends Screen {
    private static final int MAX_MESSAGE = 2000;
    private final Screen parent;
    private MultiLineEditBox message;
    private String draft = "";
    /** null : pas encore envoyé ; sinon clé du résultat affiché. */
    private String result;
    private boolean sending;

    public SupportScreen(Screen parent) {
        super(Texts.tr("xpack.support.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        XPackCore core = XPackClient.core();
        int x = width / 2 - 155;
        if (core == null || !core.canContactSupport()) {
            addRenderableWidget(Button.builder(Texts.tr("xpack.settings.open"), b -> minecraft.setScreen(new SettingsScreen(this))).bounds(x, height / 2 + 10, 150, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 160, height / 2 + 10, 150, 20).build());
            return;
        }
        int boxY = 40 + summary(core).size() * 10 + 8;
        message = new MultiLineEditBox(font, x, boxY, 310, 60, Texts.tr("xpack.support.placeholder"), Component.empty());
        message.setCharacterLimit(MAX_MESSAGE);
        message.setValue(draft);
        message.setValueListener(v -> draft = v);
        addRenderableWidget(message);
        Button send = Button.builder(Texts.tr(sending ? "xpack.support.sending" : "xpack.support.send"), b -> {
            sending = true;
            rebuildWidgets();
            core.sendSupport(draft, ok -> {
                sending = false;
                result = ok ? "xpack.support.sent" : "xpack.support.failed";
                rebuildWidgets();
            });
        }).bounds(x, boxY + 68, 150, 20).build();
        send.active = !sending && !"xpack.support.sent".equals(result);
        addRenderableWidget(send);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 160, boxY + 68, 150, 20).build());
    }

    /** Ce qui sera envoyé, en clair. */
    private List<Component> summary(XPackCore core) {
        JsonObject r = core.supportReport();
        List<Component> lines = new ArrayList<>();
        lines.add(Texts.tr("xpack.support.sentTitle").withStyle(ChatFormatting.GRAY));
        for (JsonElement d : r.getAsJsonArray("detections")) {
            JsonObject o = d.getAsJsonObject();
            String detail = o.has("detail") ? " (" + o.get("detail").getAsString() + ")" : "";
            lines.add(Component.literal("• " + o.get("reason").getAsString() + detail).withStyle(ChatFormatting.WHITE));
        }
        JsonObject m = r.has("modpack") ? r.getAsJsonObject("modpack") : new JsonObject();
        String pack = m.has("name") ? m.get("name").getAsString() : m.has("source") ? m.get("source").getAsString() : "?";
        lines.add(Texts.tr("xpack.support.context", pack, r.getAsJsonArray("mods").size()).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        XPackCore core = XPackClient.core();
        if (core == null || !core.canContactSupport()) {
            g.drawCenteredString(font, Texts.tr("xpack.support.needLink"), width / 2, height / 2 - 10, 0xFFD24A);
            return;
        }
        int y = 40;
        for (Component line : summary(core)) {
            g.drawString(font, line, width / 2 - 155, y, 0xFFFFFF);
            y += 10;
        }
        if (result != null) {
            int color = result.equals("xpack.support.sent") ? 0x55FF55 : 0xFF5555;
            g.drawCenteredString(font, Texts.tr(result), width / 2, message.getY() + 94, color);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
