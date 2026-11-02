package net.xpack.companion.forge1122;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;
import net.xpack.companion.core.Config;
import net.xpack.companion.core.J8;

/**
 * Son d'un succès XPack (#59), joué avec son toast, comme dans les autres versions : uniquement des
 * sons du jeu. Plus le succès est rare, plus il y a de notes ; un légendaire termine par un accord.
 */
final class Sounds1122 {
    /** Sons proposés dans les réglages, dans l'ordre du bouton. */
    static final List<String> PRESETS = J8.listOf("carillon", "cloche", "fanfare", "off");
    static final List<String> RARITIES = J8.listOf("commun", "peu_commun", "rare", "epique", "legendaire");

    private static final class Note {
        final long tick;
        final SoundEvent sound;
        final float pitch;
        final float volume;

        Note(long tick, SoundEvent sound, float pitch, float volume) {
            this.tick = tick;
            this.sound = sound;
            this.pitch = pitch;
            this.volume = volume;
        }
    }

    /** Accord majeur, en demi-tons au-dessus de la note de base. */
    private static final int[] ARPEGGIO = {0, 4, 7, 12};
    private static final int NOTE_GAP_TICKS = 3;

    private static final List<Note> pending = new ArrayList<>();
    private static long now;

    private Sounds1122() {}

    static void play(Config c, String rarity) {
        play(c.sound, c.soundVolume, rarity);
    }

    static void play(String preset, int volumePercent, String rarity) {
        if ("off".equals(preset) || volumePercent <= 0) return;
        float volume = Math.min(volumePercent, 100) / 100f;
        int rank = Math.max(0, RARITIES.indexOf(rarity));
        pending.clear(); // un nouveau son remplace celui en cours
        switch (preset) {
            case "cloche":
                for (int i = 0; i <= Math.min(rank, 3); i++) note(i * NOTE_GAP_TICKS * 2, SoundEvents.BLOCK_NOTE_BELL, semitones(ARPEGGIO[i] - 5), volume);
                if (rank == 4) note(8 * NOTE_GAP_TICKS, SoundEvents.BLOCK_NOTE_BELL, 1.2f, volume * 0.5f);
                break;
            case "fanfare":
                if (rank >= 3) note(0, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, rank == 4 ? 1.15f : 1.3f, volume * 0.7f);
                else note(0, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.1f + rank * 0.15f, volume * 0.6f);
                break;
            default:
                for (int i = 0; i <= Math.min(rank, 3); i++) note(i * NOTE_GAP_TICKS, SoundEvents.BLOCK_NOTE_CHIME, semitones(ARPEGGIO[i]), volume);
                if (rank == 4) note(4 * NOTE_GAP_TICKS, SoundEvents.BLOCK_NOTE_CHIME, 1.5f, volume);
        }
    }

    /** Modpack terminé à 100 % (#60) : fanfare du jeu, puis arpège sur deux octaves. */
    static void playCompletion(Config c) {
        if ("off".equals(c.sound) || c.soundVolume <= 0) return;
        float volume = Math.min(c.soundVolume, 100) / 100f;
        pending.clear();
        note(0, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, volume * 0.8f);
        int[] run = {0, 4, 7, 12, 16, 19, 24};
        for (int i = 0; i < run.length; i++) note(10 + i * 2L, SoundEvents.BLOCK_NOTE_CHIME, semitones(run[i] - 12), volume);
        note(10 + run.length * 2L + 2, SoundEvents.BLOCK_NOTE_CHIME, 1.5f, volume);
        note(10 + run.length * 2L + 2, SoundEvents.BLOCK_NOTE_BELL, 1.5f, volume * 0.4f);
    }

    /** Appelé à chaque tick client : joue les notes arrivées à échéance. */
    static void tick(Minecraft mc) {
        now++;
        if (pending.isEmpty()) return;
        for (Iterator<Note> it = pending.iterator(); it.hasNext(); ) {
            Note n = it.next();
            if (n.tick > now) continue;
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(n.sound, n.pitch, n.volume));
            it.remove();
        }
    }

    private static void note(long delayTicks, SoundEvent sound, float pitch, float volume) {
        pending.add(new Note(now + delayTicks, sound, pitch, volume));
    }

    private static float semitones(int n) {
        return (float) Math.pow(2, n / 12.0);
    }
}
