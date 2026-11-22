# Changelog

User-visible changes are recorded here. Executed verification and its limits remain in [validation](docs/validation.md).

## Unreleased

- Home keeps search/filter controls pinned in their chosen position, shows What’s new as a compact inline toggle when space is tight, and places admin Add beside search. Reader cards open details; editable-admin cards open Edit and long press starts bulk selection.
- New medications support per-section initial availability; existing-medication availability saves immediately. Bulk controls adapt to narrow/large-text layouts, read-only badges use compact height, and steps/checklists expose localized accessibility states.
- Notices/card tips can be dismissed for the session; snackbars include Close and finite timeouts. Home retains completed rows while matching results recompute, without loading skeletons; splash uses whole-logo growth and fade.

- Admin errors and manual refresh now provide specific feedback; automatic refresh avoids busy/offline/recent-sync work and honors Telegram cooldowns.
- Medication duplicate checks match names, strength and form, including spacing/digit variants; CSV adds flexible delimiters/status labels/section aliases, match actions and atomic 50-row batches.
- Admin PINs and connection passwords accept four or more digits; connection issuance offers secure generation, and same-PIN changes are rejected.
- History gains type filtering, shortage reports can cover all active sections, and compact cards show other available sections. Bulk previews remain complete with smaller saved selections.
- Production distribution supports universal, ARM32 and ARM64 APKs with matching signing identity and version.
- Hosted debug distribution uses a separate permanent signing identity and shares the production version-reservation sequence.
- Release bundles retain signing receipts, checksums and compressed R8 mappings; debug publications never take the Latest badge.

These entries describe source changes, not a published release or verified installed update.
