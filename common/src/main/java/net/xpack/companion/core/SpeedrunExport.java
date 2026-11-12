package net.xpack.companion.core;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Export d'un chrono libre terminé (#223) : JSON, CSV, fichier LiveSplit (.lss) et texte à coller dans une
 * soumission speedrun.com. Pur Java. Un chrono libre n'est pas vérifié : chaque export le dit.
 */
public final class SpeedrunExport {
    /** Une étape, en temps cumulés depuis le départ. */
    public static final class Seg {
        public final String name;
        public final long igtMs;
        public final long rtaMs;

        public Seg(String name, long igtMs, long rtaMs) {
            this.name = name;
            this.igtMs = igtMs;
            this.rtaMs = rtaMs;
        }
    }

    public static final class Run {
        public String category = "Libre";
        public String minecraftVersion = "";
        public String loader = "";
        /** Nom du modpack détecté, ou null. */
        public String modpack;
        public long startedAtMs;
        public long finishedAtMs;
        public long igtMs;
        public long rtaMs;
        public final List<Seg> splits = new ArrayList<>();
    }

    private SpeedrunExport() {}

    /** Les données d'un chrono terminé ; null si le chrono n'est pas terminé (en cours, perdu ou à zéro). */
    public static Run from(SpeedrunTimer t, String category, String minecraftVersion, String loader, String modpack, long finishedAtMs) {
        if (t.state() != SpeedrunTimer.State.FINISHED) return null;
        Run r = new Run();
        r.category = category;
        r.minecraftVersion = minecraftVersion;
        r.loader = loader;
        r.modpack = modpack;
        r.startedAtMs = t.startedAtMs();
        r.finishedAtMs = finishedAtMs;
        r.igtMs = t.igtMs();
        r.rtaMs = t.rtaMs();
        for (SpeedrunTimer.Split s : t.splits()) r.splits.add(new Seg(s.name, s.igtTicks * 50, s.rtaMs));
        return r;
    }

    // ------------------------------------------------------------------ formats

    public static String json(Run r) {
        JsonObject o = new JsonObject();
        o.addProperty("format", "xpack-speedrun");
        o.addProperty("version", 1);
        o.addProperty("mode", "free");
        o.addProperty("verified", false);
        o.addProperty("game", "Minecraft");
        o.addProperty("minecraft", r.minecraftVersion);
        o.addProperty("loader", r.loader);
        if (r.modpack != null) o.addProperty("modpack", r.modpack);
        o.addProperty("category", r.category);
        o.addProperty("startedAt", iso(r.startedAtMs));
        o.addProperty("finishedAt", iso(r.finishedAtMs));
        o.addProperty("igtMs", r.igtMs);
        o.addProperty("rtaMs", r.rtaMs);
        JsonArray arr = new JsonArray();
        long prevIgt = 0;
        long prevRta = 0;
        for (Seg s : r.splits) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name);
            j.addProperty("igtMs", s.igtMs);
            j.addProperty("rtaMs", s.rtaMs);
            j.addProperty("segmentIgtMs", s.igtMs - prevIgt);
            j.addProperty("segmentRtaMs", s.rtaMs - prevRta);
            prevIgt = s.igtMs;
            prevRta = s.rtaMs;
            arr.add(j);
        }
        o.add("splits", arr);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
    }

    public static String csv(Run r) {
        StringBuilder sb = new StringBuilder("step,igt_ms,rta_ms,segment_igt_ms,segment_rta_ms\n");
        long prevIgt = 0;
        long prevRta = 0;
        for (Seg s : r.splits) {
            sb.append(csvCell(s.name)).append(',').append(s.igtMs).append(',').append(s.rtaMs).append(',')
                    .append(s.igtMs - prevIgt).append(',').append(s.rtaMs - prevRta).append('\n');
            prevIgt = s.igtMs;
            prevRta = s.rtaMs;
        }
        sb.append("TOTAL,").append(r.igtMs).append(',').append(r.rtaMs).append(',').append(r.igtMs - prevIgt).append(',').append(r.rtaMs - prevRta).append('\n');
        return sb.toString();
    }

    /** Fichier LiveSplit : une tentative et un segment par étape (temps cumulé et durée), IGT en « GameTime ». */
    public static String lss(Run r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Run version=\"1.7.0\">\n");
        sb.append("  <GameIcon />\n  <GameName>Minecraft</GameName>\n");
        sb.append("  <CategoryName>").append(xml(r.category)).append("</CategoryName>\n");
        sb.append("  <Metadata>\n    <Run id=\"\" />\n    <Platform usesEmulator=\"False\"></Platform>\n    <Region></Region>\n    <Variables />\n  </Metadata>\n");
        sb.append("  <Offset>00:00:00</Offset>\n  <AttemptCount>1</AttemptCount>\n  <AttemptHistory>\n");
        sb.append("    <Attempt id=\"1\" started=\"").append(lssDate(r.startedAtMs)).append("\" isStartedSynced=\"True\" ended=\"")
                .append(lssDate(r.finishedAtMs)).append("\" isEndedSynced=\"True\">\n");
        sb.append("      <RealTime>").append(lssTime(r.rtaMs)).append("</RealTime>\n      <GameTime>").append(lssTime(r.igtMs)).append("</GameTime>\n    </Attempt>\n");
        sb.append("  </AttemptHistory>\n  <Segments>\n");
        long prevIgt = 0;
        long prevRta = 0;
        for (Seg s : r.splits) {
            String segRta = lssTime(s.rtaMs - prevRta);
            String segIgt = lssTime(s.igtMs - prevIgt);
            sb.append("    <Segment>\n      <Name>").append(xml(s.name)).append("</Name>\n      <Icon />\n");
            sb.append("      <SplitTimes>\n        <SplitTime name=\"Personal Best\">\n          <RealTime>").append(lssTime(s.rtaMs))
                    .append("</RealTime>\n          <GameTime>").append(lssTime(s.igtMs)).append("</GameTime>\n        </SplitTime>\n      </SplitTimes>\n");
            sb.append("      <BestSegmentTime>\n        <RealTime>").append(segRta).append("</RealTime>\n        <GameTime>").append(segIgt).append("</GameTime>\n      </BestSegmentTime>\n");
            sb.append("      <SegmentHistory>\n        <Time id=\"1\">\n          <RealTime>").append(segRta).append("</RealTime>\n          <GameTime>").append(segIgt)
                    .append("</GameTime>\n        </Time>\n      </SegmentHistory>\n    </Segment>\n");
            prevIgt = s.igtMs;
            prevRta = s.rtaMs;
        }
        sb.append("  </Segments>\n  <AutoSplitterSettings />\n</Run>\n");
        return sb.toString();
    }

    /** Texte prêt à coller dans le commentaire d'une soumission speedrun.com (en anglais, la langue du site). */
    public static String text(Run r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Minecraft ").append(r.minecraftVersion);
        if (!r.loader.isEmpty()) sb.append(" (").append(r.loader).append(')');
        if (r.modpack != null) sb.append(" - modpack: ").append(r.modpack);
        sb.append("\nCategory: ").append(r.category).append('\n');
        sb.append("IGT: ").append(SpeedrunTimer.format(r.igtMs)).append(" | RTA: ").append(SpeedrunTimer.format(r.rtaMs)).append('\n');
        if (!r.splits.isEmpty()) {
            sb.append("Splits (IGT / RTA):\n");
            for (Seg s : r.splits) sb.append("- ").append(s.name).append(": ").append(SpeedrunTimer.format(s.igtMs)).append(" / ").append(SpeedrunTimer.format(s.rtaMs)).append('\n');
        }
        sb.append("Timed with the XPack Companion mod, free mode (not verified by XPack).\n");
        return sb.toString();
    }

    /** Écrit les quatre fichiers dans {@code dir} et renvoie leurs chemins (JSON, CSV, LSS, texte). */
    public static List<Path> write(Path dir, Run r) throws IOException {
        Files.createDirectories(dir);
        String base = "speedrun-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(r.finishedAtMs));
        List<Path> out = new ArrayList<>();
        out.add(J8.writeString(dir.resolve(base + ".json"), json(r), StandardCharsets.UTF_8));
        out.add(J8.writeString(dir.resolve(base + ".csv"), csv(r), StandardCharsets.UTF_8));
        out.add(J8.writeString(dir.resolve(base + ".lss"), lss(r), StandardCharsets.UTF_8));
        out.add(J8.writeString(dir.resolve(base + ".txt"), text(r), StandardCharsets.UTF_8));
        return out;
    }

    // ------------------------------------------------------------------ détails

    private static String iso(long ms) {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms));
    }

    private static String lssDate(long ms) {
        return DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(ms));
    }

    /** « HH:MM:SS.fffffff » comme LiveSplit. */
    static String lssTime(long ms) {
        long abs = Math.max(0, ms);
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%07d", abs / 3_600_000, abs / 60_000 % 60, abs / 1000 % 60, abs % 1000 * 10_000);
    }

    private static String csvCell(String s) {
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
