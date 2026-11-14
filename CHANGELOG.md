# Changelog

User-visible changes are recorded here. Executed verification and its limits remain in [validation](docs/validation.md).

## Unreleased

- Production distribution supports universal, ARM32 and ARM64 APKs with matching signing identity and version.
- Hosted debug distribution uses a separate permanent signing identity and shares the production version-reservation sequence.
- Release bundles retain signing receipts, checksums and compressed R8 mappings; debug publications never take the Latest badge.

These entries describe source changes, not a published release or verified installed update.
