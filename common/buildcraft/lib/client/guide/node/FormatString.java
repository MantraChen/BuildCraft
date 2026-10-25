/* Copyright (c) 2017 SpaceToad and the BuildCraft team. MPL-2.0. */
package buildcraft.lib.client.guide.node;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Formatting;
import buildcraft.lib.client.guide.font.IFontRenderer;

/** Formatting-aware text that wraps without cutting format codes or Unicode surrogate pairs. */
public class FormatString {
    public final FormatSegment[] segments;
    private final String unformatted, formatted;

    public FormatString(FormatSegment[] segments) {
        this.segments = segments.clone();
        StringBuilder plain = new StringBuilder(), styled = new StringBuilder();
        for (FormatSegment segment : segments) {
            plain.append(segment.text);
            styled.append(segment.toFormatString());
        }
        unformatted = plain.toString();
        formatted = styled.toString();
    }

    public String getFormatted() { return formatted; }
    public String getUnformatted() { return unformatted; }

    public static FormatString split(String text) {
        List<FormatSegment> result = new ArrayList<>();
        Formatting colour = null;
        Set<Formatting> styles = EnumSet.noneOf(Formatting.class);
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < text.length();) {
            Formatting format = text.charAt(i) == '\u00a7' && i + 1 < text.length()
                ? Formatting.byCode(Character.toLowerCase(text.charAt(i + 1))) : null;
            if (format == null) {
                int cp = text.codePointAt(i);
                run.appendCodePoint(cp);
                i += Character.charCount(cp);
                continue;
            }
            if (run.length() > 0) {
                result.add(new FormatSegment(run.toString(), colour, Set.copyOf(styles)));
                run.setLength(0);
            }
            if (format == Formatting.RESET || format.isColor()) {
                colour = format == Formatting.RESET ? null : format;
                styles.clear();
            } else styles.add(format);
            i += 2;
        }
        if (run.length() > 0) result.add(new FormatSegment(run.toString(), colour, Set.copyOf(styles)));
        return new FormatString(result.toArray(FormatSegment[]::new));
    }

    public FormatString[] wrap(IFontRenderer font, int maxWidth) { return wrap(font, maxWidth, true); }

    /** Always consumes at least one code point, even when one glyph is wider than the column. */
    public FormatString[] wrap(IFontRenderer font, int maxWidth, boolean onWords) {
        if (unformatted.isEmpty()) return new FormatString[] { this };
        int fit = 0, wordBreak = 0;
        while (fit < unformatted.length()) {
            int cp = unformatted.codePointAt(fit);
            if (cp == '\n') return cut(fit, fit + 1);
            int next = fit + Character.charCount(cp);
            if (font.getWidth(slice(0, next).getFormatted()) > maxWidth) {
                if (fit == 0) fit = next;
                else if (onWords && wordBreak > 0) fit = wordBreak;
                return fit == unformatted.length() ? new FormatString[] { this } : cut(fit, fit);
            }
            fit = next;
            if (Character.isWhitespace(cp)) wordBreak = fit;
        }
        return new FormatString[] { this };
    }

    private FormatString[] cut(int end, int startNext) {
        return new FormatString[] { slice(0, end), slice(startNext, unformatted.length()) };
    }

    private FormatString slice(int from, int to) {
        List<FormatSegment> result = new ArrayList<>();
        int offset = 0;
        for (FormatSegment segment : segments) {
            int end = offset + segment.text.length();
            if (from < end && to > offset) {
                result.add(new FormatSegment(segment.text.substring(Math.max(from - offset, 0),
                    Math.min(to - offset, segment.text.length())), segment.colour, segment.misc));
            }
            offset = end;
            if (offset >= to) break;
        }
        return new FormatString(result.toArray(FormatSegment[]::new));
    }
}
