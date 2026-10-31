package net.xpack.companion.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Modes triche des mods (#49), lus par réflexion : aucune dépendance, et chaque lecture ne se fait
 * que si le mod est chargé. Signatures vérifiées sur JEI 15 (1.20.1) et 19 (1.21.1), REI 12, EMI
 * 1.1, FTB Quests 2001 et 2101. Une signature qui change désactive seulement ce contrôle.
 */
final class CheatModes {
    private CheatModes() {}

    private static final String JEI = "mezz.jei.common.Internal";
    private static final String REI = "me.shedaniel.rei.api.client.config.ConfigObject";
    private static final String EMI = "dev.emi.emi.config.EmiConfig";
    private static final String FTB_QUESTS = "dev.ftb.mods.ftbquests.client.ClientQuestFile";

    /** Modes allumés parmi les mods chargés : {@code jei}, {@code rei}, {@code emi}, {@code ftbquests}. */
    static Set<String> active(Collection<String> modIds) {
        Set<String> on = new HashSet<>();
        if (modIds.contains("jei") && Boolean.TRUE.equals(call(JEI, "getClientToggleState", "isCheatItemsEnabled"))) on.add("jei");
        if (modIds.contains("roughlyenoughitems") && Boolean.TRUE.equals(call(REI, "getInstance", "isCheating"))) on.add("rei");
        if (modIds.contains("emi") && Boolean.TRUE.equals(field(EMI, "cheatMode"))) on.add("emi");
        if (modIds.contains("ftbquests") && Boolean.TRUE.equals(call(FTB_QUESTS, "canClientPlayerEdit", null))) on.add("ftbquests");
        return on;
    }

    /** Éteint le mode ; FTB Quests passe par sa commande (le serveur décide du mode édition). */
    static void disable(String mode) {
        try {
            switch (mode) {
                case "jei" -> {
                    Method get = Class.forName(JEI).getMethod("getClientToggleState");
                    get.getReturnType().getMethod("setCheatItemsEnabled", boolean.class).invoke(get.invoke(null), false);
                }
                case "rei" -> {
                    Method get = Class.forName(REI).getMethod("getInstance");
                    get.getReturnType().getMethod("setCheating", boolean.class).invoke(get.invoke(null), false);
                }
                case "emi" -> Class.forName(EMI).getField("cheatMode").setBoolean(null, false);
                case "ftbquests" -> {
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection != null) connection.sendCommand("ftbquests editing_mode false");
                }
                default -> {}
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Signature inconnue : le joueur le désactive lui-même, la question revient sinon.
        }
    }

    /** {@code Classe.statique()} puis, si {@code then} est donné, {@code résultat.then()}. */
    private static Object call(String className, String staticMethod, String then) {
        try {
            // Méthode cherchée sur le type déclaré (interface publique), pas sur la classe concrète.
            Method get = Class.forName(className).getMethod(staticMethod);
            Object result = get.invoke(null);
            if (then == null || result == null) return result;
            return get.getReturnType().getMethod(then).invoke(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private static Object field(String className, String name) {
        try {
            Field f = Class.forName(className).getField(name);
            return f.get(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
