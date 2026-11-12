package net.xpack.companion.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Préréglages de speedrun livrés avec le mod (#222) : des parcours d'étapes prêts à l'emploi. Pur Java ; les
 * textes (noms) sont dans les fichiers de langue, sous {@code xpack.speedrun.preset.<id>} et
 * {@code xpack.speedrun.step.<id de l'étape>}.
 *
 * <p>Chaque étape peut nommer un progrès Minecraft : en mode libre on la valide à la main, mais le mode classé
 * (#225) la validera tout seul. Une étape sans progrès (pas d'équivalent dans le jeu) reste manuelle.
 */
public final class SpeedrunPresets {
    /** Préréglage « personnalisé » : la liste saisie à la main dans l'écran. */
    public static final String CUSTOM = "custom";

    public static final class Step {
        public final String id;
        /** Progrès Minecraft qui valide l'étape, ou null si elle est manuelle. */
        public final String advancement;

        Step(String id, String advancement) {
            this.id = id;
            this.advancement = advancement;
        }
    }

    public static final class Preset {
        public final String id;
        public final List<Step> steps;

        Preset(String id, Step... steps) {
            this.id = id;
            List<Step> list = new ArrayList<>();
            Collections.addAll(list, steps);
            this.steps = Collections.unmodifiableList(list);
        }
    }

    /** Parcours de base : village, Nether, perle d'Ender, entrée dans l'End, dragon. */
    public static final Preset CLASSIC = new Preset("classic",
            new Step("village", null),
            new Step("nether", "minecraft:story/enter_the_nether"),
            new Step("pearl", null),
            new Step("end", "minecraft:story/enter_the_end"),
            new Step("dragon", "minecraft:end/kill_dragon"));

    /** Même idée, mais chaque étape correspond à un progrès du jeu : validable automatiquement. */
    public static final Preset PROGRESS = new Preset("progress",
            new Step("nether", "minecraft:story/enter_the_nether"),
            new Step("fortress", "minecraft:nether/find_fortress"),
            new Step("stronghold", "minecraft:story/follow_ender_eye"),
            new Step("end", "minecraft:story/enter_the_end"),
            new Step("dragon", "minecraft:end/kill_dragon"));

    public static final List<Preset> ALL = Collections.unmodifiableList(java.util.Arrays.asList(CLASSIC, PROGRESS));

    private SpeedrunPresets() {}

    /** Le préréglage d'identifiant donné, ou null (y compris pour {@link #CUSTOM}). */
    public static Preset find(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return null;
    }

    /** Identifiants proposés dans l'écran, « personnalisé » en premier. */
    public static List<String> ids() {
        List<String> out = new ArrayList<>();
        out.add(CUSTOM);
        for (Preset p : ALL) out.add(p.id);
        return out;
    }
}
