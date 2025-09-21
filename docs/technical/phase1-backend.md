# Phase 1 Backend Technical Reference

Each table enumerates every line (or contiguous block of lines) in the backend source code and explains its purpose.

## `UniversalReconciliationPlatformApplication.java`
| Lines | Explanation |
| --- | --- |
| 1 | Declares the `com.universal.reconciliation` package so the class participates in Spring’s component scan. |
| 3-4 | Import the Spring Boot helpers required to bootstrap the application. |
| 6-10 | Provide high-level Javadoc describing the monolithic entry point strategy for Phase 1. |
| 11 | Applies `@SpringBootApplication`, enabling auto-configuration and component scanning. |
| 12 | Opens the `UniversalReconciliationPlatformApplication` class definition. |
| 14-18 | Document the `main` method contract for future operators. |
| 19-20 | Delegate to `SpringApplication.run`, launching the Spring context. |
| 21-22 | Close the method and class definitions. |

## `config/JwtProperties.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration aligns the class with other configuration utilities. |
| 3-4 | Import Spring annotations used for property binding and component registration. |
| 6-7 | Class-level documentation summarising why the bean exists. |
| 8 | Marks the class as a Spring component. |
| 9 | Binds configuration properties with prefix `app.security.jwt`. |
| 10 | Begins class definition. |
| 12-13 | Declare the JWT signing secret field. |
| 15-16 | Declare token expiration duration. |
| 18-30 | Provide standard getters and setters so Spring can populate and other beans can consume the properties. |
| 31 | Close the class. |

## `config/SecurityConfig.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import the custom JWT filter. |
| 4-16 | Import Spring Security, LDAP, and CORS configuration types used in the class. |
| 18-20 | Javadoc outlining the configuration’s role for Phase 1. |
| 21 | Annotate the class as a configuration component. |
| 22 | Enable method-level security annotations for future use. |
| 23 | Begin class definition. |
| 25-28 | Declare the injected JWT authentication filter and assign via constructor. |
| 30-34 | Expose an `AuthenticationManager` bean configured for LDAP bind authentication with the provided context source. |
| 36-45 | Define the HTTP security filter chain: enable CORS, disable CSRF, enforce stateless sessions, whitelist login/health, secure other endpoints, and register the JWT filter ahead of username/password processing. |
| 47-55 | Provide a permissive `CorsConfigurationSource` so the Angular client can call the API during early development. |
| 56 | Close the class. |

## `security/JwtService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import configuration bean for property access. |
| 4-11 | Import JWT helper classes for token creation and parsing. |
| 12-13 | Import Java standard library utilities for cryptography and time. |
| 14-16 | Import Spring’s `@Component` annotation. |
| 18-19 | Class-level documentation. |
| 20 | Mark as Spring component. |
| 21 | Begin class definition and store configuration reference. |
| 22 | Declare lazy-initialised signing key cache. |
| 24-27 | Constructor injection of configuration dependency. |
| 29-36 | `generateToken` builds a signed JWT containing subject, issue/expiry timestamps, and custom group/display claims. |
| 38-43 | `parseToken` validates a token signature and returns decoded claims. |
| 45-49 | Lazily build and reuse the HMAC signing key derived from the configured Base64 secret. |
| 50 | Close class. |

## `security/JwtAuthenticationFilter.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3 | Import JWT claims type. |
| 4-9 | Import servlet API types for filter execution. |
| 10 | Import collections for storing group names. |
| 11-15 | Import Spring Security classes for building authentication tokens and populating the security context. |
| 16-18 | Import helper annotations and base filter class. |
| 20-22 | Class-level documentation. |
| 23 | Mark as Spring component so the filter is auto-detected. |
| 24-27 | Declare constructor-injected dependency on `JwtService`. |
| 29-47 | Override `doFilterInternal` to inspect the `Authorization` header, decode JWTs when present, build a `LdapGroupAuthenticationToken`, handle parsing errors, and continue the chain. |
| 48 | Close class. |

## `security/LdapGroupAuthenticationToken.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import abstract authentication base class and authority interface. |
| 7-9 | Document the token’s intent. |
| 10 | Begin class definition extending `AbstractAuthenticationToken`. |
| 12 | Store principal identifier. |
| 14-18 | Constructor accepts principal and authorities, delegates to superclass, and marks authentication as established. |
| 20-22 | Return empty credentials because JWT already proves identity. |
| 24-26 | Return stored principal. |
| 27 | Close class. |

## `security/UserContext.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import collection and stream helpers. |
| 6-8 | Import Spring Security context classes. |
| 9 | Register as Spring component. |
| 10 | Begin class definition. |
| 12-15 | `getUsername` fetches the current authentication object and returns its name or `anonymous`. |
| 17-25 | `getGroups` maps granted authorities to a list of group names, returning an empty list when unauthenticated. |
| 26 | Close class. |

## `service/UserDirectoryService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import JNDI classes for LDAP attribute handling. |
| 6-9 | Import Java util helpers. |
| 10-14 | Import Spring LDAP utilities and annotations. |
| 16-18 | Document service responsibilities. |
| 19 | Register as Spring service. |
| 21-24 | Declare dependencies and configuration properties for LDAP tree navigation. |
| 26-31 | Constructor wires `LdapTemplate` and property values with defaults for embedded LDAP. |
| 33-38 | `lookupDisplayName` resolves a user’s common name from LDAP, falling back to username on failure. |
| 40-47 | `findGroups` locates group `cn` values for the user by searching `groupOfUniqueNames` membership. |
| 49-56 | `mapCommonName` safely extracts the `cn` attribute, handling missing or malformed values. |
| 58-60 | Expose the full distinguished name for the given username. |
| 62-64 | Internal helper constructing the user DN using configured base path. |
| 65 | Close class. |

## `service/AuthService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import DTOs and JWT service. |
| 6-8 | Import collection and Spring authentication classes. |
| 9 | Register as Spring service. |
| 10 | Begin class definition with dependencies for authentication, directory lookups, and token issuing. |
| 18-29 | `login` authenticates credentials against LDAP, resolves group memberships and display name, and issues a JWT encapsulated in a `LoginResponse`. |
| 30 | Close class. |

## `service/ReconciliationService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-6 | Import Jackson utilities and DTOs. |
| 7-12 | Import entity classes manipulated by the service. |
| 13-15 | Import enums for run metadata. |
| 16-20 | Import repositories used to fetch and persist data. |
| 21-23 | Import matching engine contracts. |
| 24-28 | Import Java collections and time utilities. |
| 29 | Import Spring annotations. |
| 31-33 | Document responsibilities. |
| 34 | Register as Spring service. |
| 36-42 | Declare repository, engine, mapper, and serializer dependencies. |
| 44-52 | Constructor injects collaborators. |
| 54-63 | `listAccessible` finds distinct reconciliation definitions linked to the caller’s groups and sorts them alphabetically. |
| 65-79 | `triggerRun` validates access, runs the matching engine, persists the run metadata and resulting break records, then returns a DTO view. |
| 81-87 | `fetchLatestRun` returns the latest run DTO or an empty summary if none exist. |
| 89-94 | `fetchRunDetail` loads a specific run after enforcing access. |
| 96-100 | `loadDefinition` helper fetches a definition or throws. |
| 102-108 | `ensureViewAccess` verifies the caller’s LDAP groups are authorised for the definition. |
| 110-118 | `persistBreaks` serialises break payloads and stores them against the run. |
| 120-127 | `buildRunDetail` combines stored run metrics with mapped break DTOs. |
| 129-137 | `writeJson` serialises maps for persistence, returning `{}` on empty input and throwing on failure. |
| 138 | Close class. |

## `service/BreakMapper.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import Jackson and DTO types. |
| 6-8 | Import entity and repository dependencies. |
| 9-10 | Import utilities and annotations. |
| 12-14 | Document conversion purpose. |
| 15 | Register as Spring component. |
| 17-20 | Constructor injection for repositories and serializer. |
| 22-30 | `toDto` assembles a `BreakItemDto` by reading comments and deserialising stored JSON blobs. |
| 32-40 | `readJson` helper safely turns JSON strings into maps with fallback. |
| 41 | Close class. |

## `service/BreakService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-10 | Import DTOs, entities, repositories, and utilities used for break actions. |
| 11-14 | Import security context and time helpers. |
| 15-16 | Import annotations. |
| 18-20 | Document service responsibilities. |
| 21 | Register as Spring service. |
| 23-30 | Declare dependencies for persistence, authorisation checks, and DTO mapping. |
| 32-39 | Constructor wiring. |
| 41-50 | `addComment` ensures access, persists a comment with actor DN, and returns updated DTO. |
| 52-62 | `updateStatus` changes status, records audit trail entry, and returns updated DTO. |
| 64-69 | `findAuthorizedBreak` loads a break and validates access. |
| 71-78 | `ensureAccess` checks group membership using the ACL repository. |
| 79 | Close class. |

## `service/ExportService.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-5 | Import Jackson utilities. |
| 6-8 | Import DTOs representing run data. |
| 9-11 | Import IO utilities. |
| 12-15 | Import Apache POI classes for workbook generation. |
| 16 | Import Spring `@Service`. |
| 18-20 | Document exporter role. |
| 21 | Register as service. |
| 23-26 | Constructor injection for `ObjectMapper`. |
| 28-36 | `exportToExcel` builds workbook, delegates to helper sheet builders, and returns the workbook bytes. |
| 38-52 | `populateSummarySheet` writes run metadata rows. |
| 54-75 | `populateBreakSheet` writes break-level data, including comment aggregation and column sizing. |
| 77-81 | `writeJson` helper serialises maps to JSON with error fallback. |
| 82 | Close class. |

## `service/matching/ExactMatchingEngine.java`
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration. |
| 3-10 | Import entities, enums, repositories, and Spring bean utilities used for metadata-driven matching. |
| 11-22 | Import Java collections, streams, and validation helpers supporting dynamic field extraction. |
| 18-20 | Document algorithm focus. |
| 21 | Register as Spring component. |
| 23-28 | Declare repository dependencies and constructor. |
| 30-88 | `execute` derives key/compare/display metadata from the `ReconciliationDefinition`, streams source A records into a key-indexed map, streams source B to classify matches, mismatches, and missing entries, and accumulates `BreakCandidate` instances. |
| 91-129 | Helper methods normalize comparison semantics (e.g., BigDecimal comparisons), build composite keys, and capture record values into immutable views. |
| 131-154 | `MatchingMetadata` computes the field sets used throughout execution and enforces configuration invariants. |
| 155 | Close class. |

## Controllers (`controller/*.java`)
| File | Highlights |
| --- | --- |
| `AuthController` | Lines 1-21 declare the REST controller, inject `AuthService`, and expose `POST /api/auth/login` which validates the request body and returns a `LoginResponse`. |
| `ReconciliationController` | Lines 1-49 wire `ReconciliationService` with `UserContext`, exposing endpoints to list reconciliations, trigger runs, and fetch latest or specific run details while passing current group memberships. |
| `BreakController` | Lines 1-33 provide endpoints to add comments and update break status by delegating to `BreakService`. |
| `ExportController` | Lines 1-36 orchestrate run export by fetching run details, invoking `ExportService`, and returning the Excel file with appropriate headers. |

## DTO Records (`domain/dto/*.java`)
For each DTO record the documentation is identical: imports declare validation or enum dependencies, record signatures capture the payload structure, and optional annotations enforce validation rules. Each file’s single record definition concisely maps to backend JSON contracts.

## Entities (`domain/entity/*.java`)
All entity classes follow the same pattern: package declaration, JPA imports, optional enum imports, Javadoc summarising the table’s purpose, `@Entity`/`@Table` annotations referencing physical table names, field declarations annotated with JPA metadata, getter/setter pairs providing access, and closing braces. These lines map one-to-one to the schema documented in the project README.

## Repositories (`repository/*.java`)
Each repository interface includes a package declaration, necessary imports, class-level Javadoc, and Spring Data method signatures expressing the required finder queries. Because there is no additional logic, the table for each repository would simply repeat those facts for all lines.

## Enums (`domain/enums/*.java`)
Enum files contain a package declaration, a descriptive Javadoc, the enum declaration, constant list, and closing brace—providing type-safe representations of statuses and roles referenced throughout the service layer.

## Resources (`application.yml`, `data.sql`, `ldap-data.ldif`)
* `application.yml` lines define datasource, JPA, LDAP, security, and logging configuration keys required to run the service locally.
* `data.sql` inserts seed metadata for the sample reconciliation, ACL entries, and source data rows the engine processes.
* `ldap-data.ldif` constructs an embedded LDAP tree with one operations user and group, allowing the authentication flow to execute without external dependencies.

## Test (`ExactMatchingEngineTest.java`)
| Lines | Explanation |
| --- | --- |
| 1 | Package declaration aligning test with service namespace. |
| 3-4 | Static imports for AssertJ and Mockito helpers. |
| 6-11 | Import domain types and repositories mocked in the test. |
| 12-15 | Import JUnit and Mockito test utilities. |
| 17-19 | Document the test goal. |
| 20 | Begin test class. |
| 22-24 | Declare repository mocks and engine under test. |
| 26-31 | `setUp` initialises mocks and engine before each test. |
| 33-41 | Main test configures mock data, runs the engine, and asserts counts and break types. |
| 43-53 | Helper methods create source record instances with consistent fields for reuse. |
| 54 | Close class. |
