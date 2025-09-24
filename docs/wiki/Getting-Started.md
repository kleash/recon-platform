## 3. Setup & Installation

### 3.1 Prerequisites
- JDK 17 (LTS)
- Maven 3.9+
- Node.js 18+ and npm 9+
- Docker Desktop (optional for containerized MariaDB/LDAP)
- Access to corporate LDAP (or demo profile configuration)

### 3.2 Environment Setup
1. Clone the repository and `cd` into `recon-platform`.
2. Bootstrap backend dependencies:
   ```bash
   cd backend
   ./mvnw dependency:go-offline
   cd ..
   ```
3. Install frontend dependencies:
   ```bash
   cd frontend
   npm install
   cd ..
   ```
4. Create override files if environment-specific settings are required:
   - Duplicate `backend/src/main/resources/application.yml` to `application-local.yml` and adjust the datasource URL or LDAP bases.
   - Update `frontend/src/environments/environment.ts` with the correct `apiBaseUrl` for your backend instance.
5. Ensure MariaDB (or H2) credentials match the Spring profile you intend to run.

### 3.3 Configuration Profiles
| Profile | Description | Command |
| --- | --- | --- |
| `dev` | Default local profile using H2 and seeded demo data. | `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| `local-mariadb` | Connects to a local MariaDB instance while retaining demo ETL pipelines. | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local-mariadb` |
| `prod` | Hardened configuration with external MariaDB and enterprise LDAP integrations. | `java -jar backend.jar --spring.profiles.active=prod` |

### 3.4 Data Seeding & Sample Jobs
- **Schema creation:** `spring.jpa.hibernate.ddl-auto=update` creates or updates tables automatically for development profiles. For a clean rebuild, start H2 with `-Dspring.jpa.hibernate.ddl-auto=create` once and revert to `update` afterward.
- **Sample ETL:** `SampleEtlRunner` executes the registered pipelines (`SecuritiesPositionEtlPipeline`, `SimpleCashGlEtlPipeline`) during startup, loading demo records into `source_a_records`, `source_b_records`, and attaching a reconciliation definition.
- **Refreshing data:** Restarting the backend clears the in-memory H2 database. For MariaDB, run `DELETE FROM source_a_records`, `source_b_records`, and `break_items` before rerunning the application to let the ETL pipelines repopulate data.
- **User identities:** `ldap-data.ldif` seeds demo users and groups when using the embedded LDAP server. Update this file or point Spring LDAP to an external directory for enterprise integration.


## 6.3 Running Locally
1. Start the backend: `cd backend && ./mvnw spring-boot:run`
2. In a separate terminal start the frontend: `cd frontend && npm start`
3. Navigate to `http://localhost:4200` and sign in with demo credentials (any username/password for dev profile).
