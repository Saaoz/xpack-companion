package net.xpack.companion.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.xpack.companion.core.Config;

/**
 * Son d'un succès XPack (#59), joué avec son toast. Uniquement des sons du jeu : rien à déclarer
 * dans {@code sounds.json}, donc rien qui dépende du chargement des ressources des mods (absent sur
 * Fabric sans Fabric API). Plus le succès est rare, plus il y a de notes : un succès commun reste
 * discret, un légendaire termine par un accord.
 */
final class XPackSounds {
    /** Sons proposés dans les réglages, dans l'ordre du bouton. */
    static final List<String> PRESETS = List.of("gourmand", "carillon", "cloche", "fanfare", "off");
    static final List<String> RARITIES = List.of("commun", "peu_commun", "rare", "epique", "legendaire");

    private record Note(long tick, SoundEvent sound, float pitch, float volume) {}

    /** Accord majeur, en demi-tons au-dessus de la note de base. */
    private static final int[] ARPEGGIO = {0, 4, 7, 12};
    private static final int NOTE_GAP_TICKS = 3;

    private static final List<Note> pending = new ArrayList<>();
    private static long now;

    /** Combo du son « gourmand » (#204) : succès débloqués à moins de COMBO_WINDOW_MS d'écart. */
    private static final long COMBO_WINDOW_MS = 10_000;
    private static final int COMBO_MAX_STEPS = 6;
    /** À partir de ce nombre de succès dans un seul toast, un seul son sans combo (pas de rafale). */
    private static final int BURST = 4;
    private static long lastUnlockAt;
    private static int combo;

    private XPackSounds() {}

    static void play(Config c, String rarity) {
        play(c.sound, c.soundVolume, rarity);
    }

    /** Réglages : l'écoute d'un aperçu ne doit pas compter comme un succès enchaîné. */
    static void resetCombo() {
        combo = 0;
        lastUnlockAt = 0;
    }

    /** Son d'un toast qui regroupe {@code count} succès (le plus rare donne la rareté). */
    static void play(Config c, String rarity, int count) {
        if ("gourmand".equals(c.sound) && count >= BURST) {
            // Lot (import, retour dans un monde) : un seul son marqué, sans toucher au combo.
            playGourmand(c.soundVolume, rarity, 0, true);
            return;
        }
        play(c.sound, c.soundVolume, rarity);
    }

    static void play(String preset, int volumePercent, String rarity) {
        if ("off".equals(preset) || volumePercent <= 0) return;
        float volume = Math.min(volumePercent, 100) / 100f;
        int rank = Math.max(0, RARITIES.indexOf(rarity));
        pending.clear(); // un nouveau son remplace celui en cours
        switch (preset) {
            case "gourmand" -> {
                long t = System.currentTimeMillis();
                combo = t - lastUnlockAt <= COMBO_WINDOW_MS ? Math.min(combo + 1, COMBO_MAX_STEPS) : 0;
                lastUnlockAt = t;
                playGourmand(volumePercent, rarity, combo, false);
            }
            case "cloche" -> {
                SoundEvent bell = SoundEvents.NOTE_BLOCK_BELL.value();
                for (int i = 0; i <= Math.min(rank, 3); i++) note(i * NOTE_GAP_TICKS * 2, bell, semitones(ARPEGGIO[i] - 5), volume);
                if (rank == 4) note(8 * NOTE_GAP_TICKS, SoundEvents.BELL_BLOCK, 1.2f, volume * 0.5f);
            }
            case "fanfare" -> {
                if (rank >= 3) note(0, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, rank == 4 ? 1.15f : 1.3f, volume * 0.7f);
                else note(0, SoundEvents.PLAYER_LEVELUP, 1.1f + rank * 0.15f, volume * 0.6f);
            }
            default -> {
                SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
                for (int i = 0; i <= Math.min(rank, 3); i++) note(i * NOTE_GAP_TICKS, chime, semitones(ARPEGGIO[i]), volume);
                if (rank == 4) note(4 * NOTE_GAP_TICKS, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, volume);
            }
        }
    }

    /**
     * Son « gourmand » (#204) : le « ping » de l'orbe d'expérience, plus aigu à chaque succès enchaîné
     * (combo, +2 demi-tons, jusqu'à une octave). La rareté ajoute de la matière : un scintillement
     * d'améthyste dès « rare », un accord en cascade pour l'épique, un éclat de feu d'artifice et
     * une cloche grave pour le légendaire.
     */
    private static void playGourmand(int volumePercent, String rarity, int comboSteps, boolean burst) {
        if (volumePercent <= 0) return;
        float volume = Math.min(volumePercent, 100) / 100f;
        int rank = Math.max(0, RARITIES.indexOf(rarity));
        pending.clear();
        int lift = comboSteps * 2;
        note(0, SoundEvents.EXPERIENCE_ORB_PICKUP, semitones(lift), volume * 0.9f);
        if (burst) {
            note(3, SoundEvents.EXPERIENCE_ORB_PICKUP, semitones(lift + 7), volume * 0.8f);
            note(6, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, volume * 0.7f);
            return;
        }
        if (rank >= 1) note(3, SoundEvents.EXPERIENCE_ORB_PICKUP, semitones(lift + 4), volume * 0.8f);
        if (rank >= 2) note(5, SoundEvents.AMETHYST_BLOCK_CHIME, semitones(lift + 7), volume * 0.8f);
        if (rank >= 3) {
            note(7, SoundEvents.NOTE_BLOCK_CHIME.value(), semitones(lift + 12), volume);
            note(9, SoundEvents.AMETHYST_BLOCK_CHIME, semitones(lift + 16), volume * 0.8f);
        }
        if (rank == 4) {
            note(8, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.3f, volume * 0.6f);
            note(10, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, volume * 0.7f);
            note(12, SoundEvents.BELL_BLOCK, 0.8f, volume * 0.5f);
        }
    }

    /**
     * Modpack terminé à 100 % (#60) : plus marqué que le plus rare des succès, quel que soit le son
     * choisi (le volume et « aucun son » sont respectés). Fanfare du jeu, puis arpège sur deux octaves.
     */
    static void playCompletion(Config c) {
        if ("off".equals(c.sound) || c.soundVolume <= 0) return;
        float volume = Math.min(c.soundVolume, 100) / 100f;
        pending.clear();
        note(0, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, volume * 0.8f);
        SoundEvent chime = SoundEvents.NOTE_BLOCK_CHIME.value();
        int[] run = {0, 4, 7, 12, 16, 19, 24};
        for (int i = 0; i < run.length; i++) note(10 + i * 2L, chime, semitones(run[i] - 12), volume);
        note(10 + run.length * 2L + 2, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, volume);
        note(10 + run.length * 2L + 2, SoundEvents.BELL_BLOCK, 1.5f, volume * 0.4f);
    }

    /** Appelé à chaque tick client : joue les notes arrivées à échéance. */
    static void tick(Minecraft mc) {
        now++;
        if (pending.isEmpty()) return;
        pending.removeIf(n -> {
            if (n.tick() > now) return false;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(n.sound(), n.pitch(), n.volume()));
            return true;
        });
    }

    private static void note(long delayTicks, SoundEvent sound, float pitch, float volume) {
        pending.add(new Note(now + delayTicks, sound, pitch, volume));
    }

    private static float semitones(int n) {
        return (float) Math.pow(2, n / 12.0);
    }
}
