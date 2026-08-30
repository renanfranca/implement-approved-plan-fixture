package dev.renanfranca.fixture;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(
  name = "report",
  mixinStandardHelpOptions = true,
  description = "Print a compact status report."
)
public final class ReportCommand implements Callable<Integer> {
  private static final String EXPECTED_ITEM = "nome=passed|failed|skipped";

  @Option(names = "--passed", defaultValue = "0")
  private int passed;

  @Option(names = "--failed", defaultValue = "0")
  private int failed;

  @Option(names = "--skipped", defaultValue = "0")
  private int skipped;

  @Option(names = "--item", paramLabel = EXPECTED_ITEM)
  private List<String> items = new ArrayList<>();

  @Spec
  private CommandSpec spec;

  @Override
  public Integer call() {
    List<String> normalizedItems = new ArrayList<>();
    Set<String> normalizedNames = new HashSet<>();
    int passedCount = passed;
    int failedCount = failed;
    int skippedCount = skipped;
    for (String item : items) {
      int separator = item.indexOf('=');
      if (separator < 0) {
        return invalidItem(item, "");
      }
      String name = item.substring(0, separator).trim();
      String status = item.substring(separator + 1).toLowerCase(Locale.ROOT);
      if (name.isEmpty()) {
        return invalidItem(item, "");
      }
      if (
        !"passed".equals(status)
          && !"failed".equals(status)
          && !"skipped".equals(status)
      ) {
        return invalidItem(item, "");
      }
      if (!normalizedNames.add(name.toLowerCase(Locale.ROOT))) {
        return invalidItem(item, "duplicate name; ");
      }
      if ("failed".equals(status)) {
        failedCount++;
      } else if ("skipped".equals(status)) {
        skippedCount++;
      } else {
        passedCount++;
      }
      normalizedItems.add(name + "=" + status);
    }
    normalizedItems.sort(String.CASE_INSENSITIVE_ORDER);
    int total = passedCount + failedCount + skippedCount;
    StringBuilder report = new StringBuilder();
    report.append("total=");
    report.append(total);
    report.append(System.lineSeparator());
    report.append("passed=");
    report.append(passedCount);
    report.append(System.lineSeparator());
    report.append("failed=");
    report.append(failedCount);
    report.append(System.lineSeparator());
    report.append("skipped=");
    report.append(skippedCount);
    for (String item : normalizedItems) {
      report.append(System.lineSeparator());
      report.append(item);
    }
    PrintWriter output = spec.commandLine().getOut();
    output.println(report);
    output.flush();
    if (failedCount > 0) {
      return 1;
    }
    return 0;
  }

  private int invalidItem(String item, String detail) {
    PrintWriter error = spec.commandLine().getErr();
    error.println(
      "Invalid item '" + item + "': " + detail + "expected " + EXPECTED_ITEM
    );
    error.flush();
    return 2;
  }
}
