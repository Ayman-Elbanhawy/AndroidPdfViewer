# AndroidPdfViewer Enterprise Fork

This repository started as the AndroidPdfViewer project and now contains two connected product layers:

- `viewer-engine` / `android-pdf-viewer`: the legacy Java PDF rendering engine, preserved as a reusable module
- `editor-core`, `ai-assistant`, and `app`: a Kotlin, AndroidX, Material 3, and Compose enterprise PDF platform built around that renderer

The viewer is still available as a low-level widget, but this fork is now a modular PDF editing and workflow application with enterprise-grade platform hooks.

## Repository Structure

- `:viewer-engine`
  - Existing AndroidPdfViewer rendering engine
  - Java interop preserved
  - Page coordinate mapping and overlay integration hooks
- `:editor-core`
  - Document sessions, persistence, Room, WorkManager, migration engine, diagnostics, OCR, security, collaboration, workflows, connectors, and write engine
- `:ai-assistant`
  - Provider runtime, secure provider settings, grounded citations, multi-document AI workspace, and policy-aware AI orchestration
- `:app`
  - Compose application shell, editor workspaces, diagnostics UI, settings/admin UI, connector UI, collaboration UI, and release configuration

## What This Fork Now Includes

### Core editing and document workflows
- Session-based document editing with `EditorSession`
- Undo/redo and command-based mutations
- Autosave, manual save, save-as, export copy, crash-safe restore
- Direct PDF mutation write engine with rollback, checksums, save strategies, and sidecar migration compatibility
- Text objects, image objects, annotation overlays, page organization, and structural page edits

### Annotation and review
- Highlight, underline, strikeout, ink, rectangle, ellipse, arrow, line, sticky note, and text box annotations
- Selection, move, resize, recolor, duplicate, delete
- Review comments, threads, replies, resolve/reopen, mentions, activity feed, share links, and review artifacts
- Background sync queue and remote collaboration runtime with offline replay and conflict handling

### Organize pages
- Page thumbnails and caching
- Drag/reorder support foundations
- Rotate, delete, duplicate, extract, insert blank page, insert image page, merge, split, and batch operations

### Forms and signatures
- AcroForm discovery and field modeling
- Text, multiline, checkbox, radio, dropdown, date, and signature fields
- Validation engine and form navigation
- Signature capture, saved signatures, visible signature placement, certificate-backed signing foundation, and verification state tracking

### Security and protection
- App lock, secure local encryption helpers, document protection settings
- Saved-output password protection and permission flags
- Watermark support on export
- Metadata scrubber
- Redaction mark, preview, and irreversible apply pipeline
- Inspection reports and audit trail events
- Tenant policy enforcement for export/share/print/copy restrictions

### OCR, search, and scan ingestion
- ML Kit-based OCR pipeline wiring and searchable-session support
- OCR settings, OCR queue state, diagnostics, and resumable job handling
- Document search, bookmarks/outline, recent searches, text extraction, selection copy/share
- Scan import workflow and searchable-PDF pipeline foundations

### AI assistant
- Ask PDF, summarize document, summarize page, explain selection, extract action items, semantic search
- Page-grounded citations and region references
- Redaction suggestions and form autofill suggestions
- Multi-document AI workspace with pinned files, saved document sets, conversation history, and grounded cross-document answers
- Real provider runtime for:
  - local Ollama-compatible endpoints
  - remote Ollama-compatible endpoints
  - OpenAI API
  - generic OpenAI-compatible APIs
- Secure credential storage, provider settings UI, capability discovery, streaming, cancellation, retries, and policy gating

### Enterprise platform and deployment hooks
- Personal mode and enterprise mode authentication/session architecture
- Tenant bootstrap, policy sync, entitlement refresh, telemetry queueing, diagnostics bundles
- Connector abstraction with production routing for local files, document-provider style flows, WebDAV, and S3-compatible storage
- DLP-aware export and destination filtering
- Managed app restriction support for tenant bootstrap, AI restrictions, connector restrictions, telemetry policy, watermarking, and metadata scrub enforcement
- Release readiness validation, SBOM generation, license reporting, smoke test runner, and deployment docs

### Quality, diagnostics, and upgrade safety
- Runtime diagnostics snapshot with migration summary, cache state, queue depth, provider health, connector state, and recent failures
- Startup repair for interrupted saves, interrupted OCR, interrupted sync, corrupt drafts, and corrupt session sidecars
- Versioned migration engine with compatibility mode for older local sessions
- Recovery, benchmark, smoke, and regression coverage
- Accessibility pass on major Compose surfaces with improved semantics and touch targets

## Build Variants

The app module includes these product flavors:

- `dev`
- `qa`
- `prod`
- `enterpriseDemo`

Recommended local build targets:

```powershell
.\gradlew.bat :editor-core:test
.\gradlew.bat :ai-assistant:test
.\gradlew.bat :app:compileDevDebugKotlin
.\gradlew.bat :app:assembleDevDebug
```

## Release and Compliance Gates

Production readiness checks are enforced with Gradle and CI.

Useful commands:

```powershell
.\gradlew.bat :app:validateReleaseReadiness
.\gradlew.bat :app:generateSbom :app:generateLicenseReport
powershell -ExecutionPolicy Bypass -File .\scripts\run-smoke-tests.ps1
```

`validateReleaseReadiness` fails if production code still contains:

- placeholder endpoints such as `example.invalid`
- unresolved license entries
- fake, no-op, in-memory, example, or placeholder production implementations
- placeholder or debug certificate pin configuration

## Upgrade and Migration Safety

The current implementation includes a versioned upgrade and repair path for existing users.

On startup the app can:

- back up local migration-relevant state
- upgrade legacy `.pageedits.json` sessions into the newer mutation model
- preserve compatibility for older drafts and sessions while upgrading them forward
- resume interrupted OCR and sync work
- quarantine corrupted drafts and sidecar session files
- record migration reports for diagnostics and support export

## Managed Enterprise Configuration

Managed restrictions now support:

- tenant bootstrap and issuer configuration
- AI provider defaults and restrictions
- connector restrictions and destination filtering
- telemetry policy
- watermark enforcement
- metadata scrub enforcement
- external sharing controls

See [docs/deployment/managed-config.md](docs/deployment/managed-config.md).

## Legacy Viewer Usage

The original AndroidPdfViewer engine is still available for apps that only need rendering.

### Include `PDFView` in a layout

```xml
<com.github.barteksc.pdfviewer.PDFView
    android:id="@+id/pdfView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### Load a PDF

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

Other supported sources:

```java
pdfView.fromFile(file)
pdfView.fromBytes(bytes)
pdfView.fromStream(inputStream)
pdfView.fromAsset("sample.pdf")
pdfView.fromSource(documentSource)
```

### Legacy viewer notes
- Link handling is supported through `LinkHandler`
- Scroll handle integration is still supported
- Fit policies remain available: `WIDTH`, `HEIGHT`, `BOTH`
- Rendering quality can still be adjusted with `useBestQuality(true)`

## Lightweight PDF Edit API on `PDFView`

This fork also keeps the direct lightweight edit/export API that was added to the legacy viewer surface for compatibility-oriented usage.

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

Coordinates are normalized per page, so `RectF(0f, 0f, 1f, 1f)` maps to the whole page.

## Current Library Notes

- The historical AndroidPdfViewer 1.x branch remains separate at [AndroidPdfViewerV1](https://github.com/barteksc/AndroidPdfViewerV1)
- This repository still builds on Pdfium-based rendering for the viewer engine
- 16 KB page-size support and Play compatibility updates are included in this fork

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
