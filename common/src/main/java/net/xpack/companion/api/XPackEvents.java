package net.xpack.companion.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * API d'événements locale de XPack Companion : un autre mod du même jeu peut s'abonner à ce que XPack
 * affiche déjà au joueur (succès débloqué, palier, modpack terminé, monde suivi ou non).
 *
 * <p>Tout reste sur le PC : rien n'est envoyé au serveur par cette API, et les événements ne portent que
 * ce que le joueur voit déjà dans un toast ou un message (ni UUID, ni pseudo, ni jeton, ni identifiant de
 * monde, ni liste des mods).
 *
 * <p><b>Désactivée par défaut</b> : le joueur doit mettre {@code "eventsApi": true} dans
 * {@code config/xpack.json}. Tant que l'option est coupée, aucun écouteur n'est appelé.
 *
 * <p><b>Thread</b> : les écouteurs sont appelés sur le thread du jeu (celui du cœur de XPack). Ils doivent
 * rester rapides. Une exception levée par un écouteur est attrapée et journalisée une seule fois : elle ne
 * casse ni XPack, ni les autres écouteurs, ni le jeu.
 *
 * <p><b>Stabilité</b> : les noms et les champs de ce package ne changent pas sans changement de version
 * majeure ; de nouveaux champs ou de nouvelles méthodes {@code default} peuvent être ajoutés.
 *
 * <p>La dépendance d'un autre mod envers XPack doit rester <b>facultative</b> : vérifier que le mod est
 * chargé avant d'appeler cette classe.
 */
public final class XPackEvents {
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile BooleanSupplier enabled = () -> false;
    private static volatile boolean errorLogged;

    private XPackEvents() {
    }

    /** Abonne un écouteur (sans effet s'il l'est déjà). */
    public static void register(Listener l) {
        if (l != null && !LISTENERS.contains(l)) LISTENERS.add(l);
    }

    /** Retire un écouteur. */
    public static void unregister(Listener l) {
        LISTENERS.remove(l);
    }

    /** Vrai seulement si le joueur a activé l'option {@code eventsApi} de {@code config/xpack.json}. */
    public static boolean enabled() {
        return enabled.getAsBoolean();
    }

    /** Usage interne au mod XPack : le cœur indique comment lire l'option {@code eventsApi}. */
    public static void bind(BooleanSupplier option) {
        enabled = option != null ? option : () -> false;
    }

    /** Usage interne au mod XPack : retire tous les écouteurs (tests). */
    public static void clearListeners() {
        LISTENERS.clear();
        errorLogged = false;
    }

    /** Usage interne au mod XPack : diffuse un événement. Ne fait rien si l'option est coupée ou sans écouteur. */
    public static void dispatch(Consumer<Listener> call) {
        if (LISTENERS.isEmpty() || !enabled()) return;
        for (Listener l : LISTENERS) {
            try {
                call.accept(l);
            } catch (Throwable t) {
                if (!errorLogged) {
                    errorLogged = true;
                    System.err.println("[XPack] Un écouteur de l'API d'événements a levé une exception (ignorée) : " + t);
                }
            }
        }
    }

    /** Écouteur : redéfinir seulement les méthodes utiles, les autres ne font rien. */
    public interface Listener {
        /** Un succès vient d'être débloqué (même si les toasts sont coupés). */
        default void onAchievementUnlocked(AchievementUnlocked e) {
        }

        /** Un palier du modpack est atteint (25, 50 ou 75 %). */
        default void onMilestone(Milestone e) {
        }

        /** Le modpack est terminé (100 %). */
        default void onModpackCompleted(ModpackCompleted e) {
        }

        /**
         * Le monde est suivi ou ne l'est plus. {@link WorldTracking#reason} : {@code "joined"} (entrée dans
         * le monde), {@code "practice"} (monde de test), {@code "flagged"} (monde signalé, il ne compte
         * plus), {@code "left"} (sortie du monde).
         */
        default void onWorldTracking(WorldTracking e) {
        }
    }

    /** Succès débloqué, tel que le toast l'affiche. */
    public static final class AchievementUnlocked {
        public final String name;
        public final int points;
        /** {@code commun}, {@code peu_commun}, {@code rare}, {@code epique} ou {@code legendaire}. */
        public final String rarity;
        /** Objet icône (« minecraft:diamond »), ou null. */
        public final String item;
        /** Part des joueurs du modpack qui l'ont (en %), ou -1 si inconnue. */
        public final double percent;
        /** Difficulté du monde (peaceful … hardcore), ou null. */
        public final String difficulty;
        /** Quête : les points sont des points d'avancement, à part des succès. */
        public final boolean quest;

        public AchievementUnlocked(String name, int points, String rarity, String item, double percent, String difficulty, boolean quest) {
            this.name = name;
            this.points = points;
            this.rarity = rarity;
            this.item = item;
            this.percent = percent;
            this.difficulty = difficulty;
            this.quest = quest;
        }

        @Override
        public String toString() {
            return "AchievementUnlocked{name=" + name + ", points=" + points + ", rarity=" + rarity + ", item=" + item
                    + ", percent=" + percent + ", difficulty=" + difficulty + ", quest=" + quest + "}";
        }
    }

    /** Palier de complétion du modpack. */
    public static final class Milestone {
        public final String modpackName;
        /** 25, 50 ou 75. */
        public final int tier;
        public final int achievements;
        public final int total;

        public Milestone(String modpackName, int tier, int achievements, int total) {
            this.modpackName = modpackName;
            this.tier = tier;
            this.achievements = achievements;
            this.total = total;
        }

        @Override
        public String toString() {
            return "Milestone{modpackName=" + modpackName + ", tier=" + tier + ", achievements=" + achievements + ", total=" + total + "}";
        }
    }

    /** Modpack terminé à 100 %. */
    public static final class ModpackCompleted {
        public final String modpackName;
        public final int achievements;
        public final long playtimeTicks;

        public ModpackCompleted(String modpackName, int achievements, long playtimeTicks) {
            this.modpackName = modpackName;
            this.achievements = achievements;
            this.playtimeTicks = playtimeTicks;
        }

        @Override
        public String toString() {
            return "ModpackCompleted{modpackName=" + modpackName + ", achievements=" + achievements + ", playtimeTicks=" + playtimeTicks + "}";
        }
    }

    /** Le monde est suivi (ses progrès comptent) ou ne l'est plus. */
    public static final class WorldTracking {
        public final boolean tracked;
        /** {@code "joined"}, {@code "practice"}, {@code "flagged"} ou {@code "left"}. */
        public final String reason;

        public WorldTracking(boolean tracked, String reason) {
            this.tracked = tracked;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return "WorldTracking{tracked=" + tracked + ", reason=" + reason + "}";
        }
    }
}
