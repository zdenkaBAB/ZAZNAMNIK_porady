package sk.meetingmanual.app;

import java.util.*;
import java.util.regex.Pattern;

/** Local, deterministic first-pass meeting manual builder. No network/AI service required. */
public final class ManualBuilder {
    private ManualBuilder() {}

    public static String build(String title, String transcript) {
        String t = transcript == null ? "" : transcript.trim();
        if (t.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        out.append("MANUÁL – ").append(title == null || title.trim().isEmpty() ? "STRETNUTIE" : title.trim()).append("\n\n");
        out.append("1. HLAVNÉ POKYNY A ÚLOHY\n");
        appendMatching(out, t, new String[]{"úloha", "úlohou", "úlohy", "treba", "musíme", "zabezpečiť", "vykonať", "pripraviť", "spraviť", "urobiť", "zodpovednosť"});
        out.append("\n2. TERMÍNY A LEHOTY\n");
        appendMatching(out, t, new String[]{"termín", "do ", "dátum", "deadline", "najneskôr", "od ", "do:"});
        out.append("\n3. ZODPOVEDNOSTI\n");
        appendMatching(out, t, new String[]{"zodpovedný", "zodpovedná", "bude mať na starosti", "zabezpečí", "vedúci", "pracovník", "oddelenie"});
        out.append("\n4. KONKRÉTNE POSTUPY A POKYNY\n");
        appendMatching(out, t, new String[]{"postup", "najprv", "potom", "následne", "vyplniť", "skontrolovať", "odoslať", "nahrať", "spracovať", "overiť"});
        out.append("\n5. OTVORENÉ OTÁZKY / DORIEŠENIE\n");
        appendMatching(out, t, new String[]{"otázka", "otázky", "nevieme", "doriešiť", "doriešenie", "upresniť", "overiť", "čakáme", "nie je jasné"});
        out.append("\n6. KOMPLETNÝ PREPIS\n").append(t);
        return out.toString();
    }

    private static void appendMatching(StringBuilder out, String transcript, String[] keys) {
        String[] lines = transcript.split("\\R");
        Set<String> added = new LinkedHashSet<>();
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            String low = l.toLowerCase(Locale.ROOT);
            for (String k : keys) {
                if (low.contains(k)) { added.add(l); break; }
            }
        }
        if (added.isEmpty()) out.append("• Z tejto časti prepisu nebol automaticky identifikovaný konkrétny bod.\n");
        else for (String s : added) out.append("• ").append(s).append("\n");
    }
}
