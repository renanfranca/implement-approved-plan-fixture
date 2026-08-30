package dev.renanfranca.fixture;

import java.io.PrintWriter;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
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
    try {
      Report report = buildReport();
      writeReport(report);
      return report.failed() > 0 ? 1 : 0;
    } catch (InvalidItemException exception) {
      return invalidItem(exception.item, exception.detail);
    }
  }

  private Report buildReport() {
    List<ReportItem> reportItems = parseItems();
    reportItems.sort((left, right) ->
      String.CASE_INSENSITIVE_ORDER.compare(left.name(), right.name())
    );
    return summarize(List.copyOf(reportItems));
  }

  private Report summarize(List<ReportItem> reportItems) {
    int passedCount = passed;
    int failedCount = failed;
    int skippedCount = skipped;
    for (ReportItem item : reportItems) {
      if (item.status() == Status.FAILED) {
        failedCount++;
      } else if (item.status() == Status.SKIPPED) {
        skippedCount++;
      } else {
        passedCount++;
      }
    }
    return new Report(reportItems, passedCount, failedCount, skippedCount);
  }

  private List<ReportItem> parseItems() {
    List<ReportItem> reportItems = new ArrayList<>();
    Set<String> normalizedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (String item : items) {
      ReportItem reportItem = parseItem(item);
      if (!normalizedNames.add(reportItem.name())) {
        throw new InvalidItemException(item, "duplicate name; ");
      }
      reportItems.add(reportItem);
    }
    return reportItems;
  }

  private ReportItem parseItem(String item) {
    int separator = item.indexOf('=');
    if (separator < 0) {
      throw new InvalidItemException(item, "");
    }
    String name = item.substring(0, separator).trim();
    if (name.isEmpty()) {
      throw new InvalidItemException(item, "");
    }
    String status = item.substring(separator + 1).toLowerCase(Locale.ROOT);
    return new ReportItem(name, parseStatus(item, status));
  }

  private Status parseStatus(String item, String status) {
    return switch (status) {
      case "passed" -> Status.PASSED;
      case "failed" -> Status.FAILED;
      case "skipped" -> Status.SKIPPED;
      default -> throw new InvalidItemException(item, "");
    };
  }

  private void writeReport(Report report) {
    List<String> lines = new ArrayList<>();
    lines.add("total=" + report.total());
    lines.add("passed=" + report.passed());
    lines.add("failed=" + report.failed());
    lines.add("skipped=" + report.skipped());
    for (ReportItem item : report.items()) {
      lines.add(item.name() + "=" + item.status().label);
    }
    PrintWriter output = spec.commandLine().getOut();
    output.println(String.join(System.lineSeparator(), lines));
    output.flush();
  }

  private int invalidItem(String item, String detail) {
    PrintWriter error = spec.commandLine().getErr();
    error.println(
      "Invalid item '" + item + "': " + detail + "expected " + EXPECTED_ITEM
    );
    error.flush();
    return 2;
  }

  private record Report(
    List<ReportItem> items,
    int passed,
    int failed,
    int skipped
  ) {
    private int total() {
      return passed + failed + skipped;
    }
  }

  private record ReportItem(String name, Status status) {
  }

  private enum Status {
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped");

    private final String label;

    Status(String label) {
      this.label = label;
    }
  }

  private static final class InvalidItemException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String item;
    private final String detail;

    InvalidItemException(String item, String detail) {
      this.item = item;
      this.detail = detail;
    }
  }
}
