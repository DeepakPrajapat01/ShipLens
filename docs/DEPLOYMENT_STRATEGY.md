# Deployment Strategy

## Purpose
Describe deployment principles and release gates for the planned hosted platform.

## Scope
Initial deployment should favor a simple, observable architecture with a control plane and isolated execution capacity. Cloud provider, hosting topology, and orchestration platform remain undecided.

## Deployment principles
- Deploy the modular monolith before decomposing logical modules into services.
- Separate untrusted execution workers from API/control-plane workloads.
- Keep PostgreSQL managed or operationally isolated with backups and tested restore.
- Use external secret management and least-privilege service identities.
- Use immutable build artifacts and pinned dependencies/base images.
- Apply Flyway migrations as a controlled release step.
- Support health checks, graceful shutdown, and bounded worker draining.
- Roll out incrementally and retain a rollback/forward-fix plan.

## Environments
At minimum, define development, test/staging, and production environments with separate credentials, databases, GitHub App configuration, and artifact storage. Customer source must not leak between environments.

## Release sequence
1. Build and verify immutable artifacts.
2. Run unit, integration, security, and fixture evaluation tests.
3. Review dependency and container image vulnerabilities.
4. Back up production data before risky migrations.
5. Apply backward-compatible database migration.
6. Deploy API/dashboard components.
7. Deploy compatible workers and execution images.
8. Run smoke tests and verify metrics/logs.
9. Gradually enable repository analysis.
10. Monitor error rates, queue age, resource use, and analysis completeness.

## Rollback
Application rollback must remain compatible with database schema state. Prefer expand-and-contract migrations. Destructive schema changes require a forward-fix or explicit recovery plan; do not assume a migration can be reversed without data loss. Disable new analysis triggers or worker execution if isolation or correctness is compromised.

## Scaling
Scale API and analysis/execution workers independently only when measured load requires it. The initial design does not mandate Kafka, Redis, or Kubernetes. Revisit these technologies using recorded throughput, latency, queue depth, and operational complexity.

## Security gates
Before customer-facing production:
- Review GitHub App permissions and webhook validation.
- Validate tenant isolation.
- Review Docker execution isolation and network policy.
- Verify secrets handling and log redaction.
- Define retention/deletion and incident response.
- Test backups and restoration.
- Review deployment and image supply chain.

## Dependencies
See `../01-ARCHITECTURE/SECURITY_ARCHITECTURE.md`, `../04-INTEGRATIONS/DOCKER_EXECUTION.md`, and `MONITORING_STRATEGY.md`.

## Acceptance criteria
- Deployments are repeatable and artifacts immutable.
- Migration compatibility is reviewed.
- Worker isolation is maintained in production.
- Backup restoration and rollback/forward-fix procedures are tested.
- Production enablement has explicit security and operational approval.

## Open questions
- Cloud provider and deployment region.
- Container runtime and worker isolation model.
- Managed PostgreSQL and artifact storage choice.
- CI/CD provider and release approval workflow.
- Availability, recovery point, and recovery time objectives.
