# Baseline Profile generation

Follow the [repository rules](../AGENTS.md), [Android implementation rules](../app/AGENTS.md), and [pinned build instructions](../docs/build.md). The [performance plan](../docs/plan.md#performance-tooling--planned) owns scope and priorities. For hosted workflow changes, also follow the [CI rules](../.github/AGENTS.md).

- Keep generation isolated and opt-in, with explicit Standard and Demo selectors. Use Standard journeys for any profile shipped to pharmacies. Keep generation outside ordinary builds, automatic CI, production signing, and release version reservations.
- Use deterministic startup, Home browsing and search, section switching, and medication detail journeys with public local or Demo data. Verify that each journey reaches its intended screen or state before retaining the generated profile.
- Apply the repository’s separate authorization rules for test authoring, packaging, emulator use, and workflow dispatch. For an authorized hosted run, use the owned emulator and follow the existing target identity and cleanup rules.
- Verify that the intended app artifact contains the profile. Retain the generated profile, source and artifact provenance, and the opt-in regeneration instructions and triggers. Record completed generation and packaging evidence in [validation](../docs/validation.md).
- Report generation and packaging results. Speed measurements and timing comparisons are separate work, and adoption does not depend on them under the current plan.
