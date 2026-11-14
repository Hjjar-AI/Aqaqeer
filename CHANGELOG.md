# Changelog

User-visible changes are recorded here. Executed verification and its limits remain in [validation](docs/validation.md).

## Unreleased

- Admin errors and manual refresh now provide specific feedback; automatic refresh avoids busy/offline/recent-sync work and honors Telegram cooldowns.
- Medication duplicate checks match names, strength and form, including spacing/digit variants; CSV adds flexible delimiters/status labels/section aliases, match actions and atomic 50-row batches.
- Admin PINs and connection passwords accept four or more digits; connection issuance offers secure generation, and same-PIN changes are rejected.
- History gains type filtering, shortage reports can cover all active sections, and compact cards show other available sections. Bulk previews remain complete with smaller saved selections.

- Production distribution supports universal, ARM32 and ARM64 APKs with matching signing identity and version.
- Hosted debug distribution uses a separate permanent signing identity and shares the production version-reservation sequence.
- Release bundles retain signing receipts, checksums and compressed R8 mappings; debug publications never take the Latest badge.

These entries describe source changes, not a published release or verified installed update.
