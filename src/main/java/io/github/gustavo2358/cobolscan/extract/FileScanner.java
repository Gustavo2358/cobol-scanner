package io.github.gustavo2358.cobolscan.extract;

import io.github.gustavo2358.cobolscan.output.ScanResult;
import io.github.gustavo2358.cobolscan.scan.Token;
import java.util.*;

public final class FileScanner {
  public void scan(List<Token> ts, ScanResult result) {
    for (int i = 0; i < ts.size(); i++) {
      if (ts.get(i).is("EXEC")) {
        while (i < ts.size() && !ts.get(i).is("END-EXEC")) i++;
        continue;
      }
      if (!ts.get(i).is("SELECT")) continue;
      int j = i + 1;
      while (j < ts.size() && !ts.get(j).is(".") && !ts.get(j).is("ASSIGN")) j++;
      if (j == ts.size() || !ts.get(j).is("ASSIGN")) continue;
      j++;
      if (j < ts.size() && ts.get(j).is("TO")) j++;
      while (j < ts.size()
          && (ts.get(j).is("EXTERNAL") || ts.get(j).is("DYNAMIC") || ts.get(j).is("DISK"))) {
        if (ts.get(j).is("DYNAMIC")) result.partial("Dynamic ASSIGN name requires runtime value");
        j++;
      }
      if (j < ts.size()
          && (ts.get(j).kind() == Token.Kind.WORD || ts.get(j).kind() == Token.Kind.STRING)) {
        Token name = ts.get(j);
        result.externalFileNames.add(
            name.kind() == Token.Kind.STRING ? name.value() : name.upper());
      } else result.partial("ASSIGN without supported external file name");
    }
  }
}
