package com.universal.reconciliation.examples.harness.ingestion;

import java.util.Locale;

final class CliOptions {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final String DEFAULT_USERNAME = "admin1";
    private static final String DEFAULT_PASSWORD = "password";

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String scenario;

    private CliOptions(String baseUrl, String username, String password, String scenario) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.scenario = scenario;
    }

    static CliOptions parse(String[] args) {
        String baseUrl = DEFAULT_BASE_URL;
        String username = DEFAULT_USERNAME;
        String password = DEFAULT_PASSWORD;
        String scenario = "all";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--base-url" -> baseUrl = requireValue(arg, args, ++i);
                case "--username" -> username = requireValue(arg, args, ++i);
                case "--password" -> password = requireValue(arg, args, ++i);
                case "--scenario" -> scenario = requireValue(arg, args, ++i).toLowerCase(Locale.ROOT);
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> {
                    System.err.printf("Unknown option '%s'.%n", arg);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        return new CliOptions(baseUrl, username, password, scenario);
    }

    String baseUrl() {
        return baseUrl;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }

    String scenario() {
        return scenario;
    }

    private static String requireValue(String option, String[] args, int index) {
        if (index >= args.length) {
            System.err.printf("Missing value for option '%s'.%n", option);
            printUsage();
            System.exit(1);
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar integration-ingestion-cli.jar [options]\n" +
                "Options:\n" +
                "  --scenario <cash-vs-gl|custodian-trade|securities-position|all>  Scenario to ingest (default: all)\n" +
                "  --base-url <url>                                               Platform base URL (default: http://localhost:8080)\n" +
                "  --username <username>                                          Login username (default: admin1)\n" +
                "  --password <password>                                          Login password (default: password)\n" +
                "  -h, --help                                                     Show this help text");
    }
}
