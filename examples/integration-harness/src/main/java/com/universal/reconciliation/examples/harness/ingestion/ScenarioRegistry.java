package com.universal.reconciliation.examples.harness.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScenarioRegistry {

    private static final Map<String, ScenarioDefinition> SCENARIOS = new LinkedHashMap<>();

    static {
        SCENARIOS.put(
                "cash-vs-gl",
                new ScenarioDefinition(
                        "cash-vs-gl",
                        "CASH_VS_GL_SIMPLE",
                        List.of(
                                new BatchDefinition(
                                        "CASH",
                                        "/data/cash-vs-gl/cash_source.csv",
                                        "Cash Anchor Batch",
                                        Map.of("delimiter", ",")),
                                new BatchDefinition(
                                        "GL",
                                        "/data/cash-vs-gl/gl_source.csv",
                                        "General Ledger Batch",
                                        Map.of("delimiter", ","))
                        )));

        Map<String, Object> custodianOptions = Map.of("delimiter", ",", "header", true);
        SCENARIOS.put(
                "custodian-trade",
                new ScenarioDefinition(
                        "custodian-trade",
                        "CUSTODIAN_TRADE_COMPLEX",
                        List.of(
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_alpha_morning.csv",
                                        "Alpha Morning",
                                        custodianOptions),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_beta_morning.csv",
                                        "Beta Morning",
                                        custodianOptions),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_omega_morning.csv",
                                        "Omega Morning",
                                        custodianOptions),
                                new BatchDefinition(
                                        "PLATFORM",
                                        "/data/custodian-trade/platform_morning.csv",
                                        "Platform Morning",
                                        custodianOptions),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_alpha_evening.csv",
                                        "Alpha Evening",
                                        custodianOptions),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_beta_evening.csv",
                                        "Beta Evening",
                                        custodianOptions),
                                new BatchDefinition(
                                        "PLATFORM",
                                        "/data/custodian-trade/platform_evening.csv",
                                        "Platform Evening",
                                        custodianOptions),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_omega_evening.csv",
                                        "Omega Evening",
                                        custodianOptions))));

        SCENARIOS.put(
                "securities-position",
                new ScenarioDefinition(
                        "securities-position",
                        "SEC_POSITION_COMPLEX",
                        List.of(
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/securities-position/custodian.csv",
                                        "Custodian Positions",
                                        Map.of("delimiter", ",")),
                                new BatchDefinition(
                                        "PORTFOLIO",
                                        "/data/securities-position/portfolio.csv",
                                        "Portfolio Positions",
                                        Map.of("delimiter", ","))
                        )));
    }

    private ScenarioRegistry() {
        // utility
    }

    static List<ScenarioDefinition> resolve(String key) {
        if (key == null || key.isBlank() || "all".equals(key)) {
            return new ArrayList<>(SCENARIOS.values());
        }
        ScenarioDefinition definition = SCENARIOS.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown scenario: " + key);
        }
        return List.of(definition);
    }
}
