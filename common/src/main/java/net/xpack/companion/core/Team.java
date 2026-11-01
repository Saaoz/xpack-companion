package net.xpack.companion.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Équipe XPack (#145) : coéquipiers choisis parmi les joueurs détectés sur le serveur. Sans équipe,
 * tout est compté en solo. L'état vient du serveur (réponse à la présence et aux actions de l'écran).
 */
public final class Team {
    public record Member(String pseudo, String uuidHash) {}

    /** Invitation reçue, à accepter ou refuser. {@code teamName} vide : « Équipe de {@code founder} ». */
    public record Invite(String id, String from, String teamName, String founder) {}

    /** Joueur du serveur dans l'écran Équipe. */
    public record Candidate(String name, String uuidHash, Presence.Status status, boolean teammate, boolean invited) {}

    /** {@code teamId} null : pas d'équipe pour ce modpack. */
    public record State(String teamId, String name, String founder, List<Member> members, List<Invite> invites, Set<String> invited) {
        public boolean inTeam() {
            return teamId != null;
        }

        public boolean isMember(String uuidHash) {
            return members.stream().anyMatch(m -> uuidHash.equals(m.uuidHash()));
        }
    }

    private Team() {}

    static State parse(JsonObject o) {
        JsonObject t = o.has("team") && o.get("team").isJsonObject() ? o.getAsJsonObject("team") : null;
        List<Member> members = new ArrayList<>();
        if (t != null && t.has("members") && t.get("members").isJsonArray()) {
            for (JsonElement e : t.getAsJsonArray("members")) {
                JsonObject m = e.getAsJsonObject();
                members.add(new Member(str(m, "pseudo"), str(m, "uuidHash")));
            }
        }
        List<Invite> invites = new ArrayList<>();
        if (o.has("invites") && o.get("invites").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("invites")) {
                JsonObject i = e.getAsJsonObject();
                invites.add(new Invite(str(i, "id"), str(i, "from"), str(i, "team"), str(i, "founder")));
            }
        }
        Set<String> invited = new HashSet<>();
        if (o.has("invited") && o.get("invited").isJsonArray()) o.getAsJsonArray("invited").forEach(e -> invited.add(e.getAsString()));
        return new State(t == null ? null : str(t, "id"), t == null ? null : str(t, "name"), t == null ? null : str(t, "founder"),
                List.copyOf(members), List.copyOf(invites), Set.copyOf(invited));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
