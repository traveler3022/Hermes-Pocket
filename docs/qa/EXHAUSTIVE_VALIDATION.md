# Exhaustive Real-World Validation

This campaign is separate from routine per-commit CI. It validates a selected Hermes Pocket commit, preserves evidence, and exposes gaps rather than inferring health from a green build.

## Start a campaign

1. The first campaign starts automatically when the harness files change on claude/pairing.
2. GitHub only exposes workflow_dispatch for manual launches after the workflow file is present on the repository default branch. To enable repeated manual runs, merge this workflow into that branch.
3. For a manual run, select claude/pairing or enter another branch, tag, or commit SHA.
4. Keep live Gateway tests disabled unless secrets are configured for a disposable staging Gateway.
5. Inspect the workflow summary and download the exhaustive-validation artifact.

The workflow records the resolved commit SHA and uploads the APK, JUnit XML, instrumentation output, startup timing, logcat, screenshot, memory snapshot, and source-derived feature matrix. It is deliberately separate from routine CI.

## Optional live Gateway tests

ChatSwitchLiveTest and GatewayRecoveryLiveTest require the HERMES_LIVE_URL environment variable. Without it, JUnit reports their assumptions as skipped; a skip is not a pass and does not establish real Gateway compatibility.

Configure repository Actions secrets:
- HERMES_LIVE_URL: a disposable staging Gateway WebSocket URL accepted by the tests.
- HERMES_LIVE_MODEL (optional): a low-cost test model if required by the staging environment.

Then opt in when launching the workflow. These tests create real sessions and send prompts; do not target production or an endpoint where the traffic/cost is unacceptable. The secret URL is not printed.

## What is exercised

- Architecture layer-boundary check.
- Debug APK build and Gradle JVM/Robolectric test suite.
- Android instrumentation tests on API 35 x86_64; API 35 supports ARM ABI translation for the repository's arm64-only tsbridge AAR.
- Real app launch, process-liveness check, ActivityManager startup timing, logcat capture, screenshot, and one memory snapshot.
- Optional live Gateway reconnection and chat-switch suites.
- Source-derived implementation inventory, candidate test references, and 12 validation dimensions per source unit.

## Reading the report

JUnit test, failure, error, and skipped counts are separate. Test references are review leads, not evidence that a feature is covered. Every scenario row starts NOT RUN. Change a row to PASS only when the actual assertion and result are verifiable. Confirm likely-not-applicable decisions manually before recording NOT APPLICABLE.

The inventory is heuristic and may split one business feature across screens, ViewModels, repositories, services, and domain contracts. Reconcile it against navigation, release notes, permissions, protocol contracts, and dependency injection before treating it as a complete business-feature catalogue.

## Current boundaries

This provides a deeper validation pass, not proof of every possible state. The Android instrumentation source set currently contains one test; the Gateway live suites are JVM tests requiring a real external Gateway. A launch smoke check and one memory snapshot cannot certify performance, leaks, stress thresholds, or long-run stability. Those require repeated controlled runs and stated thresholds; any unimplemented or unexecuted validation must remain a gap.
