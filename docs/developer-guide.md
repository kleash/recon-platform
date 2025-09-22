# Developer Guide

This guide documents the workflows required to build, test, and contribute to the Universal Reconciliation Platform.

## Prerequisites
- JDK 17+
- Node.js 18+ and npm
- Maven 3.9+
- Docker (optional) if you prefer containerised execution

## Repository layout
- `backend/` — Spring Boot services, domain logic, and automated tests.
- `frontend/` — Angular single-page application.
- `docs/` — Business, technical, developer, and operations documentation.

## Environment setup
1. Clone the repository and `cd` into it.
2. Install backend dependencies via Maven (first test run will download them automatically).
3. Install frontend dependencies:
   ```bash
   cd frontend
   npm install
   cd ..
   ```
4. Configure any environment-specific overrides by copying `frontend/src/environments/environment.example.ts` to `environment.ts` if required (default points to `http://localhost:8080/api`).

## Running the platform locally
1. Start the backend:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   The API will be available at `http://localhost:8080/api`.
2. Start the frontend (in a separate shell):
   ```bash
   cd frontend
   npm start
   ```
   The UI is served at `http://localhost:4200` and proxies API calls to the backend URL defined in the environment file.

## Coding standards
- Backend code follows standard Spring idioms with constructor injection and immutable DTO records.
- Frontend code uses Angular standalone components, strongly typed interfaces, and RxJS for reactive state.
- All new code must be accompanied by technical documentation under `docs/technical/` that explains each line or contiguous block.
- Unit/integration tests are required for backend service layers; frontend logic should be covered by component/service specs where applicable.

## Testing & quality gates
- **Backend**: run `mvn test` from the `backend` directory. JaCoCo coverage must remain ≥ 70% for instructions, branches, lines, and complexity.
- **Frontend**: run `npm test -- --watch=false --browsers=ChromeHeadless` from the `frontend` directory. Ensure a passing suite before committing.
- **Linting**: Angular CLI linting can be invoked via `npm run lint`; Maven uses the default formatting plugins.
- **Continuous integration**: replicate the local commands above in your CI pipeline to guarantee parity.

## Git workflow
- Work from feature branches off `main` when collaborating.
- Commit logical units with descriptive messages and keep the working tree clean before submitting PRs.
- Each pull request must include:
  - Updated business, technical, developer (if necessary), and operations documentation.
  - Test evidence (command outputs) demonstrating all suites passed locally.
  - Summary of functional changes and verification steps.

## Troubleshooting tips
- If H2 seed data fails, ensure the backend is using the default profile (`spring.profiles.active=dev`).
- When exports fail, confirm `report_templates` and `report_columns` tables exist (they are populated by `data.sql`).
- For frontend CORS issues, verify the Angular proxy configuration in `frontend/proxy.conf.json` matches the backend base URL.

For further questions, contact the architecture team or refer to the business and technical documentation in the `docs/` directory.
