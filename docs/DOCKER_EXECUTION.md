# Docker Test Execution

## Purpose
Define the security and operational contract for running selected tests from untrusted Java/Maven repositories in Docker.

## Scope
Docker is the initial isolation mechanism for Maven/JUnit 5 test execution. This document is a security baseline, not a claim that Docker alone eliminates container-escape risk.

## Trust boundary
Repository source, Maven wrapper, POM files, plugins, test code, dependencies, and test output are untrusted. They must never execute directly on the control-plane host.

## Execution lifecycle
1. Receive a run-scoped execution request containing immutable revision and selected test/module information.
2. Validate that the request is authorized and within resource limits.
3. Prepare a disposable workspace containing only the required repository snapshot and generated execution metadata.
4. Create a container from an approved, pinned Java/Maven image.
5. Apply non-root user, CPU/memory/PID limits, timeout, bounded writable storage, and restricted capabilities.
6. Mount only the workspace required for the run; avoid host home, Docker socket, credentials, and control-plane configuration.
7. Disable network access by default. Any dependency-download exception must be explicitly designed, allowlisted, and reviewed.
8. Execute a fixed, validated command template; do not interpolate arbitrary user-controlled shell fragments.
9. Capture bounded stdout/stderr, exit code, test summary, timing, and infrastructure diagnostics.
10. Stop and remove container and temporary resources; verify cleanup.
11. Normalize outcome and persist metadata/report references.

## Minimum controls
- Non-root execution.
- No privileged mode.
- Drop unnecessary Linux capabilities.
- No Docker socket mount.
- No host filesystem mounts beyond the isolated workspace.
- CPU, memory, PID, disk, and wall-clock limits.
- Bounded log/output size.
- Restricted or disabled network.
- Pinned image digest/version and controlled image provenance.
- Container/workspace cleanup on success, failure, timeout, and cancellation.
- No platform secrets or customer credentials in environment variables.
- Separate worker identity and permissions from control plane.

## Maven-specific risks
Maven plugins and wrapper scripts can execute arbitrary code. Dependency resolution may require network access. A build can spawn subprocesses, consume resources, or attempt filesystem/network access. Therefore, dependency prefetching, offline mode, plugin allowlisting, and network policy need an explicit implementation decision. Do not assume `mvn test` is safe merely because it is a conventional command.

## Outcome model
Distinguish:
- `PASSED`
- `TEST_FAILURE`
- `BUILD_FAILURE`
- `TIMED_OUT`
- `CANCELLED`
- `INFRASTRUCTURE_ERROR`
- `NOT_RUN`

The exact enum may be aligned with the platform error model. A nonzero exit code alone may not distinguish all categories; parse bounded reports and retain raw process status.

## Cancellation and cleanup
Cancellation must terminate the process/container, enforce a bounded grace period, and clean resources. A cleanup failure is an operational/security event and must be surfaced.

## Acceptance criteria
- No repository code runs on the control-plane host.
- Execution limits and network policy are enforced and tested.
- Secrets and privileged host resources are not exposed.
- Outcomes distinguish test failure from infrastructure failure.
- Cleanup is verified after every terminal state.

## Dependencies
See `../01-ARCHITECTURE/SECURITY_ARCHITECTURE.md`, `../01-ARCHITECTURE/SYSTEM_ARCHITECTURE.md`, and `../02-ENGINEERING/TESTING_STRATEGY.md`.

## Open questions
- Docker daemon/worker topology and hosting environment.
- Whether rootless Docker or stronger sandboxing is required.
- Maven dependency strategy under default network denial.
- Approved base image and patching cadence.
- Resource-limit defaults based on representative repositories.
- Handling tests requiring external databases, services, or credentials.
