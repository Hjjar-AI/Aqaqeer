# Public repository review — 2026-11-14

This is a sanitized source/settings review, not an app or hosted-workflow execution result.

The GitHub API identified `Final` as the default branch and the repository as public. Secret scanning and push protection were enabled. Default workflow tokens were read-only and could not approve PRs. Private vulnerability reporting and Dependabot security updates were disabled; no rulesets were returned. Proposed rules are reviewable JSON in `.github/repository-settings/`; they were not applied. Server-side full-SHA enforcement was disabled (`sha_pinning_required=false`); all actions were allowed. Dependabot alert status could not be confirmed by the available endpoint. No paid features are requested.

Only two recent runs were available during review. This does not establish representative memory/CPU pressure, cold-build cost or an improvement from changed worker limits. Existing Gradle limits and job isolation remain unchanged. The ignored settings/timing receipt is `local/verification/repository-settings/review-20261114.json` (repository-root relative).

Historical local device/build evidence remains described in [validation](../validation.md), with local-only paths explicitly labeled. No original evidence was pruned. Existing historical passes do not validate this source revision.

## Focused settings update — 2026-11-15

With explicit user authorization, the API enabled and read back private vulnerability reporting (`enabled=true`), Dependabot alerts (HTTP 204) and security-update PRs (`enabled=true`, `paused=false`). Existing secret-scanning settings were preserved.

The two repository JSON rulesets were applied and their full requested fields compared against fresh API responses: **27280772**, Preserve default branch history; **27280775**, Preserve shared version reservations and production tags. Both are active, with no bypass actors. Direct pushes and new tags remain allowed; PRs/approvals/signatures are not required. Protected-history repairs require temporarily changing the affected rule.

Default branch remains `Final`. Server-side full-SHA enforcement, release immutability and broader dependency tooling were not enabled. These settings observations are not app/runtime verification.
