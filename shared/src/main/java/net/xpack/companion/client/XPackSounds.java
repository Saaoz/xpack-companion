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
    static final List<String> PRESETS = List.of("carillon", "cloche", "fanfare", "off");
    static final List<String> RARITIES = List.of("commun", "peu_commun", "rare", "epique", "legendaire");

    private record Note(long tick, SoundEvent sound, float pitch, float volume) {}

    /** Accord majeur, en demi-tons au-dessus de la note de base. */
    private static final int[] ARPEGGIO = {0, 4, 7, 12};
    private static final int NOTE_GAP_TICKS = 3;

    private static final List<Note> pending = new ArrayList<>();
    private static long now;

    private XPackSounds() {}

    static void play(Config c, String rarity) {
        play(c.sound, c.soundVolume, rarity);
    }

    static void play(String preset, int volumePercent, String rarity) {
        if ("off".equals(preset) || volumePercent <= 0) return;
        float volume = Math.min(volumePercent, 100) / 100f;
        int rank = Math.max(0, RARITIES.indexOf(rarity));
        pending.clear(); // un nouveau son remplace celui en cours
        switch (preset) {
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
