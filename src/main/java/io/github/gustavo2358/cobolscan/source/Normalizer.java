package io.github.gustavo2358.cobolscan.source;

import io.github.gustavo2358.cobolscan.output.ScanResult;

public final class Normalizer {
  public static String normalize(String raw, String format, ScanResult result) {
    raw = raw.replace("\r\n", "\n").replace('\r', '\n');
    if (raw.startsWith("\ufeff")) raw = raw.substring(1);
    StringBuilder out = new StringBuilder(raw.length());
    boolean fixed = format.equals("fixed");
    char quote = 0;
    for (String line : raw.split("\n", -1)) {
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
