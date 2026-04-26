# ClawHub Handover Context

This repository is the control plane. It is not the business-instance repository.

## Repository Roles

### ClawHub control plane responsibilities

- manage users and tenant boundaries
- save instance metadata
- create, start, stop, restart, and delete containers
- present an instance list and entry links
- manage product-specific runtime templates such as image, ports, and volume mounts

### QwenPaw instance responsibilities

- run the actual product UI and APIs
- execute relay jobs
- manage local agent model/runtime behavior
- own in-instance chat, browser tasks, and execution flow

ClawHub must not absorb QwenPaw business logic.

## Known QwenPaw Conclusions

- QwenPaw remains a managed instance, not the platform itself
- volume persistence should be handled by `docker run` / container mounts rather than image changes
- first-version instance entry should open a new browser tab
- relay is currently an MVP and already works for text and browser tasks
- stream/event-based relay return is a future design topic, not part of ClawHub MVP

## ClawHub MVP Direction

- separate repository from QwenPaw
- Java/Spring Boot monolith first
- admin creates instances first; self-service user creation can wait
- first version focuses on one-user-one-instance or admin-assigned instance
- Docker image pull may be skipped on restricted servers; assume preloaded images and perform `create/start`
- each instance needs an isolated persistent volume, at minimum for the product home directory such as `~/.qwenpaw`

## Current Code Intent

The current scaffold implements:

- a Spring Boot backend skeleton
- managed-instance metadata persistence
- Docker CLI based lifecycle abstraction for container operations
- admin-oriented REST endpoints for instance lifecycle

The current scaffold does not yet implement:

- authentication/authorization
- a frontend console
- product-specific templates beyond basic request fields
- SSH/remote Docker host management
- SSO, iframe embedding, or unified portal session federation
