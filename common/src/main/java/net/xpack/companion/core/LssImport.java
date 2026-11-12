package net.xpack.companion.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Lecture d'un fichier LiveSplit (.lss) déposé par le joueur (#224) : nom de la catégorie, noms des étapes et,
 * si le fichier en contient, le meilleur temps (temps de jeu cumulé à chaque étape). Le fichier vient de
 * l'extérieur : taille bornée, aucune DTD ni entité externe (pas d'attaque XXE).
 */
public final class LssImport {
    /** Taille maximale lue : un .lss de plusieurs centaines de segments reste très en dessous. */
    static final long MAX_BYTES = 2L * 1024 * 1024;

    public static final class Result {
        public final String category;
        public final List<String> steps;
        /** IGT cumulé en ticks à chaque étape, ou une liste vide si le fichier n'a pas de meilleur temps complet. */
        public final List<Long> bestTicks;

        Result(String category, List<String> steps, List<Long> bestTicks) {
            this.category = category;
            this.steps = steps;
            this.bestTicks = bestTicks;
        }
    }

    /** Fichier illisible ou qui n'est pas un .lss : le message est destiné au joueur. */
    public static final class LssException extends Exception {
        private static final long serialVersionUID = 1L;

        LssException(String message) {
            super(message);
        }
    }

    private LssImport() {}

    public static Result read(Path file) throws IOException, LssException {
        if (Files.size(file) > MAX_BYTES) throw new LssException("too_big");
        return parse(Files.readAllBytes(file));
    }

    public static Result parse(byte[] xml) throws LssException {
        Document doc;
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setExpandEntityReferences(false);
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new LssException("not_xml");
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"Run".equals(root.getTagName())) throw new LssException("not_lss");
        String category = text(first(root, "CategoryName"));
        Element segments = first(root, "Segments");
        List<String> steps = new ArrayList<>();
        List<Long> best = new ArrayList<>();
        boolean bestComplete = true;
        if (segments != null) {
            NodeList list = segments.getElementsByTagName("Segment");
            for (int i = 0; i < list.getLength(); i++) {
                Element seg = (Element) list.item(i);
                String name = text(first(seg, "Name")).trim();
                steps.add(name.isEmpty() ? "Étape " + (i + 1) : name);
                Long ms = personalBestMs(seg);
                if (ms == null) bestComplete = false;
                else best.add(ms / 50);
            }
        }
        if (steps.isEmpty()) throw new LssException("no_segments");
        return new Result(category.trim(), steps, bestComplete ? best : new ArrayList<Long>());
    }

    /** Temps cumulé du « Personal Best » d'un segment : temps de jeu, sinon temps réel. */
    private static Long personalBestMs(Element segment) {
        Element splits = first(segment, "SplitTimes");
        if (splits == null) return null;
        NodeList list = splits.getElementsByTagName("SplitTime");
        for (int i = 0; i < list.getLength(); i++) {
            Element st = (Element) list.item(i);
            if (!"Personal Best".equals(st.getAttribute("name"))) continue;
            Long game = parseTime(text(first(st, "GameTime")));
            return game != null ? game : parseTime(text(first(st, "RealTime")));
        }
        return null;
    }

    /** « HH:MM:SS.fffffff » (ou sans fraction) en millisecondes ; null si vide ou illisible. */
    static Long parseTime(String s) {
        String t = s.trim();
        if (t.isEmpty()) return null;
        try {
            String[] hms = t.split(":");
            if (hms.length != 3) return null;
            double sec = Double.parseDouble(hms[2]);
            long h = Long.parseLong(hms[0]);
            long m = Long.parseLong(hms[1]);
            return h * 3_600_000 + m * 60_000 + Math.round(sec * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Element first(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static String text(Element e) {
        if (e == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) if (n.getNodeType() == Node.TEXT_NODE) sb.append(n.getNodeValue());
        return sb.toString();
    }
}
