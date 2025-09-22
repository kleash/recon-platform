# Phase 2 Backend Technical Reference

Each table enumerates the Phase 2 backend source and explains every line or contiguous block.

## `controller/SystemActivityController.java`
| Lines | Explanation |
| --- | --- |
| 1 | Declares the controller package so Spring can locate the class. |
| 3-10 | Import DTO, security context, service, collection, and web annotations needed for the endpoint. |
| 12-14 | Document the controller’s responsibility for exposing the activity feed. |
| 15-16 | `@RestController` and `@RequestMapping` configure the component and URL prefix. |
| 17 | Begin the controller class definition. |
| 19-20 | Declare dependencies on the activity service and user context. |
| 22-25 | Constructor injection wiring the dependencies. |
| 27 | Map the `recentActivity` handler to HTTP GET. |
| 28-33 | Guard against users without groups and delegate to the service, wrapping the response in `ResponseEntity`. |

## `service/SystemActivityService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-10 | Import DTO, entity, enum, repository, time, collection, and Spring infrastructure used by the service. |
| 12-14 | Provide context on the service’s audit responsibilities. |
| 15 | Register the class as a Spring service. |
| 18 | Declare the repository dependency. |
| 20-22 | Constructor injection for the repository. |
| 24-31 | `recordEvent` builds and persists a new `SystemActivityLog` entry with the supplied metadata. |
| 33-37 | `fetchRecent` streams the latest twenty entries and maps them into DTOs for the API. |

## `service/BreakAccessService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-13 | Import domain entities, enums, repository, and Java collections used for access decisions. |
| 16-18 | Document the class as the central maker/checker rules engine. |
| 19 | Register the class with Spring. |
| 22 | Declare the repository dependency. |
| 24-26 | Constructor storing the injected repository. |
| 28-33 | Fetch access control entries for the caller’s groups, rejecting users with no groups. |
| 35-38 | `assertCanView` throws when `canView` fails. |
| 41-43 | `canView` checks whether any entry matches the break dimensions. |
| 45-49 | `assertCanComment` enforces comment authorisation. |
| 51-54 | `canComment` allows makers or checkers scoped to the break dimensions. |
| 56-88 | `allowedStatuses` derives the legal workflow transitions based on maker/checker roles, current status, and definition configuration. |
| 90-99 | `assertTransitionAllowed` validates a requested status change against the computed options. |
| 101-105 | `matchingEntries` filters entries that match the break’s product, sub-product, and entity values or wildcards. |
| 108-110 | `hasRole` checks for a specific access role within filtered entries. |
| 112-114 | `matches` treats null entry values as wildcards and otherwise requires equality. |

## `service/BreakService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-18 | Import DTOs, entities, enums, repositories, security context, time utilities, collections, and Spring annotations required by the service. |
| 20-22 | Document the service’s purpose for managing analyst actions. |
| 23 | Register the component as a Spring service. |
| 26-33 | Declare repository, context, mapper, access, and activity service dependencies. |
| 34-48 | Constructor wiring the injected collaborators. |
| 51-73 | `addComment` loads break context, enforces comment permissions, persists the new comment, records a system event, and returns an updated DTO. |
| 75-107 | `updateStatus` loads context, validates the transition, updates persistence, appends an audit trail comment, records activity, and maps the refreshed DTO. |
| 109-116 | `loadBreakContext` fetches the break, gathers scoped access entries, validates view access, and returns the aggregate context record. |
| 118-119 | `BreakContext` record exposes the tuple consumed by other methods. |

## `service/BreakFilterCriteria.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-8 | Import domain entity, enums, and collection utilities used for filtering. |
| 10-14 | Document the record and define its fields. |
| 16-18 | `none` factory returns an empty filter. |
| 20-25 | `resolvedStatuses` yields all statuses when none are specified. |
| 27-37 | `matches` checks each optional dimension and ensures the break status is within the allowed set. |

## `service/BreakMapper.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import Jackson, DTOs, entities, enums, collections, logging, and Spring annotations used in mapping. |
| 17-19 | Describe the mapper’s role. |
| 20 | Register as a Spring component. |
| 23 | Declare logger instance. |
| 25 | Declare injected `ObjectMapper`. |
| 27-29 | Constructor storing the mapper dependency. |
| 31-48 | `toDto` sorts comments chronologically, maps them to DTOs, copies allowed statuses, and deserialises stored payloads for API use. |
| 51-62 | `readJson` safely converts stored JSON payloads to maps, logging failures and returning empty maps. |

## `service/ReconciliationService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-35 | Import DTOs, entities, enums, repositories, matching utilities, collections, and Spring annotations leveraged by the service. |
| 37-39 | Summarise the service responsibilities. |
| 40 | Mark the class as a Spring service. |
| 43-51 | Declare repositories, matching engine, mapper, access service, activity service, and object mapper dependencies. |
| 53-72 | Constructor wiring each collaborator. |
| 74-87 | `listAccessible` returns definitions the caller can access by aggregating access control entries and sorting them. |
| 89-111 | `triggerRun` validates access, executes the matching engine, persists run metrics and breaks, records activity, and returns the run detail DTO with no filters applied. |
| 114-121 | `fetchLatestRun` loads the newest run for a definition when available or returns an empty summary with filter metadata. |
| 123-129 | `fetchRunDetail` retrieves a specific run after confirming access. |
| 131-134 | `loadDefinition` fetches a definition or throws when it is missing. |
| 136-142 | `ensureAccess` resolves scoped entries for the caller and blocks unauthorised access. |
| 144-159 | `persistBreaks` transforms break candidates into entities, enriching them with metadata and serialised payloads before batch saving. |
| 161-177 | `buildRunDetail` produces the run summary and maps authorised, filtered break DTOs while attaching filter metadata. |
| 179-201 | `buildFilterMetadata` compiles unique product, sub-product, entity, and status options from the caller’s access control entries. |
| 203-211 | `writeJson` serialises maps to JSON, returning `{}` for empty payloads and failing fast on errors. |

## `service/matching/ExactMatchingEngine.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-33 | Import entities, enums, repositories, math, date/time, collection, stream, and Spring utilities used by the engine. |
| 35-37 | Describe the Phase 2 matching engine. |
| 38 | Register the engine as a Spring component. |
| 39 | Begin class definition implementing `MatchingEngine`. |
| 41-42 | Declare source repository dependencies. |
| 44-48 | Constructor wiring repositories. |
| 51-64 | `execute` initialises metadata and builds an index of source A records, enforcing unique keys. |
| 66-70 | Initialise counters and break candidate list. |
| 71-100 | Stream source B records, match against source A snapshots, increment counters, and emit break candidates for mismatches and missing records. |
| 102-111 | Emit missing-in-B break candidates for any unpaired source A records. |
| 113-114 | Return aggregated `MatchingResult`. |
| 116-125 | `recordsMatch` compares configured fields and short-circuits on the first difference. |
| 128-137 | `compareValues` applies comparison logic based on rule configuration, handling nulls gracefully. |
| 140-145 | `normalizedEquals` compares values according to declared data types. |
| 148-154 | `compareNumericWithThreshold` calculates tolerance percentages and applies them to numeric differences. |
| 156-164 | `toBigDecimal` normalises different numeric representations. |
| 166-180 | `asLocalDate` handles multiple date representations and raises descriptive errors on failure. |
| 183-188 | `buildKey` concatenates configured key fields via Spring’s property accessor. |
| 190-205 | `captureRecord` extracts configured output fields and product/sub-product/entity dimensions into an immutable snapshot. |
| 208-210 | `coalesce` helper chooses the first non-null dimension between two records. |
| 212-218 | Nested records define the snapshot and comparison rule structures. |
| 219-268 | `MatchingMetadata.fromDefinition` translates reconciliation metadata into key fields, comparison rules, and output field sets, detecting missing keys and including product hierarchy fields. |
| 271-274 | `filterByRole` helper selects fields with a given role. |
| 276-282 | `singleFieldName` returns the first field mapped to the requested role or null when absent. |

## `service/matching/MatchingResult.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import list collection used by the record. |
| 5-8 | Document and define the record capturing aggregated match results and generated break candidates. |

## `service/matching/BreakCandidate.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Import break type enum and map collection used in the record. |
| 6-15 | Describe and define the record storing pre-persistence break details including dimensional metadata. |

## `domain/dto/AddBreakCommentRequest.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import bean validation annotation used for request validation. |
| 5-10 | Document and define the record requiring both comment text and action code. |

## `domain/dto/BreakCommentDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import time type used in the DTO. |
| 5-8 | Document and define the record describing a break comment timeline entry. |

## `domain/dto/BreakItemDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-7 | Import enums, time, and collection types represented in the DTO. |
| 9-23 | Document and define the record exposing break details, dimensional tags, allowed transitions, payloads, and comments. |

## `domain/dto/FilterMetadataDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Import break status enum and list collection. |
| 6-14 | Document and define the record capturing available filter facets. |

## `domain/dto/RunDetailDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import list collection used for break DTO aggregation. |
| 5-9 | Document and define the record returning run summary, break list, and filter metadata. |

## `domain/dto/ReconciliationListItemDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Document and define the record describing accessible reconciliation definitions. |

## `domain/dto/ReconciliationSummaryDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import the `Instant` type used for the run timestamp. |
| 5-8 | Document and define the record containing headline run metrics. |

## `domain/dto/SystemActivityDto.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Import system event enum and timestamp type. |
| 6-9 | Document and define the record representing an activity feed entry. |

## `domain/dto/UpdateBreakStatusRequest.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Import the break status enum and validation constraint used for the request. |
| 6-9 | Document and define the record enforcing that a status value is provided. |

## `domain/entity/AccessControlEntry.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-15 | Import enums, JPA annotations, and Lombok accessors required by the entity. |
| 17-19 | Document the entity’s expanded Phase 2 role. |
| 21-24 | Mark as JPA entity/table and enable Lombok getters/setters. |
| 27-29 | Identify the primary key. |
| 31-32 | Persist the LDAP group DN. |
| 34-36 | Map the owning reconciliation definition. |
| 38-45 | Persist optional product, sub-product, and entity scoping columns. |
| 47-49 | Store the access role enum with string persistence. |

## `domain/entity/BreakComment.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-13 | Import JPA annotations, time type, and Lombok accessors. |
| 15-17 | Describe the entity. |
| 18-21 | Declare entity metadata and Lombok annotations. |
| 24-26 | Configure primary key generation. |
| 28-30 | Reference the parent break item. |
| 32-38 | Persist actor, action, and comment details. |
| 41-42 | Store the comment timestamp. |

## `domain/entity/BreakItem.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-21 | Import enums, JPA annotations, time type, collections, and Lombok accessors. |
| 23-24 | Document entity purpose. |
| 26-29 | Configure entity metadata and Lombok annotations. |
| 32-34 | Primary key definition. |
| 36-38 | Relationship to reconciliation run. |
| 40-46 | Persist break type and status, defaulting to OPEN. |
| 48-55 | Store product, sub-product, and entity dimensions. |
| 57-58 | Capture detection timestamp. |
| 60-66 | Persist JSON payloads for source A and source B. |
| 68-69 | Map comment relationship for eager mapping via entity graph. |

## `domain/entity/ReconciliationDefinition.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import JPA annotations, collections, and Lombok accessors. |
| 16-18 | Describe the entity. |
| 19-22 | Configure entity metadata and Lombok annotations. |
| 25-27 | Primary key. |
| 29-36 | Persist code, name, and description. |
| 38-39 | Map associated fields with cascade/orphan settings. |
| 41-42 | Store maker-checker flag. |

## `domain/entity/ReconciliationField.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-19 | Import enums, JPA annotations, math type, and Lombok accessors. |
| 21-22 | Document the field entity purpose. |
| 24-27 | Configure entity metadata and Lombok annotations. |
| 30-32 | Primary key. |
| 34-36 | Link back to the definition. |
| 38-43 | Persist source field name and display label. |
| 44-56 | Store field role, data type, comparison logic, and optional threshold. |

## `domain/entity/ReconciliationRun.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-20 | Import enums, JPA annotations, time type, collections, and Lombok accessors. |
| 22-24 | Document the run entity. |
| 25-28 | Configure entity metadata and Lombok annotations. |
| 31-33 | Primary key. |
| 35-37 | Associate run with its definition. |
| 39-57 | Persist run timestamp, trigger type, status, and aggregate counts. |
| 59-60 | Map related break items. |

## `domain/entity/SourceRecordA.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-12 | Import JPA annotations, numeric/date types, and Lombok accessors. |
| 14-16 | Document the entity. |
| 17-20 | Configure entity metadata and Lombok annotations. |
| 23-25 | Primary key definition. |
| 27-46 | Persist transaction identifier, amount, currency, trade date, and dimensional attributes. |

## `domain/entity/SourceRecordB.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-12 | Import JPA annotations, numeric/date types, and Lombok accessors. |
| 14-16 | Document the entity. |
| 17-20 | Configure entity metadata and Lombok annotations. |
| 23-25 | Primary key definition. |
| 27-46 | Persist transaction identifier, amount, currency, trade date, and dimensional attributes for source B. |

## `domain/entity/SystemActivityLog.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import enums, JPA annotations, time type, and Lombok accessors. |
| 16-18 | Describe the entity. |
| 19-22 | Configure entity metadata and Lombok annotations. |
| 25-27 | Primary key definition. |
| 29-37 | Persist event type, details, and timestamp columns. |

## `domain/enums/AccessRole.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Document the enum’s purpose. |
| 7-10 | Define viewer, maker, and checker roles. |

## `domain/enums/BreakStatus.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Document the break lifecycle. |
| 6-9 | Enumerate Open, Pending Approval, and Closed statuses. |

## `domain/enums/BreakType.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Describe the enum meaning. |
| 6-9 | Enumerate mismatch and missing record break types. |

## `domain/enums/ComparisonLogic.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Document the enum’s use in matching. |
| 6-10 | Enumerate supported comparison behaviours. |

## `domain/enums/FieldDataType.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Explain the enum’s role in type-normalised comparisons. |
| 7-11 | Enumerate supported logical data types. |

## `domain/enums/FieldRole.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Document the enum. |
| 6-12 | Enumerate key, compare, display, and dimensional field roles. |

## `domain/enums/SystemEventType.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-4 | Describe the enum. |
| 6-9 | Enumerate reconciliation run, break status change, and break comment events. |

## `repository/AccessControlEntryRepository.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Import entity and Spring Data interfaces. |
| 8-15 | Document the repository and declare finder methods for groups and scoped definitions. |

## `repository/BreakCommentRepository.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Import entity and repository base interface. |
| 8-13 | Document the repository and expose ordered comment lookup. |

## `repository/BreakItemRepository.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-7 | Import entity types and annotations. |
| 9-15 | Document the repository and expose an entity-graph-backed finder for ordered breaks. |

## `repository/SystemActivityLogRepository.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import entity class and list collection. |
| 7-13 | Document the repository and declare the top-20 finder. |

## `controller/BreakController.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-14 | Import DTOs, service, validation, and web annotations needed for the endpoints. |
| 16-18 | Document controller responsibilities. |
| 19-21 | Register as REST controller with `/api/breaks` base path. |
| 23 | Declare dependency on `BreakService`. |
| 25-27 | Constructor wiring the service. |
| 29-33 | `addComment` endpoint accepting validated request payloads and delegating to the service. |
| 35-39 | `updateStatus` endpoint performing the same pattern for status changes. |

## `controller/ReconciliationController.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-21 | Import DTOs, enums, services, validation annotations, collection utilities, and web annotations. |
| 23-25 | Document controller purpose. |
| 26-28 | Register as REST controller under `/api/reconciliations`. |
| 30-31 | Declare dependencies on reconciliation service and user context. |
| 33-36 | Constructor storing dependencies. |
| 38-41 | `listReconciliations` returns accessible definitions for the current user. |
| 43-47 | `triggerRun` endpoint invoking the service with current user groups. |
| 49-58 | `getLatestRun` builds filter criteria from query parameters and returns the latest run. |
| 61-69 | `getRun` fetches a specific run with identical filtering behaviour. |
| 72-75 | `buildFilter` helper converts optional query parameters into a `BreakFilterCriteria` instance. |

## `service/matching/MatchingEngine.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import reconciliation definition entity referenced by the contract. |
| 5-10 | Document the strategy interface and declare the execute method signature. |
