package net.xpack.companion.legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.xpack.companion.core.Presence;
import net.xpack.companion.core.Team;
import net.xpack.companion.core.XPackCore;

/**
 * Équipe XPack (#145), ouvert par {@code /xpack team}, comme dans les autres versions. Les joueurs
 * détectés sur le serveur, en deux listes : avec le mod XPack lié (on peut les inviter, ils acceptent
 * dans leur jeu) et sans le mod (rappel : installe XPack pour faire partie de l'équipe). Sans équipe,
 * tout compte en solo.
 */
public final class TeamScreen extends LegacyGuiScreen {
    private static final int ROW = 22;
    private static final int DONE = 1, PREV = 2, NEXT = 3, LEAVE = 4, SETTINGS = 5, ACTIONS = 100;
    private final GuiScreen parent;
    /** État affiché : l'écran est reconstruit quand le serveur répond. */
    private String shownState = "";
    private int page;
    private boolean confirmLeave;
    /** Lignes de texte à dessiner (les boutons sont des widgets) : position et contenu. */
    private final List<Line> lines = new ArrayList<>();
    /** Action de chaque bouton de ligne (invitation, accepter, refuser). */
    private final Map<Integer, Runnable> actions = new HashMap<>();

    private static final class Line {
        final int x;
        final int y;
        final String text;
        final boolean centered;

        Line(int x, int y, String text, boolean centered) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.centered = centered;
        }
    }

    /** Une ligne de la liste : invitation reçue, titre de section, joueur avec ou sans le mod. */
    private static final class Row {
        final Team.Invite invite;
        final String heading;
        final Team.Candidate player;

        Row(Team.Invite invite, String heading, Team.Candidate player) {
            this.invite = invite;
            this.heading = heading;
            this.player = player;
        }
    }

    public TeamScreen(GuiScreen parent) {
        this.parent = parent;
        XPackCore core = Platform.core();
        if (core != null) core.refreshTeam();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        lines.clear();
        actions.clear();
        final XPackCore core = Platform.core();
        int x = width / 2 - 155;
        int bottom = height - 28;
        if (core == null || !core.teamsAvailable()) {
            boolean unlinked = core != null && core.inWorld() && !core.isLinkedHere();
            lines.add(new Line(width / 2, height / 2 - 20, Lang.tr(unlinked ? "xpack.team.unlinked" : "xpack.team.unavailable"), true));
            if (unlinked) buttonList.add(new GuiButton(SETTINGS, width / 2 - 100, height / 2, 200, 20, Lang.tr("xpack.settings.open")));
            buttonList.add(new GuiButton(DONE, width / 2 - 100, bottom, 200, 20, I18n.format("gui.done")));
            return;
        }
        shownState = state(core);
        Team.State team = core.team();

        // En-tête : l'équipe actuelle, ou comment en créer une.
        String header;
        if (team == null) header = Lang.tr("xpack.team.loading");
        else if (!team.inTeam()) header = Lang.tr("xpack.team.none");
        else header = Lang.tr("xpack.team.members", joinPseudos(team));
        lines.add(new Line(width / 2, 34, Fmt.GRAY + font().trimStringToWidth(header, width - 20), true));

        List<Row> rows = new ArrayList<>();
        if (team != null) for (Team.Invite i : team.invites()) rows.add(new Row(i, null, null));
        List<Team.Candidate> players = core.teamCandidates();
        List<Team.Candidate> linked = new ArrayList<>();
        List<Team.Candidate> others = new ArrayList<>();
        for (Team.Candidate p : players) (p.status() == Presence.Status.LINKED ? linked : others).add(p);
        if (players.isEmpty()) rows.add(new Row(null, Lang.tr("xpack.team.alone"), null));
        if (!linked.isEmpty()) rows.add(new Row(null, Lang.tr("xpack.team.withMod"), null));
        for (Team.Candidate p : linked) rows.add(new Row(null, null, p));
        if (!others.isEmpty()) rows.add(new Row(null, Lang.tr("xpack.team.withoutMod"), null));
        for (Team.Candidate p : others) rows.add(new Row(null, null, p));

        int top = 50;
        int perPage = Math.max(1, (bottom - 30 - top) / ROW);
        int pages = (rows.size() + perPage - 1) / perPage;
        page = Math.min(page, Math.max(0, pages - 1));
        int y = top;
        int nextId = ACTIONS;
        for (Row r : rows.subList(page * perPage, Math.min(rows.size(), (page + 1) * perPage))) {
            if (r.invite != null) {
                final String inviteId = r.invite.id();
                lines.add(new Line(x, y + 6, Fmt.YELLOW + Lang.tr("xpack.team.inviteFrom", r.invite.from()), false));
                actions.put(nextId, () -> core.answerTeamInvite(inviteId, true));
                buttonList.add(new GuiButton(nextId++, x + 170, y, 68, 20, Lang.tr("xpack.team.accept")));
                actions.put(nextId, () -> core.answerTeamInvite(inviteId, false));
                buttonList.add(new GuiButton(nextId++, x + 242, y, 68, 20, Lang.tr("xpack.team.decline")));
            } else if (r.heading != null) {
                lines.add(new Line(x, y + 8, Fmt.WHITE.toString() + Fmt.BOLD + r.heading, false));
            } else if (r.player.status() == Presence.Status.LINKED) {
                final Team.Candidate p = r.player;
                lines.add(new Line(x, y + 6, Fmt.GREEN + Fmt.plain(p.name()), false));
                String label = p.teammate() ? "xpack.team.teammate" : p.invited() ? "xpack.team.invitedLabel" : "xpack.team.invite";
                GuiButton invite = new GuiButton(nextId, x + 190, y, 120, 20, Lang.tr(label));
                invite.enabled = !p.teammate() && !p.invited();
                actions.put(nextId++, () -> core.inviteToTeam(p.uuidHash()));
                buttonList.add(invite);
            } else {
                Team.Candidate p = r.player;
                String hint = p.status() == Presence.Status.INSTALLED ? "xpack.team.hintUnlinked" : "xpack.team.hintInstall";
                lines.add(new Line(x, y + 6, Fmt.GRAY + Fmt.plain(p.name()) + " — " + Fmt.DARK_GRAY + Lang.tr(hint), false));
            }
            y += ROW;
        }
        if (pages > 1) {
            GuiButton prev = new GuiButton(PREV, x, bottom - 26, 20, 20, "<");
            prev.enabled = page > 0;
            GuiButton next = new GuiButton(NEXT, x + 290, bottom - 26, 20, 20, ">");
            next.enabled = page < pages - 1;
            buttonList.add(prev);
            buttonList.add(next);
            lines.add(new Line(width / 2, bottom - 20, Fmt.GRAY + "" + (page + 1) + " / " + pages, true));
        }

        if (team != null && team.inTeam()) {
            // Pas de boîte de confirmation ici : un second clic sur le bouton confirme.
            String leave = confirmLeave ? Lang.tr("xpack.team.leaveConfirmTitle") : Lang.tr("xpack.team.leave");
            buttonList.add(new GuiButton(LEAVE, x, bottom, 150, 20, Fmt.RED + leave));
            buttonList.add(new GuiButton(DONE, x + 160, bottom, 150, 20, I18n.format("gui.done")));
        } else {
            buttonList.add(new GuiButton(DONE, width / 2 - 100, bottom, 200, 20, I18n.format("gui.done")));
        }
    }

    private static String joinPseudos(Team.State team) {
        StringBuilder sb = new StringBuilder();
        for (Team.Member m : team.members()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(m.pseudo());
        }
        return sb.toString();
    }

    private static String state(XPackCore core) {
        return core.teamsAvailable() + "|" + core.team() + "|" + core.teamCandidates();
    }

    @Override
    public void updateScreen() {
        XPackCore core = Platform.core();
        if (core != null && !state(core).equals(shownState)) initGui();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        XPackCore core = Platform.core();
        switch (button.id) {
            case DONE:
                mc.displayGuiScreen(parent);
                return;
            case SETTINGS:
                mc.displayGuiScreen(new SettingsScreen(this));
                return;
            case PREV:
                page--;
                initGui();
                return;
            case NEXT:
                page++;
                initGui();
                return;
            case LEAVE:
                if (core == null) return;
                if (confirmLeave) {
                    confirmLeave = false;
                    core.leaveTeam();
                } else {
                    confirmLeave = true;
                }
                initGui();
                return;
            default:
                Runnable action = actions.get(button.id);
                if (action != null) action.run();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(font(), Lang.tr("xpack.team.title"), width / 2, 18, 0xFFFFFF);
        for (Line l : lines) {
            if (l.centered) drawCenteredString(font(), l.text, l.x, l.y, 0xFFFFFF);
            else drawString(font(), l.text, l.x, l.y, 0xFFFFFF);
        }
        XPackCore core = Platform.core();
        if (core != null && core.teamError() != null) {
            drawCenteredString(font(), Fmt.RED + Lang.tr(core.teamError()), width / 2, height - 38, 0xFF5555);
        } else if (confirmLeave) {
            drawCenteredString(font(), Fmt.RED + font().trimStringToWidth(Lang.tr("xpack.team.leaveConfirm"), width - 20), width / 2, height - 38, 0xFF5555);
        } else if (core != null && core.teamsAvailable()) {
            drawCenteredString(font(), Fmt.DARK_GRAY + font().trimStringToWidth(Lang.tr("xpack.team.footer"), width - 20), width / 2, height - 38, 0x777777);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
