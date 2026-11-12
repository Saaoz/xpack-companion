package net.xpack.companion.client;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.xpack.companion.core.Presence;
import net.xpack.companion.core.Team;
import net.xpack.companion.core.XPackCore;

/**
 * Équipe XPack (#145), ouvert par le menu pause ou {@code /xpack team}. Les joueurs détectés sur le
 * serveur, en deux listes : avec le mod XPack lié (on peut les inviter, ils acceptent dans leur jeu)
 * et sans le mod (rappel : installe XPack pour faire partie de l'équipe). Sans équipe, tout compte en solo.
 */
public final class TeamScreen extends Screen {
    private static final int ROW = 22;
    private final Screen parent;
    /** État affiché : l'écran est reconstruit quand le serveur répond. */
    private String shownState = "";
    private int page;
    /** Lignes de texte à dessiner (les boutons sont des widgets) : position et contenu. */
    private final List<Line> lines = new ArrayList<>();

    private record Line(int x, int y, Component text) {}

    /** Une ligne de la liste : invitation reçue, titre de section, joueur avec ou sans le mod. */
    private record Row(Team.Invite invite, Component heading, Team.Candidate player) {}

    public TeamScreen(Screen parent) {
        super(Texts.tr("xpack.team.title"));
        this.parent = parent;
        XPackCore core = XPackClient.core();
        if (core != null) core.refreshTeam();
    }

    @Override
    protected void init() {
        lines.clear();
        XPackCore core = XPackClient.core();
        int x = width / 2 - 155;
        int bottom = height - 28;
        if (core == null || !core.teamsAvailable()) {
            boolean unlinked = core != null && core.inWorld() && !core.isLinkedHere();
            lines.add(new Line(width / 2, height / 2 - 20, Texts.tr(unlinked ? "xpack.team.unlinked" : "xpack.team.unavailable")));
            if (unlinked) {
                addRenderableWidget(Button.builder(Texts.tr("xpack.settings.open"), b -> minecraft.setScreen(new SettingsScreen(this))).bounds(width / 2 - 100, height / 2, 200, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, bottom, 200, 20).build());
            return;
        }
        shownState = state(core);
        Team.State team = core.team();

        // En-tête : l'équipe actuelle, ou comment en créer une.
        Component header;
        if (team == null) header = Texts.tr("xpack.team.loading");
        else if (!team.inTeam()) header = Texts.tr("xpack.team.none");
        else header = Texts.tr("xpack.team.members", team.members().stream().map(Team.Member::pseudo).collect(Collectors.joining(", ")));
        lines.add(new Line(width / 2, 34, header.copy().withStyle(ChatFormatting.GRAY)));

        List<Row> rows = new ArrayList<>();
        if (team != null) team.invites().forEach(i -> rows.add(new Row(i, null, null)));
        List<Team.Candidate> players = core.teamCandidates();
        List<Team.Candidate> linked = players.stream().filter(p -> p.status() == Presence.Status.LINKED).toList();
        List<Team.Candidate> others = players.stream().filter(p -> p.status() != Presence.Status.LINKED).toList();
        if (players.isEmpty()) rows.add(new Row(null, Texts.tr("xpack.team.alone"), null));
        if (!linked.isEmpty()) rows.add(new Row(null, Texts.tr("xpack.team.withMod"), null));
        linked.forEach(p -> rows.add(new Row(null, null, p)));
        if (!others.isEmpty()) rows.add(new Row(null, Texts.tr("xpack.team.withoutMod"), null));
        others.forEach(p -> rows.add(new Row(null, null, p)));

        int top = 50;
        int perPage = Math.max(1, (bottom - 30 - top) / ROW);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.min(page, Math.max(0, pages - 1));
        int y = top;
        for (Row r : rows.subList(page * perPage, Math.min(rows.size(), (page + 1) * perPage))) {
            if (r.invite() != null) {
                lines.add(new Line(x, y + 6, Texts.tr("xpack.team.inviteFrom", r.invite().from()).withStyle(ChatFormatting.YELLOW)));
                addRenderableWidget(Button.builder(Texts.tr("xpack.team.accept"), b -> core.answerTeamInvite(r.invite().id(), true)).bounds(x + 170, y, 68, 20).build());
                addRenderableWidget(Button.builder(Texts.tr("xpack.team.decline"), b -> core.answerTeamInvite(r.invite().id(), false)).bounds(x + 242, y, 68, 20).build());
            } else if (r.heading() != null) {
                lines.add(new Line(x, y + 8, r.heading().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
            } else if (r.player().status() == Presence.Status.LINKED) {
                Team.Candidate p = r.player();
                lines.add(new Line(x, y + 6, Component.literal(p.name()).withStyle(ChatFormatting.GREEN)));
                String label = p.teammate() ? "xpack.team.teammate" : p.invited() ? "xpack.team.invitedLabel" : "xpack.team.invite";
                Button invite = Button.builder(Texts.tr(label), b -> core.inviteToTeam(p.uuidHash())).bounds(x + 190, y, 120, 20).build();
                invite.active = !p.teammate() && !p.invited();
                addRenderableWidget(invite);
            } else {
                Team.Candidate p = r.player();
                String hint = p.status() == Presence.Status.INSTALLED ? "xpack.team.hintUnlinked" : "xpack.team.hintInstall";
                lines.add(new Line(x, y + 6, Component.literal(p.name() + " — ").withStyle(ChatFormatting.GRAY).append(Texts.tr(hint).withStyle(ChatFormatting.DARK_GRAY))));
            }
            y += ROW;
        }
        if (pages > 1) {
            Button prev = Button.builder(Component.literal("<"), b -> {
                page--;
                rebuildWidgets();
            }).bounds(x, bottom - 26, 20, 20).build();
            prev.active = page > 0;
            Button next = Button.builder(Component.literal(">"), b -> {
                page++;
                rebuildWidgets();
            }).bounds(x + 290, bottom - 26, 20, 20).build();
            next.active = page < pages - 1;
            addRenderableWidget(prev);
            addRenderableWidget(next);
            lines.add(new Line(width / 2, bottom - 20, Component.literal((page + 1) + " / " + pages).withStyle(ChatFormatting.GRAY)));
        }

        if (team != null && team.inTeam()) {
            addRenderableWidget(Button.builder(Texts.tr("xpack.team.leave").withStyle(ChatFormatting.RED), b ->
                    minecraft.setScreen(new ConfirmScreen(ok -> {
                        if (ok) core.leaveTeam();
                        minecraft.setScreen(this);
                    }, Texts.tr("xpack.team.leaveConfirmTitle"), Texts.tr("xpack.team.leaveConfirm")))).bounds(x, bottom, 150, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x + 160, bottom, 150, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 100, bottom, 200, 20).build());
        }
    }

    private static String state(XPackCore core) {
        return core.teamsAvailable() + "|" + core.team() + "|" + core.teamCandidates();
    }

    @Override
    public void tick() {
        XPackCore core = XPackClient.core();
        if (core != null && !state(core).equals(shownState)) rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Compat.background(this, g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        for (Line l : lines) {
            if (l.x() == width / 2) g.drawCenteredString(font, l.text(), l.x(), l.y(), 0xFFFFFF);
            else g.drawString(font, l.text(), l.x(), l.y(), 0xFFFFFF);
        }
        XPackCore core = XPackClient.core();
        if (core != null && core.teamError() != null) {
            g.drawCenteredString(font, Texts.tr(core.teamError()).withStyle(ChatFormatting.RED), width / 2, height - 38, 0xFF5555);
        } else if (core != null && core.teamsAvailable()) {
            g.drawCenteredString(font, Texts.tr("xpack.team.footer").withStyle(ChatFormatting.DARK_GRAY), width / 2, height - 38, 0x777777);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
