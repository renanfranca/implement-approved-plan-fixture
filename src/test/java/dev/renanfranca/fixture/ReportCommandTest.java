package dev.renanfranca.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  @Test
  void reportsRepeatedPassedItems() {
    Execution execution = execute(
      "--item", "alpha=passed",
      "--item", "beta=passed"
    );

    assertEquals(0, execution.exitCode());
    assertEquals(
      """
      total=2
      passed=2
      failed=0
      skipped=0
      alpha=passed
      beta=passed
      """,
      execution.output()
    );
  }

  @Test
  void normalizesAnItemNameAndStatus() {
    Execution execution = execute("--item", "  Alpha  =SKiPpEd");

    assertEquals(0, execution.exitCode());
    assertEquals(
      """
      total=1
      passed=0
      failed=0
      skipped=1
      Alpha=skipped
      """,
      execution.output()
    );
  }

  @Test
  void returnsOneWhenAnyItemFailed() {
    Execution execution = execute(
      "--item", "alpha=passed",
      "--item", "beta=failed"
    );

    assertEquals(1, execution.exitCode());
    assertEquals(
      """
      total=2
      passed=1
      failed=1
      skipped=0
      alpha=passed
      beta=failed
      """,
      execution.output()
    );
  }

  @Test
  void ordersItemsByNameIgnoringCase() {
    Execution execution = execute(
      "--item", "Zulu=passed",
      "--item", "alpha=passed",
      "--item", "Bravo=passed"
    );

    assertEquals(0, execution.exitCode());
    assertEquals(
      """
      total=3
      passed=3
      failed=0
      skipped=0
      alpha=passed
      Bravo=passed
      Zulu=passed
      """,
      execution.output()
    );
  }

  @Test
  void rejectsAnEmptyItemNameWithoutAPartialReport() {
    Execution execution = execute(
      "--item", "valid=passed",
      "--item", "  =failed"
    );

    assertEquals(2, execution.exitCode());
    assertEquals("", execution.output());
    assertEquals(
      "Invalid item '  =failed': expected nome=passed|failed|skipped\n",
      execution.error()
    );
  }

  @Test
  void rejectsAnUnsupportedStatusWithoutAPartialReport() {
    Execution execution = execute(
      "--item", "valid=passed",
      "--item", "broken=unknown"
    );

    assertEquals(2, execution.exitCode());
    assertEquals("", execution.output());
    assertEquals(
      "Invalid item 'broken=unknown': expected nome=passed|failed|skipped\n",
      execution.error()
    );
  }

  @Test
  void rejectsDuplicateNamesIgnoringCaseWithoutAPartialReport() {
    Execution execution = execute(
      "--item", "Alpha=passed",
      "--item", "alpha=failed"
    );

    assertEquals(2, execution.exitCode());
    assertEquals("", execution.output());
    assertEquals(
      "Invalid item 'alpha=failed': duplicate name; "
        + "expected nome=passed|failed|skipped\n",
      execution.error()
    );
  }

  @Test
  void rejectsUnicodeDuplicateNamesIgnoringCaseWithoutAPartialReport() {
    Execution execution = execute(
      "--item", "İ=passed",
      "--item", "i=failed"
    );

    assertEquals(2, execution.exitCode());
    assertEquals("", execution.output());
    assertEquals(
      "Invalid item 'i=failed': duplicate name; "
        + "expected nome=passed|failed|skipped\n",
      execution.error()
    );
  }

  @Test
  void rejectsAMalformedItemWithoutAPartialReport() {
    Execution execution = execute(
      "--item", "valid=passed",
      "--item", "broken"
    );

    assertEquals(2, execution.exitCode());
    assertEquals("", execution.output());
    assertEquals(
      "Invalid item 'broken': expected nome=passed|failed|skipped\n",
      execution.error()
    );
  }

  @Test
  void rejectsAMissingItemValueWithTheExpectedShape() {
    Execution execution = execute("--item");

    assertEquals(2, execution.exitCode());
    assertEquals("", execution.output());
    assertTrue(
      execution.error().contains("nome=passed|failed|skipped"),
      execution.error()
    );
  }

  private Execution execute(String... arguments) {
    StringWriter output = new StringWriter();
    StringWriter error = new StringWriter();
    CommandLine commandLine = new CommandLine(new ReportCommand());
    commandLine.setOut(new PrintWriter(output));
    commandLine.setErr(new PrintWriter(error));

    int exitCode = commandLine.execute(arguments);

    return new Execution(exitCode, output.toString(), error.toString());
  }

  private record Execution(int exitCode, String output, String error) {
  }
}
