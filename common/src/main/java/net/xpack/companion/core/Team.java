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
    public static final class Member {
        private final String pseudo;
        private final String uuidHash;

        public Member(String pseudo, String uuidHash) {
            this.pseudo = pseudo;
            this.uuidHash = uuidHash;
        }

        public String pseudo() {
            return pseudo;
        }

        public String uuidHash() {
            return uuidHash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Member)) return false;
            Member other = (Member) o;
            return java.util.Objects.equals(pseudo, other.pseudo) && java.util.Objects.equals(uuidHash, other.uuidHash);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pseudo, uuidHash);
        }

        @Override
        public String toString() {
            return "Member[" + "pseudo=" + pseudo + ", uuidHash=" + uuidHash + "]";
        }
    }

    /** Invitation reçue, à accepter ou refuser. {@code teamName} vide : « Équipe de {@code founder} ». */
    public static final class Invite {
        private final String id;
        private final String from;
        private final String teamName;
        private final String founder;

        public Invite(String id, String from, String teamName, String founder) {
            this.id = id;
            this.from = from;
            this.teamName = teamName;
            this.founder = founder;
        }

        public String id() {
            return id;
        }

        public String from() {
            return from;
        }

        public String teamName() {
            return teamName;
        }

        public String founder() {
            return founder;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Invite)) return false;
            Invite other = (Invite) o;
            return java.util.Objects.equals(id, other.id) && java.util.Objects.equals(from, other.from) && java.util.Objects.equals(teamName, other.teamName) && java.util.Objects.equals(founder, other.founder);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, from, teamName, founder);
        }

        @Override
        public String toString() {
            return "Invite[" + "id=" + id + ", from=" + from + ", teamName=" + teamName + ", founder=" + founder + "]";
        }
    }

    /** Joueur du serveur dans l'écran Équipe. */
    public static final class Candidate {
        private final String name;
        private final String uuidHash;
        private final Presence.Status status;
        private final boolean teammate;
        private final boolean invited;

        public Candidate(String name, String uuidHash, Presence.Status status, boolean teammate, boolean invited) {
            this.name = name;
            this.uuidHash = uuidHash;
            this.status = status;
            this.teammate = teammate;
            this.invited = invited;
        }

        public String name() {
            return name;
        }

        public String uuidHash() {
            return uuidHash;
        }

        public Presence.Status status() {
            return status;
        }

        public boolean teammate() {
            return teammate;
        }

        public boolean invited() {
            return invited;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Candidate)) return false;
            Candidate other = (Candidate) o;
            return java.util.Objects.equals(name, other.name) && java.util.Objects.equals(uuidHash, other.uuidHash) && java.util.Objects.equals(status, other.status) && java.util.Objects.equals(teammate, other.teammate) && java.util.Objects.equals(invited, other.invited);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, uuidHash, status, teammate, invited);
        }

        @Override
        public String toString() {
            return "Candidate[" + "name=" + name + ", uuidHash=" + uuidHash + ", status=" + status + ", teammate=" + teammate + ", invited=" + invited + "]";
        }
    }

    /** {@code teamId} null : pas d'équipe pour ce modpack. */
    public static final class State {
        private final String teamId;
        private final String name;
        private final String founder;
        private final List<Member> members;
        private final List<Invite> invites;
        private final Set<String> invited;

        public State(String teamId, String name, String founder, List<Member> members, List<Invite> invites, Set<String> invited) {
            this.teamId = teamId;
            this.name = name;
            this.founder = founder;
            this.members = members;
            this.invites = invites;
            this.invited = invited;
        }

        public String teamId() {
            return teamId;
        }

        public String name() {
            return name;
        }

        public String founder() {
            return founder;
        }

        public List<Member> members() {
            return members;
        }

        public List<Invite> invites() {
            return invites;
        }

        public Set<String> invited() {
            return invited;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof State)) return false;
            State other = (State) o;
            return java.util.Objects.equals(teamId, other.teamId) && java.util.Objects.equals(name, other.name) && java.util.Objects.equals(founder, other.founder) && java.util.Objects.equals(members, other.members) && java.util.Objects.equals(invites, other.invites) && java.util.Objects.equals(invited, other.invited);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(teamId, name, founder, members, invites, invited);
        }

        @Override
        public String toString() {
            return "State[" + "teamId=" + teamId + ", name=" + name + ", founder=" + founder + ", members=" + members + ", invites=" + invites + ", invited=" + invited + "]";
        }

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
                J8.copyOf(members), J8.copyOf(invites), J8.copyOfSet(invited));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
