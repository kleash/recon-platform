# Universal Reconciliation Platform - Developer Wiki

Welcome to the developer wiki for the Universal Reconciliation Platform. This wiki is the central source of truth for engineers building, extending, and operating the platform.

## 1. Getting Started

If you are a new developer on the project, start here.

- **[Getting Started Guide](./Getting-Started.md)**: A step-by-step guide to setting up your local development environment and running the application.

## 2. Core Documentation

These guides provide deep dives into the platform's architecture, data model, and APIs.

- **[Architecture Deep Dive](./Architecture.md)**: An overview of the system's architecture, key components, design patterns, and code organization.
- **[Database Schema Reference](./Database-Schema.md)**: Detailed reference for the core database tables, including maker-checker audit history and ingestion metadata.
- **[API Reference](./API-Reference.md)**: A complete reference for the backend REST API, including admin configuration endpoints and maker/checker workflow operations.
- **[Documentation Navigator](./Documentation-Navigator.md)**: Quick index of every guide and when to use it.

## 3. Development & Extension

Use these guides to understand common development workflows and learn how to extend the platform.

- **[Tutorial: Creating a New Reconciliation](./Tutorial-Creating-a-New-Reconciliation.md)**: A hands-on tutorial for adding a new reconciliation pipeline to the platform via configuration-driven metadata.
- **[Development Workflow Guide](./Development-Workflow.md)**: Information on building, testing, debugging, and other common development tasks.
- **[Admin Reconciliation Configurator Guide](./Admin-Configurator-Guide.md)**: Step-by-step instructions for using the admin workspace, schema exports, and ingestion helpers.
- **[Ingestion SDK](./ingestion-sdk.md)**: How to build Spring Boot ingestion jars using the reusable SDK, including JDBC and API adapters.
- **[Automation Regression Guide](../../automation/regression/README.md)**: How to execute the Playwright end-to-end suite and interpret reports.
- **[Global Multi-Asset Playbook](./Global-Multi-Asset.md)**: Business and technical blueprint for the six-source showcase reconciliation, including diagrams and automation steps.

## 4. Process & Governance

These documents cover the business-level processes and features of the platform.

- **[Onboarding Playbook](./onboarding-guide.md)**: A checklist and process guide for onboarding a new reconciliation from a business perspective.
- **[Feature Overview](./features.md)**: A description of the platform's features from a user's point of view.
- **[Maker-Checker Enhancements](./maker-checker-enhancements.md)**: Implementation guide and user experience notes for the enhanced approval workflow.
- **Completed feature requests:** Historical specifications that informed delivery now live in [`completed-features/`](./completed-features/README.md).
