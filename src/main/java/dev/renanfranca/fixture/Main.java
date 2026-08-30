package dev.renanfranca.fixture;

import picocli.CommandLine;

public final class Main {
  private Main() {
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new ReportCommand()).execute(args);
    System.exit(exitCode);
  }
}

