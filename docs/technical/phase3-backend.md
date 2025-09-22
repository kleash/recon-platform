# Phase 3 Backend Technical Reference

Each section enumerates the Phase 3 backend sources and explains every line or contiguous block that was introduced or modified during this phase.

## `controller/BreakController.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration for the controller layer. |
| 3-16 | Import DTOs, service, validation, collection, and Spring MVC annotations required by the controller. |
| 18-20 | Document the controller's responsibility for break maintenance operations. |
| 21-47 | Define the REST controller, inject the `BreakService`, and expose comment, status, and bulk update endpoints returning the mapped DTO responses. |

## `controller/ExportController.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import enums, services, context, headers, media type, response wrapper, and Spring MVC annotations used for exporting. |
| 16-18 | Describe the controller's role for Excel exports. |
| 19-51 | Register the controller, inject required collaborators, fetch run detail, stream the Excel workbook, audit the export, and build the HTTP response with attachment headers. |

## `controller/ReconciliationController.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-21 | Import DTOs, enums, services, context, validation, collections, and Spring MVC annotations needed for reconciliation discovery and execution. |
| 23-25 | Summarise the API surface. |
| 26-79 | Declare the REST controller, wire dependencies, list accessible reconciliations, trigger runs with optional payload, fetch latest and specific runs with filter parameters, and convert query parameters into a `BreakFilterCriteria`. |

## `domain/dto/BulkBreakUpdateRequest.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Import enums, validation, and collection utilities for the record. |
| 8-15 | Document and declare the record fields including validation annotations for break IDs and optional actions. |
| 17-32 | Provide helper methods for status, comment, default action resolution, and validation that at least one change is requested. |

## `domain/dto/ReconciliationSummaryDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Import the trigger type enum and timestamp type. |
| 6-19 | Document and declare the record capturing metadata and headline counts for a reconciliation run. |

## `domain/dto/RunAnalyticsDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import `Map` used for aggregations. |
| 5-16 | Document and declare the record that holds aggregated analytics for a run. |
| 18-20 | Provide a static factory yielding an empty analytics structure. |

## `domain/dto/RunDetailDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import list type used for break collections. |
| 5-12 | Document and declare the record bundling the run summary, analytics, break details, and filter metadata. |

## `domain/dto/TriggerRunRequest.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import the trigger enum. |
| 5-9 | Document and declare the record containing trigger metadata supplied by the UI or API client. |

## `domain/entity/ReportColumn.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-15 | Import enums, JPA annotations, and Lombok accessors used by the entity. |
| 17-48 | Document the entity and declare JPA mappings for the template relationship, header metadata, value source, source field, ordering, and highlighting flag. |

## `domain/entity/ReportTemplate.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-16 | Import JPA annotations, collections, and Lombok utilities. |
| 18-54 | Document the template entity and declare fields for identification, owning definition, export options, difference highlighting, and associated `ReportColumn` children. |

## `domain/entity/ReconciliationDefinition.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import JPA annotations, collection types, and Lombok decorators. |
| 16-47 | Document the entity and extend it with a template relationship in addition to the existing reconciliation metadata fields. |

## `domain/entity/ReconciliationRun.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-18 | Import enums, JPA annotations, time type, and collections required for the run entity. |
| 22-71 | Document the entity and add persisted fields for trigger details, correlation metadata, matched counts, and relationships to definition and breaks. |

## `domain/enums/ReportColumnSource.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-10 | Document the enum and declare source options for report columns. |

## `domain/enums/SystemEventType.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-12 | Document and list the event types, including new bulk action and export categories used for audit logging. |

## `domain/enums/TriggerType.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-10 | Document the enum and declare all supported trigger sources, adding external API and Kafka event types. |

## `repository/ReportTemplateRepository.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Import the entity, optional wrapper, and Spring Data annotations. |
| 8-14 | Document the repository and expose a method that fetches the latest template with eager column loading. |

## `service/BreakService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-20 | Import DTOs, entities, enums, repositories, context, utilities, and Spring annotations used by the service. |
| 22-50 | Document the service and inject repositories, directory lookup, mapper, access service, and activity recorder. |
| 53-75 | Implement comment handling: enforce permissions, persist comment, audit, and return updated DTO. |
| 77-109 | Implement status updates with transition validation, audit trail persistence, and activity logging before returning the refreshed DTO. |
| 111-168 | Add bulk update workflow iterating selected breaks, conditionally applying status changes and comments, counting results, recording activity, and returning DTOs. |
| 170-181 | Provide helper record and method for loading break context with access checks. |

## `service/ExportService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-32 | Import Jackson, DTOs, entities, enums, repository, IO helpers, temporal utilities, collections, Apache POI, logging, and Spring annotations. |
| 34-38 | Document the service and declare it as a Spring bean. |
| 40-48 | Define logger and inject dependencies. |
| 50-81 | Generate the workbook using the resolved template, populate sheets for summary, matched, mismatched, and missing data, and return the byte array while handling IO errors. |
| 83-110 | Write the summary sheet rows, analytics distributions, and autosize columns. |
| 112-140 | Populate the matched summary sheet including coverage calculation and product leader board. |
| 142-199 | Build detailed break sheets with configurable columns, difference highlighting, and auto-sizing. |
| 200-232 | Resolve templates and column layouts with defaults when no configuration exists. |
| 234-257 | Utility methods for writing summary rows, distributions, and highlight cell style. |
| 260-321 | Helper functions for difference detection, value resolution, metadata extraction, JSON serialisation, and cell rendering. |
| 323-329 | Record encapsulating column layout metadata. |

## `service/ReconciliationService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-37 | Import DTOs, entities, enums, repositories, matching constructs, utilities, collections, and Spring annotations. |
| 39-77 | Document the service and inject repositories, engine, mapper, access service, activity recorder, and analytics calculator. |
| 79-92 | List accessible reconciliations by resolving access control entries and sorting definitions. |
| 94-127 | Trigger a reconciliation run: enforce access, execute engine, persist run metadata, capture trigger details, store breaks, audit, and return the run detail. |
| 129-149 | Fetch the latest run applying optional filters, returning default analytics when none exist. |
| 151-161 | Fetch specific runs with filter support and overload without filters. |
| 163-174 | Helper methods for loading definitions and ensuring access entitlements. |
| 176-191 | Persist break candidates into break items with serialised payloads. |
| 193-223 | Build run detail including summary, accessible break DTOs, analytics calculation, and filter metadata. |
| 225-247 | Compile filter metadata options from access control entries. |
| 249-257 | Serialise maps to JSON for break payloads. |
| 260-265 | Resolve the run initiator from trigger request fallbacks. |

## `service/RunAnalyticsCalculator.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-16 | Import DTO, entities, enums, time utilities, collections, streams, and Spring annotation. |
| 18-38 | Document the component and compute aggregated analytics for a run. |
| 40-60 | Count breaks by status and type retaining meaningful entries. |
| 62-80 | Aggregate by classifier (product/entity) with default labels and sorted output. |
| 82-100 | Bucket open breaks by age using defined categories and deterministic ordering. |
| 102-116 | Helper methods producing bucket labels and comparison ordering. |

## `main/resources/data.sql`
| Lines | Explanation |
| --- | --- |
| 1-42 | Seed reconciliation definition, fields, and access control entries for the demo scenario. |
| 43-69 | Populate source system sample data records. |
| 71-107 | Insert the default report template and column configuration used for Phase 3 exports. |

## `test/service/BreakServiceTest.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-24 | Import assertion helpers, Mockito utilities, DTOs, entities, enums, repositories, context, collections, and JUnit annotations. |
| 25-53 | Declare the test class, mock dependencies, and instantiate the service under test. |
| 55-84 | Verify comment handling persists audit data and records activity. |
| 86-124 | Validate the bulk update workflow emits updates, records activity, and saves comments for each break. |
| 126-139 | Provide helper method constructing a representative break entity graph. |

## `test/service/ReconciliationServiceIntegrationTest.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import assertions, DTOs, enums, collections, and Spring/JUnit annotations used in the integration test. |
| 15-21 | Configure Spring Boot test context and inject the service. |
| 23-51 | Execute a full reconciliation run, assert analytics values, validate filtering, and ensure entity-specific results. |
