# Phase 4 Backend Technical Reference

Each section documents the Phase 4 backend changes and explains the introduced code line by line (or by contiguous block).

## `domain/entity/SourceRecordA.java`
| Lines | Explanation |
| --- | --- |
| 1-15 | Import persistence annotations and value types needed for the extended entity. |
| 30-33 | Link every source record to its owning `ReconciliationDefinition`, enabling per-reconciliation datasets. |
| 34-74 | Retain existing transaction attributes and add optional securities metadata (account, ISIN, quantities, valuation details, custodian, portfolio manager) used by the complex example. |

## `domain/entity/SourceRecordB.java`
| Lines | Explanation |
| --- | --- |
| 1-15 | Import persistence types mirroring the source A entity. |
| 30-33 | Associate source B rows with a `ReconciliationDefinition` for scoped matching. |
| 34-74 | Preserve prior columns and introduce the same optional securities fields to support the complex reconciliation data set. |

## `repository/SourceRecordARepository.java`
| Lines | Explanation |
| --- | --- |
| 3-9 | Import definition entity and Spring Data query helpers. |
| 16-20 | Replace the global lookup with definition-scoped find/stream methods so matching operates on the correct dataset. |

## `repository/SourceRecordBRepository.java`
| Lines | Explanation |
| --- | --- |
| 3-9 | Import definition entity and Spring Data helpers. |
| 16-20 | Mirror the source A repository by exposing definition-scoped lookup and streaming queries. |

## `service/matching/ExactMatchingEngine.java`
| Lines | Explanation |
| --- | --- |
| 55-104 | Stream source A/B data by definition instead of loading every row, ensuring multiple reconciliations can coexist without cross-contamination. |

## `etl/EtlPipeline.java`
| Lines | Explanation |
| --- | --- |
| 1-16 | Define the contract implemented by every sample ETL pipeline, exposing a descriptive name and execution hook. |

## `etl/AbstractSampleEtlPipeline.java`
| Lines | Explanation |
| --- | --- |
| 3-53 | Capture shared dependencies and logging infrastructure for the pipelines. |
| 56-140 | Provide helpers to create definitions, fields, report templates, columns, and access control entries in a consistent manner. |
| 142-148 | Utility converters to normalise CSV values into domain types. |
| 150-182 | CSV reader that loads classpath resources into ordered maps, throwing descriptive errors when files are missing. |

## `etl/SampleEtlRunner.java`
| Lines | Explanation |
| --- | --- |
| 3-29 | Spring `CommandLineRunner` that iterates over all `EtlPipeline` beans at startup, logging execution for operators. |

## `etl/SimpleCashGlEtlPipeline.java`
| Lines | Explanation |
| --- | --- |
| 3-23 | Import domain entities, enums, repositories, and Spring annotations required by the simple pipeline. |
| 32-40 | Constructor wires the shared repositories via the abstract superclass. |
| 47-85 | Execute method skips when data already exists, builds the reconciliation definition, saves access entries, and loads CSV records into source tables. |
| 87-100 | Declare field configuration covering keys, comparison roles, and display metadata for the simple reconciliation. |
| 101-121 | Configure the report template and columns used for Excel exports. |
| 123-159 | Helper methods that add fields to the definition and transform CSV rows into `SourceRecordA`/`SourceRecordB` entities. |

## `etl/SecuritiesPositionEtlPipeline.java`
| Lines | Explanation |
| --- | --- |
| 3-23 | Import the richer domain types and Spring utilities consumed by the complex pipeline. |
| 32-40 | Constructor delegates repository wiring to the superclass. |
| 47-85 | Execute method guards against duplicate loads, persists the complex reconciliation definition, access control entries, and seeded records. |
| 87-148 | Configure the extensive field set covering multi-key matching, numeric tolerances, maker/checker metadata, and display-only columns. |
| 151-175 | Build the complex export template including valuation and workflow fields. |
| 177-223 | Helper methods that register fields and map CSV rows into enriched source records. |

## `src/main/resources/data.sql`
| Lines | Explanation |
| --- | --- |
| 1 | Document that data seeding is now handled by the dedicated ETL pipelines instead of static SQL inserts. |

## `src/main/resources/etl/simple/*.csv`
| Files | Explanation |
| --- | --- |
| `cash_gl_source_a.csv` | Source A sample data driving the simple cash vs GL reconciliation. |
| `cash_gl_source_b.csv` | Complementary source B data including mismatches/missing rows for training scenarios. |

## `src/main/resources/etl/complex/*.csv`
| Files | Explanation |
| --- | --- |
| `securities_source_a.csv` | Complex source A dataset containing multi-key securities positions and valuation details. |
| `securities_source_b.csv` | Complex source B dataset crafted to produce matches, mismatches, and missing records. |

## `etl/SampleEtlIntegrationTest.java`
| Lines | Explanation |
| --- | --- |
| 13-46 | Spring Boot integration test verifying that both pipelines create definitions and load sample data scoped by definition. |

## `service/ReconciliationServiceIntegrationTest.java`
| Lines | Explanation |
| --- | --- |
| 20-67 | Resolve the seeded definition ID dynamically via the repository before running the existing filtering assertions, keeping the test resilient to ID changes. |

## `service/ExactMatchingEngineTest.java`
| Lines | Explanation |
| --- | --- |
| 43-139 | Update the unit test to supply a concrete definition when mocking repository streams and to attach that definition to synthetic source records. |
