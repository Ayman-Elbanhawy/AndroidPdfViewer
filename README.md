# AndroidPdfViewer Enterprise Fork

This fork keeps the original Pdfium-based Android PDF renderer as a reusable module and layers a Kotlin, AndroidX, Material 3, Compose-based enterprise document platform on top of it.

The repo is no longer only a viewer widget project. It now contains a modular mobile PDF editor and workflow application with editing, OCR, AI, collaboration, connectors, enterprise policy, diagnostics, migration, and release-engineering support.

## Modules

- `:viewer-engine`
  - Legacy Java rendering engine based on AndroidPdfViewer
  - Java interop preserved
  - Overlay hooks, coordinate mapping, and viewer bridge components
- `:editor-core`
  - Document sessions, write engine, OCR, search, Room persistence, WorkManager jobs, migrations, diagnostics, security, collaboration, connectors, workflows, and enterprise services
- `:ai-assistant`
  - Real AI provider runtime, secure provider configuration, grounded citations, multi-document workspace, and enterprise policy-aware orchestration
- `:app`
  - Compose application shell, editor workspaces, admin/settings UI, diagnostics UI, connector UI, AI UI, collaboration UI, and release configuration

## Product Areas

### Editing and write pipeline
- Session-based editing through `EditorSession`
- Command-based undo and redo
- Direct PDF mutation as the primary persistence model
- Legacy `.pageedits.json` migration support for backward compatibility
- Save, save-as, export copy, rollback, checksum verification, transaction logging, and file locking
- Text edits, image edits, annotations, page reorder, page insert/delete/duplicate/extract/rotate, blank-page creation, and structural page persistence

### Annotation and review
- Highlight, underline, strikeout, freehand ink, rectangle, ellipse, arrow, line, sticky note, and text box annotations
- Selection, recolor, resize, move, duplicate, and delete operations
- Review threads, replies, mentions, resolve/reopen, activity feed, share links, version-linked review state, and offline sync queue support

### Organize pages
- Page thumbnail generation and caching
- Drag/reorder support
- Rotate, delete, duplicate, extract, insert blank page, insert image page, merge, split, and batch operations

### Forms and signatures
- AcroForm field detection and modeling
- Text, multiline, checkbox, radio, dropdown, date, and signature field support
- Form validation and navigation
- Handwritten signature appearance capture
- Certificate-backed digital signature support
- Signature verification state, invalidation after edits, request-sign metadata, signer ordering, reminder metadata, expiration metadata, and reusable form templates

### Security and protected output
- Password protection and permission flags written into saved output
- Watermarking and policy-driven export watermark enforcement
- Metadata scrub support
- Irreversible redaction apply pipeline
- Inspection reports for metadata, protection flags, hidden content flags, redactions, and signatures
- Audit trail events and policy-enforced export/share restrictions

### OCR, search, and scan import
- ML Kit-based OCR runtime
- Searchable PDF generation from imported scans and images
- OCR settings, job lifecycle, resumable persistence, diagnostics, and progress surfaces
- OCR text integrated into search, copy text, and AI grounding
- Search, bookmarks/outline, recent searches, and text extraction

### AI assistant
- Ask PDF, summarize document, summarize page, explain selection, extract action items, suggest next actions, and semantic search
- Grounded citations to page and page-region coordinates
- Multi-document AI workspace with pinned files, saved document sets, conversation history, and workspace summaries
- Real provider runtime for:
  - local Ollama-compatible endpoints
  - remote Ollama-compatible endpoints
  - OpenAI API
  - generic OpenAI-compatible APIs
- Secure provider credential storage using Android Keystore-backed encryption
- Provider discovery, model enumeration, health checks, capability metadata, streaming, cancellation, retry, timeout, and enterprise policy gating

### Collaboration and workflow automation
- Remote collaboration adapter with offline queue, optimistic replay, conflict handling, and WorkManager sync
- Compare workflow with page-level change markers and reviewable change summaries
- Share links, comments, replies, activity events, and review snapshots
- Request-sign workflow, form request workflow, lifecycle tracking, reminder events, and activity integration

### Enterprise platform hooks
- Personal mode and enterprise mode session architecture
- Tenant bootstrap, cached policy sync, entitlements, telemetry queueing, and diagnostics bundles
- Managed app configuration support for tenant bootstrap, AI restrictions, connector restrictions, telemetry policy, default provider endpoints, watermarking, metadata scrub, and external-sharing controls

### Connectors and storage
- Local file routing
- Android document provider support
- WebDAV connector support
- S3-compatible connector support
- Capability model for future enterprise connectors
- Conflict-aware remote metadata handling with etag/checksum, modified time, and version id
- Secure temp/cache lifecycle and DLP-aware destination filtering

### Diagnostics, recovery, and upgrade safety
- Runtime diagnostics snapshot with provider health, sync backlog, OCR queue, connector state, recent failures, and migration reports
- Startup repair for interrupted saves, interrupted sync, interrupted OCR, corrupted drafts, and stale local artifacts
- Versioned migration framework for Room, drafts, AI settings, connector/session state, OCR/search data, and older mutation/session formats
- Benchmark, smoke, regression, and migration-oriented test coverage

## Build Variants

The app module exposes these product flavors:

- `dev`
- `qa`
- `prod`
- `enterpriseDemo`

Recommended local commands:

```powershell
.\gradlew.bat clean
.\gradlew.bat :editor-core:test
.\gradlew.bat :ai-assistant:test
.\gradlew.bat :app:assembleDevDebug
.\gradlew.bat :app:assembleProdDebug
```

## Release Gates and CI

The repo now includes repo-wide release gates that block merge when production source sets contain prohibited placeholders or fake-style implementations.

Protected patterns include:
- `Fake*`
- `NoOp*`
- `InMemory*`
- `Placeholder*`
- `TODO`
- `example.invalid`
- sidecar-primary save-path references in production code

CI workflows live in `.github/workflows/` and now cover:
- grep-based release gates
- `editor-core` lint and unit tests
- `ai-assistant` lint and unit tests
- app prod lint/build/unit gates
- targeted migration and upgrade-safety tests
- targeted OCR, collaboration, compare/export/protection, connector, forms/signature, and workflow regression tests
- instrumentation and benchmark workflows for core enterprise flows
- SBOM and license reporting
- signed prod artifact generation hooks

Useful local commands:

```powershell
.\gradlew.bat :app:validateReleaseReadiness
.\gradlew.bat :editor-core:lint :editor-core:test
.\gradlew.bat :ai-assistant:lint :ai-assistant:test
.\gradlew.bat :app:lintProdDebug :app:assembleProdDebug
.\gradlew.bat :app:testProdDebugUnitTest
powershell -ExecutionPolicy Bypass -File .\scripts\run-smoke-tests.ps1
```

## Managed Configuration

Managed restrictions support enterprise deployment scenarios for:
- tenant bootstrap and issuer/base URLs
- AI provider defaults and restrictions
- connector restrictions and allowed destinations
- telemetry policy
- forced watermarking
- forced metadata scrub
- external sharing controls

See:
- `docs/deployment/managed-config.md`
- `docs/deployment/key-rotation.md`
- `docs/deployment/certificate-pinning.md`
- `docs/security/security-review-checklist.md`
- `docs/release/release-checklist.md`
- `docs/release/smoke-tests.md`
- `docs/privacy/data-safety.md`

## Upgrade and Migration Safety

The app includes a versioned migration and repair pipeline. On startup it can:
- back up upgrade-relevant state
- migrate legacy page-edit sessions forward
- preserve and upgrade older drafts and OCR/search/session state
- resume interrupted OCR and sync work
- quarantine corrupted drafts or outdated local artifacts
- generate supportable migration reports for diagnostics export

## Legacy Viewer Usage

The original viewer engine is still available for lower-level rendering use cases.

### XML usage

```xml
<com.github.barteksc.pdfviewer.PDFView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Java usage

```java
pdfView.fromUri(uri)
    .defaultPage(0)
    .enableSwipe(true)
    .swipeHorizontal(false)
    .enableDoubletap(true)
    .enableAnnotationRendering(false)
    .spacing(0)
    .autoSpacing(false)
    .pageSnap(false)
    .pageFling(false)
    .nightMode(false)
    .load();
```

Other sources remain supported:

```java
pdfView.fromFile(file)
pdfView.fromBytes(bytes)
pdfView.fromStream(inputStream)
pdfView.fromAsset("sample.pdf")
pdfView.fromSource(documentSource)
```

### Lightweight legacy edit/export surface

The compatibility-oriented lightweight edit API on `PDFView` is still present:

```java
PDFView pdfView = findViewById(R.id.pdfView);
pdfView.fromUri(uri)
    .defaultPage(0)
    .enableAnnotationRendering(true)
    .load();

pdfView.addEditElement(new PdfTextEdit(
    0,
    new RectF(0.08f, 0.10f, 0.70f, 0.16f),
    "Edited with AndroidPdfViewer",
    Color.RED,
    18f
));

pdfView.addEditElement(new PdfSignatureEdit(
    0,
    new RectF(0.58f, 0.78f, 0.92f, 0.88f),
    signatureBitmap,
    true,
    Color.BLUE
));

File output = new File(getExternalFilesDir(null), "edited.pdf");
pdfView.exportEditedPdf(output);
```

Normalized coordinates still map `RectF(0f, 0f, 1f, 1f)` to the full page.

## Notes

- Historical AndroidPdfViewer 1.x remains separate at [AndroidPdfViewerV1](https://github.com/barteksc/AndroidPdfViewerV1)
- This fork still uses Pdfium-based rendering in the viewer layer
- 16 KB page-size support and Play compatibility updates are included

## License

Created with the help of android-pdfview by [Joan Zapata](http://joanzapata.com/)

```text
Copyright 2017 Bartosz Schiller

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
