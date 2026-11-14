# Aqaqeer-Final — Logic Enhancement Review

Scope: logic improvements for mainstream use. Extreme edge cases are deliberately excluded.

## How this review was done

- Source review only, based on `README.md`, `AGENTS.md`, `app/AGENTS.md` and the production Kotlin sources under `app/src/main/kotlin/com/aqaqeer/app`.
- Nothing was built, compiled, linted, run on a device or tested. No project file was changed (the zip was extracted to a scratch directory).
- Tests and migrations were not opened, per the repo rules.
- Read in depth: catalog rules, search/presentation, refresh and publication logic, admin edits and merge, PIN throttle and PIN change, publishing and restore reconcile, backup export/restore, history, CSV import, reports (CSV and PDF), connection import, the Telegram read transport, the ViewModel, and the main screens and components.
- Not read in depth: envelope/payload readers (crypto contracts), `SecureStore`, the maintenance coordinator's internals, theme/layout code.

## What already looks sound (leave alone)

- PIN throttle: persisted, resets the delay after reboot, delay capped, failures counted only after UI validation of six digits.
- Single-flight foreground refresh and the publication-ancestry checks (`publicationDecision`).
- Publish flow: write-ahead attempt records, re-inspection before mutation, read-back verification.
- Merge conflict handling in `CatalogEdits.merge`, including deletions and taxonomy collisions.
- Bounded, strict CSV parsing and spreadsheet-safe export.
- Backup export validates by restoring into a staging database before writing the destination.

## Summary table

| # | Area | Item | Value | Risk |
|---|------|------|-------|------|
| 1 | UX/errors | Specific error messages swallowed by `command` | High | Low |
| 2 | Refresh | No throttle/offline/busy guard on auto-refresh | High | Low |
| 3 | Catalog | New section shows every medication as "unknown" | Medium | Needs decision |
| 4 | History | Never pruned; filter lists recomputed each emission | Medium | Low–Med |
| 5 | Performance | Every edit rewrites the whole catalog | High (if lag) | High |
| 6 | Search | Minor normalization/coverage gaps | Low | Low |
| 7 | CSV | Duplicate detection uses names only | High | Low |
| 8 | Catalog | No duplicate check on manual add/edit | High | Low |
| 9 | CSV | Preview cost grows with file size | Medium | Low |
| 10 | CSV/Admin | Per-row full edit; quadratic pending folding | High | Low (grouping) |
| 11 | CSV | Delimiter, status parsing, vague errors | Medium | Low |
| 12 | Reports | `status()` is quadratic | Medium | Low |
| 13 | UX/errors | Publish/restore swallow specific messages | High | Low |
| 14 | Home | No expiry filter | Medium | Low |
| 15 | Backup | Reminder ignores whether anything changed | Low | Low |
| 16 | Home | "Available elsewhere" only for OUT | Low | Needs decision |
| 17 | PIN | Weak and no-op PINs accepted | Medium | Low |
| 18 | Catalog | Strength stored un-normalized | Medium | Low |
| 19 | Reports | Expiring report excludes expired items | Medium | Needs decision |
| 20 | Reports | Single-section shortage report only | Medium | Low |
| 21 | Refresh | Manual refresh gives no result | Low | Low |
| 22 | Startup | One failure goes straight to recovery screen | Low–Med | Low |
| 23 | Misc | Small cleanups | Low | Low |
| 24 | Home | Compact layout hides "available elsewhere" | Medium | Low |
| 25 | Refresh | `getMe` on every refresh; 429 handling | Low | Low |
| 26 | Performance | Diffs JSON-encode every row | Medium | Low |
| 27 | Reports | PDF is one vertical card per medication | Medium | Needs device check |
| 28 | History | No entity-type filter | Low | Low |
| 29 | Import | Whole observe flow used for a simple check | Low | Low |
| 30 | Sharing | Connection-file password UX | High | Low |
| 31 | Home | Bulk selection preview and saved-state size | Medium | Low |
| 32 | Admin | Pending banner counts operations, not changes | Low | Low |
| 33 | CSV | Update flow needs too many taps | Medium | Low |
| 34 | CSV | Section headers require UUIDs | Low | Low |

## Suggested order of work

1. **Safe and visible:** 1 + 13, 2, 7 + 8 + 18, 10 (the pending-grouping part only), 9, 12, 17 + 30, 21, 24.
2. **Medium:** 3, 4, 11, 14, 19, 20, 22, 25, 26, 28, 31, 32, 33.
3. **Larger or riskier:** 5 and 27. Do these only if you observe lag or page-count problems.
4. **Low priority:** 6, 15, 16, 23, 29, 34.

Decisions needed from you: items 3, 16 and 19.

---

## A. Errors and feedback

### 1. Specific error messages are swallowed (`CatalogViewModel.command`)

**Problem.** `command()` shows its own text only for `MaintenanceProblem`, `CsvProblem` and `EnvelopeException`. Every other failure becomes the generic string `ui_catalog_view_model_012` ("تعذر إتمام العملية…"). That includes the Arabic reasons thrown by `AdminRepository`:

- wrong PIN ("رمز الإدارة غير صحيح") and the throttle wait ("انتظر قليلاً قبل المحاولة التالية"),
- section-delete name mismatch ("اكتب اسم القسم لتأكيد الحذف"),
- stale preview ("تغيرت بيانات مختارة؛ أعد المعاينة"),
- "القسم لم يعد نشطاً", "لم يعد هذا الدواء موجوداً".

A wrong PIN looks the same as a storage failure.

**Suggested change.** Add a small `AdminProblem(reason: String)` exception, throw it at the existing user-facing `check`/`require` sites, and show `reason` in `command`. Do not surface arbitrary exception messages: Room and SQLCipher also throw `IllegalStateException` with English text that must stay hidden (the repo comments say SQL, keys and filenames must never reach the UI).

### 13. Publishing and restore swallow specific messages too

**Problem.** `PharmacySharing.publishLocked` throws precise reasons: "تغير رمز الإدارة؛ أعد التفويض", "يلزم تحديث التطبيق", "أكمل إعداد تيليغرام أولاً", "المجموعة تحتوي صيدلية أخرى". `publish()` collapses everything except `TelegramFailure` and `ReviewRequired` into one generic sentence. `reconcileRestore()` does the same.

**Suggested change.** Reuse the `AdminProblem` type from item 1 in both places.

### 21. Manual refresh gives no result

**Problem.** `refreshCatalog()` in the ViewModel discards the `RefreshOutcome` (UNCHANGED / APPLIED).

**Suggested change.** On a manual refresh, show a brief message such as "updated" or "already current", optionally with the number of applied changes (already computed for history).

---

## B. Refresh and connectivity

### 2. Auto-refresh has no throttle, offline check or busy guard

**Problem.** `AqaqeerApp` calls `refreshCatalog()` on every `ON_START`, including when returning from the system file pickers used by CSV, backup and report flows. `refreshCatalog()` only checks `refreshState.busy`. It ignores connectivity and `adminBusy`. `SnapshotRefreshRepository.apply()` throws `LOCAL_STATE_CHANGED` if anything changed while the read was in flight, so a refresh during an admin action risks a spurious error.

**Suggested change.** For the automatic call only, skip when any of these hold:

- the last successful sync is under about 5 minutes old,
- `state.online` is false,
- `adminBusy` is true.

Keep the manual refresh unthrottled.

### 25. `getMe` on every refresh; 429 handling

**Problem.** `SnapshotReadClient.readWithCache` calls `transport.bot()` (`getMe`) and then `getChat` twice before any download. The bot identity is stable for a given token. A 429 is mapped to `RATE_LIMITED` and `retry_after` is ignored on the read path.

**Suggested change.** Cache the `BotIdentity` in memory per token (keyed by a hash of the token, never the token) for the process lifetime. Optionally carry `retry_after` into the refresh error message.

### 22. One failure sends the app to the recovery screen

**Problem.** In `CatalogRepository.observe`, any exception becomes `CatalogState.RecoveryRequired` and ends the flow. Only the user's retry restarts it.

**Suggested change.** Retry the flow once automatically after a short delay before emitting `RecoveryRequired`. Recovery stays non-destructive, so this is safe.

---

## C. Catalog, search and duplicates

### 3. A new section shows every medication as "unknown"

**Problem.** `saveTaxonomy` adds a section without creating availability rows, so every medication reads UNKNOWN there. A new medication, in contrast, gets AVAILABLE in all sections (`saveMedication`). `MedicationPresentation.editableStatuses` excludes UNKNOWN, so the status filter cannot find the unset items.

**Suggested change.** Either add an "unknown" option to the status filter, or let the admin choose an initial status when creating a section.

**Decision needed.** Is a new medication defaulting to AVAILABLE in every section intended? Readers will trust that value.

### 8. No duplicate check on manual add or edit

**Problem.** `CatalogRules.validate` checks taxonomy uniqueness only. The same medication can be added twice by hand, while CSV import is strict.

**Suggested change.** Add a soft warning ("same name, strength and form already exists") that lets the admin continue. Use the same key as item 7.

### 18. Strength is stored as typed

**Problem.** `MedicationEditor` saves `strength.trim()`. "500mg", "500 mg" and Eastern-digit "٥٠٠ mg" are different values, which undermines duplicate detection, sorting and grouping.

**Suggested change.** Normalize on save: Western digits and collapsed whitespace. The expiry field already normalizes digits (`GregorianDateField`).

### 14. No expiry filter on Home

**Problem.** Expiry badges, `MedicationPresentation.expiry()` and an "expiring soon" report exist, but `HomeFilters` only has status and category.

**Suggested change.** Add an EXPIRED / SOON filter. The search index already has everything required (pharmacy timezone for "today").

### 24. Compact layout hides "available elsewhere"

**Problem.** `HomePreferences` defaults to COMPACT, and `MedicationCard` sets `elsewhere = emptyList()` in compact mode. For an out-of-stock item this is the most useful detail, and most users never see it.

**Suggested change.** Show a one-line "متوفر في: …" for OUT items in compact mode as well.

### 16. "Available elsewhere" only applies to OUT

**Problem.** `availableElsewhere` returns results only when the current status is OUT.

**Suggested change.** Consider also showing it for LOW, if that matches the intended workflow.

### 6. Search: small gaps

- `normalizedSearch` folds hamza on alef and ء but not ؤ and ئ.
- Search covers names, brand and strength, but not form or category names.

---

## D. History

### 4. History is never pruned; filter lists are recomputed

**Problem.**

- The `history` table is only cleared on leave or restore. Each applied refresh adds a row per changed field plus a publication row, and recovery candidates store the full payload.
- `HistoryRepository.observe` recomputes `medicationOptions` and `editorOptions` (full-table group-by with correlated subqueries) on every emission.

**Suggested change.**

- Add a retention rule, for example older than 12 months or capped at N rows, never touching entries whose operation is still pending.
- Compute the option lists only when the filter sheet opens, or cache them.

### 28. No entity-type filter

**Problem.** Filters are date, editor and medication. Availability toggles dominate the list.

**Suggested change.** Add a filter for availability, medication details and taxonomy. It is a small SQL clause on `entityType`.

---

## E. Performance

### 5. Every edit rewrites the whole catalog

**Problem.** `AdminRepository.edit()` ends with `dao.replace(next)`, which deletes and reinserts all categories, forms, sections, medications and availability. The observe flow then reloads and re-validates the whole catalog and the ViewModel rebuilds the search index. One status toggle on a catalog of a few thousand medications across up to five sections writes thousands of rows. I have not measured this.

**Suggested change.** Apply only the changed rows (upsert/delete by id), using the changes already computed in `edit()`. This needs new DAO methods and touches the core write path, so do it last, and only if you see lag.

### 10. CSV commit and the quadratic pending-folding loop

**Problem.**

- `CsvImportRepository.commit` runs one `admin.edit` per row, so N rows mean N full-catalog rewrites (item 5).
- Inside `edit()`, the "fold additions" loop filters the whole pending list once per addition: additions × pending per edit. During a large offline import pending grows with each row, so the cost grows progressively.

**Suggested change.**

- Group pending operations by (entityType, targetId) once per edit. This is small and safe.
- Optionally commit in chunks of about 50 rows with per-row result tracking preserved. This is a bigger change; the "never auto-replay uncertain writes" rule must still hold.

### 26. Diffs JSON-encode every row

**Problem.** `CatalogEdits.rows()` and `RemoteObservationHistory.records()` serialize every medication and availability row of both catalogs just to find the few that changed. This runs on every edit and on every applied refresh (the latter inside the DB transaction).

**Suggested change.** Compare typed rows by id with `==` first and encode only the ones that differ. Results stay identical.

### 29. Connection import opens the whole observe flow for a simple check

**Problem.** `requireUnconfigured()` and `loadPending()` call `catalog.observe().first { … }` (lease, seed check, transaction, invalidation tracker). Stage, verify and activate each call it twice.

**Suggested change.** Use a direct DAO check such as `pharmacy() != null`. The cost is small today because the catalog is empty at that point.

---

## F. CSV import

### 7. Duplicate detection uses names only

**Problem.** `CsvRowPlanner.names()` compares the four name fields and ignores strength and form. A row for another strength or form of an existing generic (for example Haloperidol 5 mg and 10 mg, or tablet and syrup) is flagged as a duplicate and needs a manual decision. The same happens for two such rows inside one file.

**Suggested change.** Define a duplicate as the same normalized name, strength (digits normalized, see item 18) and form.

### 9. Preview cost grows with file size

**Problem.**

- Duplicate detection re-normalizes every medication's names for every row (rows × medications × 4 normalizations; up to 10,000 rows).
- `revise()` re-previews the whole file every time one row's decision changes.

**Suggested change.** Build a normalized-name index once per preview. On `revise()`, re-plan only the changed row plus rows that share its target or name.

### 11. CSV friendliness

**Problems.**

- Only `,` is accepted. Arabic-locale Excel often saves with `;`, which fails with "unknown columns".
- Section status cells use `AvailabilityStatus.valueOf`, so `available` or `متوفر` fails, and UNKNOWN is accepted as input.
- Any parse failure becomes the generic "قيم غير صالحة…".

**Suggested change.**

- Detect `;` or tab from the header line.
- Accept case-insensitive English and the Arabic status labels; reject UNKNOWN as an input.
- Name the column and value in errors.

### 33. Update flow needs too many taps

**Problem.** Even when exactly one duplicate is detected, the admin must pick the UPDATE target from a list of every medication.

**Suggested change.**

- Preselect the single match with a one-tap "update the matching medication".
- Add bulk actions: "update all matching" and "skip all duplicates".

### 34. Section headers require UUIDs

**Problem.** Headers must be `section:<uuid>`, so an admin editing in Excel must keep them exactly.

**Suggested change.** Accept `section:<section name>` (normalized, exact match) as an alias and reject it if ambiguous. The template can stay UUID-based.

---

## G. Reports

### 12. `ReportSnapshot.status()` is quadratic

**Problem.** It runs a linear `find` over all availability rows for every (medication, section) pair.

**Suggested change.** Use a map keyed by (medication, section), as `CatalogSnapshot` already does.

### 19. The expiring report excludes expired medications

**Problem.** `EXPIRING_SOON` keeps only `ExpiryState.SOON`, and the CSV `expiryPolicy` line says so. Expired stock is usually the most urgent.

**Suggested change.** Include EXPIRED as a flagged or first group.

**Decision needed.** This is a policy choice.

### 20. The shortage report covers one section only

**Problem.** The OUT preset requires exactly one section. Inactive sections appear in report columns (the PDF labels them "قسم مغلق").

**Suggested change.** Add an "all sections" shortage option, and omit inactive sections by default unless requested.

### 27. The PDF is one vertical card per medication

**Problem.** Each medication takes roughly 10–12 lines, about four per page. A full report of 1,000 medications is around 250 pages, and the 1,000-page cap is reached near 4,000.

**Suggested change.** A compact one-line row per medication with sections as columns. The code comment says Arabic rendering still needs device review, so verify any layout change on a device.

---

## H. PIN and sharing

### 17. Weak and no-op PINs are accepted

**Problem.** `AdminRepository.create` and `PinChanges.change` require only six digits. `000000` and `123456` pass. "Changing" to the same PIN also succeeds, because a new salt gives a new verifier, which creates a pending operation and a publication revision for no real change.

**Suggested change.** Reject all-same-digit and straight-sequence PINs, and reject a new PIN that matches the current one (via `PinSecurity.matches`).

### 30. The connection-file password is easy to get wrong

**Problem.** `EnvelopeWriter.connection` requires digits only, at least 16. The screen only shows a hint. The field has no inline validation, so a normal passphrase fails with the generic error from item 1. Nothing prevents patterns like `1234123412341234`.

**Suggested change.**

- Add a "generate 16 digits" button using `SecureRandom`, shown once with a copy action.
- Add inline validation that rejects repeated, sequential and date-like patterns.

### Design-level note (not recommended for change now)

The PIN verifier is part of the shared snapshot. Anyone holding the shared credentials could guess a six-digit PIN offline (the throttle only protects on-device attempts). The README already notes that shared credentials do not cryptographically enforce reader rights. Fixing this needs a longer PIN or a format change, so it is only flagged here.

---

## I. Bulk selection and the pending banner

### 31. Large bulk selections are heavy to display and to save

**Problem.**

- `AvailabilityEditor` and `BulkMedicationEditor` render every selected medication in a non-lazy column for the preview.
- `AvailabilitySelectionSaver` writes the full selection, including a copy of every `Medication`, into the saved-instance bundle.
- With "select visible" on a large section this means slow composition and a risk of exceeding the bundle size limit when the app is backgrounded.

**Suggested change.**

- Cap the preview ("first 20 plus a count, expand for all").
- Save only the ids and expected statuses, and rebuild the medications from the catalog on restore.

### 32. The pending-changes banner counts field operations

**Problem.** `pendingCount` is `COUNT(*)` of pending operations. Adding one medication to a three-section catalog reads as 4 changes, and editing three fields reads as 3.

**Suggested change.** Show distinct changed items instead, using the `unpublishedMedicationIds` set plus taxonomy edits, or count distinct transactions.

---

## J. Smaller items

### 15. Backup reminder ignores whether anything changed

`BackupReminder.nextAt()` only looks at time (one week after the latest of first admin, last export and dismissal). Remind only if there are edits or new history since `lastBackupAt`.

### 23. Small cleanups

- Leaving a pharmacy leaves its `selected_section_<id>` preference behind.
- The medication form does not warn about an expiry date already in the past.
- `AdminRepository.edit` uses `Instant.now()` while `create` and `promote` use the injected clock, so time handling is inconsistent.

---

## Repo rules to respect when applying these

- No tests, migrations, builds, lint or version changes unless you explicitly ask for them.
- Preserve existing DI/Compose patterns, 48dp touch targets, RTL, large text, focus, reduced motion and all themes and fonts.
- Set busy guards before writes, cancel stale reads, preserve drafts and selections, and never auto-replay uncertain writes (answers, toggles, creates, finish, sync).
- Keep README and docs compact, with current behavior only.
