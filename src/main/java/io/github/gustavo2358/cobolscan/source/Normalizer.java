package io.github.gustavo2358.cobolscan.source;

import io.github.gustavo2358.cobolscan.output.ScanResult;

public final class Normalizer {
  private static final java.util.Set<String> COMMENT_ENTRIES =
      java.util.Set.of(
          "AUTHOR", "INSTALLATION", "DATE-WRITTEN", "DATE-COMPILED", "SECURITY", "REMARKS");

  public static String normalize(String raw, String format, ScanResult result) {
    raw = raw.replace("\r\n", "\n").replace('\r', '\n');
    if (raw.startsWith("\ufeff")) raw = raw.substring(1);
    StringBuilder out = new StringBuilder(raw.length());
    boolean fixed = format.equals("fixed");
    char quote = 0;
    boolean identification = false, commentEntry = false;
    for (String line : raw.split("\n", -1)) {
      // Recognize unmistakable whole-line markers before fixed columns can erase them.
      // This also tolerates fixed comment records in a file configured as free.
      if (line.stripLeading().startsWith("*>") || fixedCommentRecord(line)) {
        out.append('\n');
        continue;
      }
      String body = line;
      boolean continuation = false;
      if (fixed) {
        if (line.length() < 7) {
          out.append('\n');
          continue;
        }
        char indicator = line.charAt(6);
        if (indicator == '*' || indicator == '/') {
          out.append('\n');
          continue;
        }
        if (indicator == 'D' || indicator == 'd') {
          /* Include debug lines conservatively. */
        } else if (indicator == '-') continuation = true;
        else if (indicator != ' ') result.partial("Unknown fixed indicator: " + indicator);
        body = line.substring(7, Math.min(72, line.length()));
      }
      String directive = body.strip().toUpperCase(java.util.Locale.ROOT);
      if (fixed && quote == 0) {
        boolean areaA = !body.substring(0, Math.min(4, body.length())).isBlank();
        if (commentEntry && !areaA) {
          out.append('\n');
          continue;
        }
        if (areaA) commentEntry = false;
        if (directive.startsWith("IDENTIFICATION DIVISION") || directive.startsWith("ID DIVISION"))
          identification = true;
        if (directive.startsWith("ENVIRONMENT DIVISION")
            || directive.startsWith("DATA DIVISION")
            || directive.startsWith("PROCEDURE DIVISION")) identification = false;
        int period = directive.indexOf('.');
        if (identification
            && areaA
            && period >= 0
            && COMMENT_ENTRIES.contains(directive.substring(0, period).strip())) {
          commentEntry = true;
          out.append('\n');
          continue;
        }
      }
      if (directive.startsWith(">>SOURCE")) {
        if (directive.endsWith("FREE")) fixed = false;
        else if (directive.endsWith("FIXED")) fixed = true;
        else result.partial("Unsupported source directive: " + directive);
        out.append('\n');
        continue;
      }
      if (continuation) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n')
          out.setLength(out.length() - 1);
        body = body.stripLeading();
        if (quote != 0) {
          if (!body.isEmpty() && body.charAt(0) == quote) body = body.substring(1);
          else result.partial("Literal continuation without opening quote");
        } else {
          while (out.length() > 0 && out.charAt(out.length() - 1) == ' ')
            out.setLength(out.length() - 1);
        }
      } else if (quote != 0) {
        result.partial("Unterminated literal before next line");
      }
      quote = quoteAfter(body, quote);
      out.append(body);
      if (fixed && quote != 0 && body.length() < 65) out.append(" ".repeat(65 - body.length()));
      out.append('\n');
    }
    return out.toString();
  }

  private static boolean fixedCommentRecord(String line) {
    if (line.length() < 7 || (line.charAt(6) != '*' && line.charAt(6) != '/')) return false;
    for (int i = 0; i < 6; i++)
      if (line.charAt(i) != ' ' && !Character.isDigit(line.charAt(i))) return false;
    return true;
  }

  private static char quoteAfter(String s, char quote) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (quote == 0 && c == '*' && i + 1 < s.length() && s.charAt(i + 1) == '>') break;
      if (quote == 0 && (c == '\'' || c == '"')) quote = c;
      else if (quote == c) {
        if (i + 1 < s.length() && s.charAt(i + 1) == c) i++;
        else quote = 0;
      }
    }
    return quote;
  }
}
