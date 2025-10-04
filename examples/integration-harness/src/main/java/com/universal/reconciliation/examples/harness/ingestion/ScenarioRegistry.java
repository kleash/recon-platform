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
                                        Map.of("delimiter", ","),
                                        null),
                                new BatchDefinition(
                                        "GL",
                                        "/data/cash-vs-gl/gl_source.csv",
                                        "General Ledger Batch",
                                        Map.of("delimiter", ","),
                                        null)
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
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_beta_morning.csv",
                                        "Beta Morning",
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_omega_morning.csv",
                                        "Omega Morning",
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "PLATFORM",
                                        "/data/custodian-trade/platform_morning.csv",
                                        "Platform Morning",
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_alpha_evening.csv",
                                        "Alpha Evening",
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_beta_evening.csv",
                                        "Beta Evening",
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "PLATFORM",
                                        "/data/custodian-trade/platform_evening.csv",
                                        "Platform Evening",
                                        custodianOptions,
                                        null),
                                new BatchDefinition(
                                        "CUSTODIAN",
                                        "/data/custodian-trade/custodian_omega_evening.csv",
                                        "Omega Evening",
                                        custodianOptions,
                                        null))));

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
                                        Map.of("delimiter", ","),
                                        null),
                                new BatchDefinition(
                                        "PORTFOLIO",
                                        "/data/securities-position/portfolio.csv",
                                        "Portfolio Positions",
                                        Map.of("delimiter", ","),
                                        null)
                        )));

        Map<String, Object> globalMasterOptions = Map.of(
                "hasHeader", true,
                "includeAllSheets", true,
                "includeSheetNameColumn", true,
                "sheetNameColumn", "global_sheet_tag");

        Map<String, Object> apacOptions = Map.of(
                "hasHeader", true,
                "includeAllSheets", true,
                "includeSheetNameColumn", true,
                "sheetNameColumn", "apac_sheet_tag");

        Map<String, Object> emeaOptions = Map.of(
                "hasHeader", true,
                "includeAllSheets", true,
                "includeSheetNameColumn", true,
                "sheetNameColumn", "emea_sheet_tag");

        Map<String, Object> americasOptions = Map.of("delimiter", ",");
        Map<String, Object> derivativesOptions = Map.of("delimiter", ",");
        Map<String, Object> custodyOptions = Map.of("delimiter", "|");

        SCENARIOS.put(
                "global-multi-asset",
                new ScenarioDefinition(
                        "global-multi-asset",
                        "GLOBAL_MULTI_ASSET_COMPLEX",
                        List.of(
                                new BatchDefinition(
                                        "GLOBAL_MASTER",
                                        "/data/global-multi-asset/global_master.xlsx",
                                        "Global Master",
                                        globalMasterOptions,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                                new BatchDefinition(
                                        "APAC_MULTI",
                                        "/data/global-multi-asset/apac_positions.xlsx",
                                        "APAC Books",
                                        apacOptions,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                                new BatchDefinition(
                                        "EMEA_MULTI",
                                        "/data/global-multi-asset/emea_positions.xlsx",
                                        "EMEA Books",
                                        emeaOptions,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                                new BatchDefinition(
                                        "AMERICAS_CASH",
                                        "/data/global-multi-asset/americas_cash.csv",
                                        "Americas Cash",
                                        americasOptions,
                                        "text/csv"),
                                new BatchDefinition(
                                        "DERIVATIVES_FEED",
                                        "/data/global-multi-asset/derivatives_positions.csv",
                                        "Derivatives Valuations",
                                        derivativesOptions,
                                        "text/csv"),
                                new BatchDefinition(
                                        "GLOBAL_CUSTODY",
                                        "/data/global-multi-asset/global_custody.txt",
                                        "Global Custody",
                                        custodyOptions,
                                        "text/plain")
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
