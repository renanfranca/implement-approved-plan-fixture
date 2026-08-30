package dev.renanfranca.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ReportCommandTest {
  @Test
  void reportsSuccessfulCountsInAStableOrder() {
    Execution execution = execute(
      "--passed", "2",
      "--failed", "0",
      "--skipped", "1"
    );

    assertEquals(0, execution.exitCode());
    assertEquals(
      """
      total=3
      passed=2
      failed=0
      skipped=1
      """,
      execution.output()
    );
  }

  @Test
  void returnsOneWhenTheReportContainsFailures() {
    Execution execution = execute(
      "--passed", "1",
      "--failed", "2",
      "--skipped", "0"
    );

    assertEquals(1, execution.exitCode());
    assertEquals(
      """
      total=3
      passed=1
      failed=2
      skipped=0
      """,
      execution.output()
    );
  }

  private Execution execute(String... arguments) {
    StringWriter output = new StringWriter();
    CommandLine commandLine = new CommandLine(new ReportCommand());
    commandLine.setOut(new PrintWriter(output));

    int exitCode = commandLine.execute(arguments);

    return new Execution(exitCode, output.toString());
  }

  private record Execution(int exitCode, String output) {
  }
}

