package net.xpack.companion.core;

/** Affichage côté Minecraft, implémenté dans le code client (écrans, toasts, chat). */
public interface Ui {
    /** Alerte avant une commande de triche (#44). {@code onContinue} est appelé si le joueur confirme. */
    void showCheatWarning(String command, Runnable onContinue);

    /**
     * Mode triche d'un mod activé (#49) : {@code jei}, {@code rei}, {@code emi} (donner des objets) ou
     * {@code ftbquests} (mode édition des quêtes). {@code onDisable} l'éteint (aussi en fermant
     * l'écran), {@code onContinue} le garde et signale le monde.
     */
    void showCheatModeWarning(String mode, Runnable onDisable, Runnable onContinue);

    /** Toast « Succès débloqué » (#39). */
    void toastUnlocked(Unlock unlock);

    /** Un succès débloqué, tel que le toast l'affiche. */
    final class Unlock {
        public final String name;
        public final int points;
        /** commun, peu_commun, rare, epique ou legendaire. */
        public final String rarity;
        /** Objet icône du progrès (« minecraft:diamond »), ou null : succès sans progrès du jeu, ancien serveur. */
        public final String item;
        /** Part des joueurs du modpack qui l'ont (en %), ou -1 si le serveur ne l'envoie pas. */
        public final double percent;
        /** Badge de difficulté du monde (peaceful … hardcore), ou null. */
        public final String difficulty;

        public Unlock(String name, int points, String rarity, String item, double percent, String difficulty) {
            this.name = name;
            this.points = points;
            this.rarity = rarity;
            this.item = item;
            this.percent = percent;
            this.difficulty = difficulty;
        }

        /** Rang de la rareté, du commun (0) au légendaire (4). */
        public static int rank(String rarity) {
            switch (rarity) {
                case "peu_commun":
                    return 1;
                case "rare":
                    return 2;
                case "epique":
                    return 3;
                case "legendaire":
                    return 4;
                default:
                    return 0;
            }
        }

        /** Couleur de la rareté (celles du site). */
        public static int color(String rarity) {
            switch (rarity) {
                case "peu_commun":
                    return 0x4CC38A;
                case "rare":
                    return 0x4FA3FF;
                case "epique":
                    return 0xB47CFF;
                case "legendaire":
                    return 0xFFB347;
                default:
                    return 0xA9B6C4;
            }
        }

        /** Le plus rare d'une liste non vide (le premier en cas d'égalité). */
        public static Unlock rarest(java.util.List<Unlock> unlocks) {
            Unlock best = unlocks.get(0);
            for (Unlock u : unlocks) if (rank(u.rarity) > rank(best.rarity)) best = u;
            return best;
        }

        /** Pourcentage arrondi pour le toast, -1 inconnu, 0 pour « moins de 1 % ». */
        public long shownPercent() {
            return percent < 0 ? -1 : percent < 1 ? 0 : Math.round(percent);
        }

        /** Traduction d'une clé des fichiers de langue du mod, propre à chaque version de Minecraft. */
        public interface Tr {
            String tr(String key, Object... args);
        }

        /** 1re ligne du toast : « SUCCÈS XPACK · RARE », ou « 3 SUCCÈS XPACK ». */
        public static String header(java.util.List<Unlock> unlocks, Tr t) {
            String text = unlocks.size() == 1 ? t.tr("xpack.toast.header", t.tr("xpack.rarity." + unlocks.get(0).rarity)) : t.tr("xpack.toast.headerMany", unlocks.size());
            return text.toUpperCase(java.util.Locale.ROOT);
        }

        /** 2e ligne : le nom, ou les noms séparés par des virgules. */
        public static String names(java.util.List<Unlock> unlocks) {
            StringBuilder out = new StringBuilder();
            for (Unlock u : unlocks) out.append(out.length() == 0 ? "" : ", ").append(u.name);
            return out.toString();
        }

        /** 3e ligne : part des joueurs et difficulté ; pour plusieurs succès, la rareté du plus rare. */
        public static String detail(java.util.List<Unlock> unlocks, Tr t) {
            Unlock rarest = rarest(unlocks);
            if (unlocks.size() > 1) return t.tr("xpack.toast.rarest", t.tr("xpack.rarity." + rarest.rarity));
            long p = rarest.shownPercent();
            String players = p < 0 ? null : p == 0 ? t.tr("xpack.toast.playersFew") : t.tr("xpack.toast.players", p);
            String difficulty = rarest.difficulty == null ? null : t.tr("xpack.difficulty." + rarest.difficulty);
            if (players != null && difficulty != null) return players + " · " + difficulty;
            return players != null ? players : difficulty != null ? difficulty : "";
        }

        public static int totalPoints(java.util.List<Unlock> unlocks) {
            int sum = 0;
            for (Unlock u : unlocks) sum += u.points;
            return sum;
        }
    }

    /** Toast « Modpack terminé à 100 % » (#60), plus marqué qu'un succès. {@code modpackName} peut être null. */
    void toastCompleted(String modpackName, int achievements, long playtimeTicks);

    /** Toast « Palier 25/50/75 % atteint » (#60). {@code modpackName} peut être null. */
    void toastMilestone(String modpackName, int tier, int achievements, int total);

    /** Écran de consentement au premier lancement (#40). {@code answer} reçoit le choix du joueur. */
    void askConsent(java.util.function.Consumer<Boolean> answer);

    /** Message système dans le chat, à partir d'une clé de traduction du mod. */
    void notify(String translationKey, Object... args);

    /**
     * Triche détectée automatiquement (créatif, vol, mod interdit…) : le monde n'est plus
     * comptabilisé. Notification qui reste ~20 s avec un accès au support (contestation).
     */
    void integrityAlert(String translationKey, Object... args);

    /**
     * La difficulté vient de baisser (#116) : le badge de difficulté du monde baissera s'il reste
     * trop longtemps plus bas. Prévenir, pas punir : le joueur peut la remettre ({@code canRevert},
     * en solo) ou accepter. Montré une seule fois par monde.
     */
    void difficultyWarning(String from, String to, boolean canRevert);

    /**
     * Résultat de {@code /xpack check} (#126) : pour chaque autre joueur connecté, mod XPack lié,
     * installé sans compte, ou non détecté. {@code selfLinked} : le compte du joueur est lié.
     */
    void presenceReport(boolean selfLinked, java.util.List<Presence.Player> players);

    /** Message de l'équipe XPack (#145) dans le chat, avec un lien qui ouvre l'écran Équipe. */
    void teamNotice(String translationKey, Object... args);
}
