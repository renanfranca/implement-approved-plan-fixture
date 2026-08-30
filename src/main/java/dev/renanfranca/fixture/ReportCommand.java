package dev.renanfranca.fixture;

import java.io.PrintWriter;
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
  @Option(names = "--passed", defaultValue = "0")
  private int passed;

  @Option(names = "--failed", defaultValue = "0")
  private int failed;

  @Option(names = "--skipped", defaultValue = "0")
  private int skipped;

  @Spec
  private CommandSpec spec;

  private final int[] counts = new int[3];

  @Override
  public Integer call() {
    counts[0] = passed;
    counts[1] = failed;
    counts[2] = skipped;
    int total = counts[0] + counts[1] + counts[2];
    StringBuilder report = new StringBuilder();
    report.append("total=");
    report.append(total);
    report.append(System.lineSeparator());
    report.append("passed=");
    report.append(counts[0]);
    report.append(System.lineSeparator());
    report.append("failed=");
    report.append(counts[1]);
    report.append(System.lineSeparator());
    report.append("skipped=");
    report.append(counts[2]);
    PrintWriter output = spec.commandLine().getOut();
    output.println(report);
    output.flush();
    if (counts[1] > 0) {
      return 1;
    }
    return 0;
  }
}

