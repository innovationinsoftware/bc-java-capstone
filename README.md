# Secure Digital Banking Platform Capstone

This directory contains the complete specification, student scaffolding, reference solution, and bootcamp curriculum materials for a full-stack, enterprise-grade banking application.

## Overview

This capstone is designed to provide students with a realistic, hands-on experience building a secure, distributed full-stack application. It reinforces modern architectural patterns, emphasizing the **Backend-For-Frontend (BFF)** security model to mitigate frontend vulnerabilities (like XSS) by keeping authentication tokens completely out of the browser.

Students will build the application using **Spring Boot 3** (Java 17), **React 18**, **Oracle Database**, and **Apache Kafka** to simulate an event-driven architecture with external payment processors (mocked via WireMock).

## Repository Structure

- **docs/**
  The original capstone specifications. Starts at 0-overview.md and covers architecture, domain models, API contracts, security (BFF), Kafka events, and testing rubrics.
  
- **simple/** (Student Scaffolding)
  The "bare-bones" starting point for the students. It contains the project skeleton, base configurations, and UI shells, but leaves the core entity mappings, controller logic, service validations, Kafka integrations, and Spring Security SecurityFilterChain configurations as TODOs for the students to complete over a 3-day sprint. Details can be found in simple/scaffolding/step-by-step-instructions.md.

- **solution/** (Reference Implementation)
  The fully completed, working reference solution. It perfectly satisfies all rubric criteria and implements the BFF security pattern (stateful HttpOnly sessions exchanging Authorization Codes with Google OAuth2). 

- **2609-Labs-Apr6-main/**
  Historical bootcamp labs and curriculum exercises spanning Spring Data JPA, Oracle Optimization, Kafka, and OAuth2 OIDC foundations that lead up to this capstone.

- **Capstone Banking Solution Implementation.md**
  Comprehensive implementation context, planner rationale, and architectural decisions (such as the migration to the BFF pattern).

## Technology Stack

- **Frontend:** React 18, React Router, Vite, Axios (with interceptors)
- **Backend:** Java 17, Spring Boot 3.2, Spring Security (OAuth2 Client), Spring Data JPA, Spring Kafka
- **Infrastructure (Docker):** Oracle Database, Apache Kafka (KRaft), WireMock - **to be updated to use standalone installations**
- **Identity Provider:** Google OAuth 2.0 (Authorization Code Flow)

## Quick Start

1. **Instructors/Evaluators:** Review the solution/ directory to see a fully functioning build. 
2. **Students:** Begin by reading docs/00-overview.md and follow the technical specifications. Your active workspace will be inside the simple/ directory. Check out simple/scaffolding/step-by-step-instructions.md for your guided 3-day development blueprint.
