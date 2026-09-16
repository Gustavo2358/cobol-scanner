package io.github.gustavo2358.cobolscan.scan;

import static io.github.gustavo2358.cobolscan.scan.Token.Kind.*;

import java.util.*;

/** Linear micro-lexer. Quoted payloads are never reconsidered as statements. */
public final class Lexer {
  public static List<Token> lex(String text) {
    List<Token> out = new ArrayList<>();
    int i = 0, line = 1;
    boolean sql = false;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c)) {
        if (c == '\n') line++;
        i++;
        continue;
      }
      if (text.startsWith("*>", i) || (sql && text.startsWith("--", i))) {
        while (i < text.length() && text.charAt(i) != '\n') i++;
        continue;
      }
      if (sql && text.startsWith("/*", i)) {
        int start = i;
        i += 2;
        while (i < text.length() && !text.startsWith("*/", i)) {
          if (text.charAt(i++) == '\n') line++;
        }
        if (i == text.length())
          out.add(new Token(INVALID, text.substring(start), "unclosed SQL comment", line));
        else i += 2;
        continue;
      }
      int start = i, atLine = line;
      if (c == '\'' || c == '"') {
        StringBuilder value = new StringBuilder();
        i++;
        boolean closed = false;
        while (i < text.length()) {
          char v = text.charAt(i++);
          if (v == '\n') line++;
          if (v == c) {
            if (i < text.length() && text.charAt(i) == c) {
              value.append(c);
              i++;
            } else {
              closed = true;
              break;
            }
          } else value.append(v);
        }
        out.add(
            new Token(
                closed ? STRING : INVALID, text.substring(start, i), value.toString(), atLine));
        continue;
      }
      if (Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '@') {
        i++;
        while (i < text.length()) {
          char v = text.charAt(i);
          if (!(Character.isLetterOrDigit(v)
              || v == '-'
              || v == '_'
              || v == '$'
              || v == '#'
              || v == '@')) break;
          i++;
        }
        String word = text.substring(start, i);
        Token token =
            new Token(
                word.chars().allMatch(Character::isDigit) ? NUMBER : WORD, word, word, atLine);
        if (token.is("SQL") && !out.isEmpty() && out.get(out.size() - 1).is("EXEC")) sql = true;
        if (token.is("END-EXEC")) sql = false;
        out.add(token);
        continue;
      }
      i++;
      if (c == '=' && i < text.length() && text.charAt(i) == '=') i++;
      String symbol = text.substring(start, i);
      out.add(new Token(SYMBOL, symbol, symbol, line));
    }
    return out;
  }
}
