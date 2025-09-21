Project: Universal Reconciliation Platform
1. Project Vision & Executive Summary

To build a highly configurable and scalable reconciliation platform that can be adapted to any type of reconciliation process. The platform will automate matching, provide a dynamic user interface for break investigation, implement a robust workflow for break resolution (including maker-checker), and ensure strict access control. The core architectural principle is a common, reusable engine powered by reconciliation-specific configurations.

2. Core Architectural Principles

Configuration over Code: The platform should be driven by metadata. Defining a new reconciliation should be a configuration exercise, not a new development project (excluding the initial data extraction/transformation layer).

Pluggable Data Layer: The core engine will be agnostic to the source data's origin, relying on a standardized format in MariaDB prepared by a unique ETL layer for each reconciliation.

Security First: Granular access control is paramount. Users should only see and interact with the data they are authorized for.

Scalability & Performance: The matching engine and database must be designed to handle large volumes of data efficiently.

3. Feature Plan: Epics & User Stories

We will group features into logical Epics, which are large bodies of work. Each Epic contains smaller, actionable User Stories.

EPIC 1: Core Reconciliation Engine & Configuration

This epic covers the "brains" of the platform—the non-UI components that perform the actual reconciliation.

User Story 1.1 (Recon Definition): As a Reconciliation Administrator, I want to define a new reconciliation type in the system by specifying its name, description, and associated metadata (e.g., fields, data types).

User Story 1.2 (Data Source Config): As a Reconciliation Administrator, I want to configure the source MariaDB tables and columns for a given reconciliation, so the matching engine knows where to pull data from.

User Story 1.3 (Field Role Config): As a Reconciliation Administrator, I want to tag each field as a "Matching Key," "Comparison Field," or "Display-Only Field," so the engine can perform its logic correctly.

User Story 1.4 (Matching Rule Config): As a Reconciliation Administrator, I want to define comparison rules for each "Comparison Field" (e.g., Exact Match, Case-Insensitive Match, Numeric Match with +/- 5% threshold, Date Match ignoring time).

User Story 1.5 (Trigger Mechanism): As a System, I want to trigger the matching process based on configurable events:

A CRON scheduler (e.g., daily at 5 AM).

An API call being received.

A Kafka message on a specific topic.

The successful load of a minimum number of required source files.

User Story 1.6 (Matching Execution): As the Matching Engine, I will execute the configured rules to categorize records into "Matched," "Mismatched," and "Missing in Source X." The results will be stored for the UI to display.

EPIC 2: User & Access Management (Security)

This epic focuses on ensuring only the right people can see the right data.

User Story 2.1 (Security Groups): Setup LDAP via docker and define initial security groups.

User Story 2.2 (User Provisioning): As a System Administrator, I want to create users and assign them to one or more security groups in LDAP.

User Story 2.3 (Access Control Matrix): As a System Administrator, I want to grant security groups access to specific combinations of Product, Sub-Product, and Entity.

User Story 2.4 (Data Segregation): As a User, I only want to see the list of reconciliations and the data within them that my security group has been granted access to.

EPIC 3: Reconciliation Dashboard & Break Investigation UI

This epic covers the primary user-facing screens for viewing and analyzing reconciliation results.

User Story 3.1 (Dynamic Recon List): As a User, when I log in, I want to see a list of only the reconciliation processes I have access to.

User Story 3.2 (Dynamic Dashboard): As a User, after selecting a reconciliation, I want to see a dashboard with a summary table of the latest run, showing counts for Matched, Mismatched (Breaks), and Missing items.

User Story 3.3 (Dynamic Filtering): As a User, I want to filter the dashboard view using dropdowns for Product, Entity, and Sub-Product, which are dynamically populated based on the selected reconciliation. I also want to filter by break status (Open, Closed, Pending Approval).

User Story 3.4 (Drill-Down View): As a User, when I click on a mismatched row in the dashboard, I want a split-screen or detailed view showing the data from Source A and Source B side-by-side, with the specific mismatched fields highlighted in red.

User Story 3.5 (Data Export): As a User, I want to export the currently filtered view of the dashboard table to an Excel file for offline analysis.

EPIC 4: Break Management & Workflow (Maker-Checker)

This epic focuses on the lifecycle of a break, from identification to resolution.

User Story 4.1 (Break Creation): As the System, I will automatically create a "Break" record with a status of 'OPEN' for every Mismatched or Missing item identified by the matching engine.

User Story 4.2 (Break Annotation): As a Maker, I want to select one or more breaks and add a comment, attach a supporting file (e.g., an email confirmation), and assign a reason code.

User Story 4.3 (Maker Action): As a Maker, after investigating, I want to change the status of a break to 'Pending Approval' or 'Closed' (if a checker is not required).

User Story 4.4 (Workflow Configuration): As a Reconciliation Administrator, I want to configure, per reconciliation, whether a maker-checker workflow is required. If so, I want to designate which security groups are "Makers" and which are "Checkers."

User Story 4.5 (Checker Queue): As a Checker, I want to see a queue of breaks that are in 'Pending Approval' status.

User Story 4.6 (Checker Action): As a Checker, I want to review the break, along with the maker's comments and attachments, and either 'Approve' (moves to Closed) or 'Reject' (moves back to Open with my comments).

User Story 4.7 (Bulk Actions): As a Maker or Checker, I want to select multiple breaks from the dashboard and perform a bulk update (e.g., assign the same comment and close 50 related breaks at once).

EPIC 5: Reporting & Exporting

This epic covers the generation of formal, auditable reports.

User Story 5.1 (Report Generation): As a User, I want to generate a full reconciliation report in Excel for a specific business date.

User Story 5.2 (Configurable Report Template): As a Reconciliation Administrator, I want to configure the layout of the Excel report, including which columns to include, their order, and custom headers.

User Story 5.3 (Rich Report Content): The generated Excel report must contain separate tabs for Matched, Mismatched, and Missing items.

User Story 5.4 (Report Formatting): In the report, mismatched columns should be highlighted. For numeric threshold mismatches, the report should show the actual difference. The report must also include the final break status, all user comments, and a link to any attached files.

EPIC 6: System Auditing & Monitoring

This epic ensures transparency and traceability of all system activities.

User Story 6.1 (Activity Page): As a Support User or Administrator, I want to view an activity page that shows a timeline of events for each reconciliation, including when source files were received, when the matching engine started and finished, and who triggered it.

User Story 6.2 (Audit Trail): As an Auditor, I need the system to log every significant action on a break (comment added, status changed, file attached), including the user's name and a timestamp. This audit trail should be easily viewable.

4. Conceptual Database Design (MariaDB)

Here is a high-level schema to support the features above.

Reconciliation_Definitions: Stores the master configuration for each recon.

id, name, description, source_a_table, source_b_table, is_maker_checker_enabled

Reconciliation_Fields: Defines the fields for each recon.

id, recon_definition_id, field_name, data_type, field_role ('KEY', 'COMPARE', 'DISPLAY'), comparison_logic ('EXACT', 'THRESHOLD'), threshold_value

Reconciliation_Runs: A log for each time a reconciliation is executed.

id, recon_definition_id, run_datetime, status ('SUCCESS', 'FAILED'), trigger_type ('API', 'SCHEDULE')

Break_Items: The core table holding all breaks.

id, recon_run_id, break_type ('MISMATCH', 'MISSING_IN_A'), status ('OPEN', 'PENDING', 'CLOSED'), data_from_source_a (JSON), data_from_source_b (JSON)

Break_Comments: A log of all actions on a break.

id, break_item_id, user_id, comment_text, action_performed, timestamp

Attachments: Stores metadata about uploaded files.

id, break_item_id, file_name, file_path_s3, uploaded_by_user_id

Users: System users.

id, username, email

Security_Groups: User groups.

id, group_name

User_Group_Mappings: Links users to groups.

user_id, group_id

Access_Control_List: Defines what groups can see.

group_id, recon_definition_id, product, sub_product, entity, role ('MAKER', 'CHECKER', 'VIEWER')

System_Activity_Log: Tracks high-level system events.

id, event_type ('FILE_RECEIVED', 'RECON_STARTED'), event_details, timestamp

5. Proposed Phased Rollout (MVP Approach)

It's crucial to deliver value incrementally.

Phase 1: Minimum Viable Product (MVP)
Goal: Get the first, simplest reconciliation running end-to-end.

Features:

Core Engine: Ability to configure ONE reconciliation with exact matching only (Epic 1).

Security: Basic user login and role (Admin vs. User) (Simplified Epic 2).

UI: A simple dashboard to view matched/mismatched items and drill down. No dynamic filtering yet (Simplified Epic 3).

Break Management: Users can add comments and manually change status from OPEN to CLOSED. No formal maker-checker (Simplified Epic 4).

Basic Export to Excel (Simplified Epic 5).

Phase 2: Core Functionality Enhancement
Goal: Build out the core configurability and workflow.

Features:

Full Matching Engine configuration (thresholds, date logic) (Epic 1).

Full Security Model with granular access control (Epic 2).

Full maker-checker workflow (Epic 4).

Dynamic filtering on the UI (Epic 3).

System Activity Page (Epic 6).

Phase 3: Mature Platform
Goal: Add advanced features and user experience improvements.

Features:

Configurable Reporting Engine (Epic 5).

Bulk update capabilities for breaks (Epic 4).

Advanced dashboarding and analytics.

API-based triggers for matching (Epic 1).

# Technology Stack

| Layer | Technology | Notes |
| --- | --- | --- |
| Backend | Java (Spring Boot) | Microservice-ready backend for the reconciliation engine and APIs. |
| Database | MariaDB | Stores configuration, reconciliation runs, and audit data. |
| Frontend | Angular 17 | Targeting Angular 17 to leverage the current LTS feature set and stability. We will monitor Angular release announcements and use the Angular Update Guide to assess compatibility as new major versions ship, scheduling upgrades in a hardening sprint once dependencies and automated tests pass on the new release. |
| Authentication | JWT (JSON Web Tokens) | Enables stateless authentication across services. |
| Styling | Modern UI library (TBD) | Final selection will be confirmed during UI design. |
Project Plan Reference: We will be following the feature plan I have previously outlined, which includes these core Epics:
