# Dreamland Reception — agent / contributor guide

This repo is a **Kotlin Multiplatform** Compose Desktop app (`kotlin { jvm() }` in `composeApp/build.gradle.kts`). Platform-specific code for the desktop app lives under `composeApp/src/jvmMain/`. **`commonMain`** remains the place for shared code if you add more targets later; do not fold everything into `jvmMain` unless it is JVM-only (Firebase Admin, file paths, etc.).

## Reference project (read-only)

The folder `jewellery/Gagan-Jewellers-Desktop/KotlinProject/` is a **reference only** (Gagan Jewellers desktop). It uses `jvm("desktop")` and Material (M2); Dreamland uses **`jvm()`** and **Material 3**. When mirroring patterns, adapt imports and APIs—do not change files under `jewellery/`.

| Concern | Gagan Jewellers (reference) | Dreamland Reception |
|--------|-----------------------------|---------------------|
| Root shell + sidebar | `navigation/JewelryApp.kt` | `navigation/DreamlandApp.kt` |
| Firebase bootstrap | `JewelryAppInitializer.kt` | `DreamlandAppInitializer.kt` → `data/firebase/FirebaseManager.kt` |
| Repositories | `object Firestore*Repository` + `initialize(firestore)` | `object Firestore*Repository` in `data/repository/*.kt` + same init from initializer |
| ViewModels | From initializer (`getViewModel()`, etc.) | `DreamlandAppInitializer.getDashboardViewModel()`, … |
| Global loading | `viewModel.loading` overlay in `JewelryApp` | `DreamlandLoadCoordinator` + overlay in `DreamlandApp` |
| Splash | `ui/SplashScreen.kt` → `SplashScreenDesktop` | `ui/splash/SplashScreen.kt` → `SplashScreenDesktop` |
| MVVM | `viewModels/`, `data/` | `ui/viewmodel/`, `data/repository/`, `data/model/` |

## Architecture (MVVM + app shell)

- **UI**: `*Screen.kt` at package root — composables.
- **ViewModels**: `ui/viewmodel/` — `ViewModel` + `StateFlow`; heavy work wrapped with `launchWithGlobalLoading` (`ui/viewmodel/ViewModelLoading.kt`) so the **global dimmed overlay** reflects any in-flight load.
- **Repositories**: `FirestoreBookingRepository`, `FirestoreRoomRepository`, … — Kotlin **`object`**s implementing the `*Repository` interface, with **`fun initialize(fs: Firestore)`** called once from `DreamlandAppInitializer.initialize()` when Firebase is connected.
- **Loading**: `data/loading/DreamlandLoadCoordinator.kt` — reference-counted `begin()` / `end()`; `DreamlandApp` collects `loading` and shows `DreamlandGlobalLoadingOverlay` (same idea as jewellery’s loading card over content).
- **Single VM instances**: `DreamlandAppInitializer` creates one instance per feature ViewModel (lazy, thread-safe). Screens default to `DreamlandAppInitializer.get…ViewModel()` so navigation shares state and Firestore access paths. Top-bar **Refresh** calls `DreamlandAppInitializer.refreshAllViewModels()`.
- **Splash**: `App()` shows `SplashScreenDesktop` (~3s, same animation pattern as jewellery), then `DreamlandShell()` → `DreamlandApp`.
- **Window**: `main.kt` uses `rememberWindowState(1280.dp, 800.dp)` like the reference `Main.kt`.

Navigation is **state-based**: `navigation/DreamlandApp.kt` — `MainTab` + `when { … }`.

## Firebase service account JSON — where to put it

The app uses the **Firebase Admin SDK** on the JVM (not `google-services.json`, which is for Android clients).

**Recommended locations** (first existing file wins; see `FirebaseManager` for full order):

1. **`composeApp/firebase/service-account.json`**
2. **`composeApp/firebase/firebase-credentials.json`**
3. **Project root** `firebase-credentials.json`
4. **`~/.dreamland/service-account.json`**
5. **Environment**: `DREAMLAND_FIREBASE_CREDENTIALS` or `GOOGLE_APPLICATION_CREDENTIALS`

Download: Firebase Console → **Project settings** → **Service accounts** → **Generate new private key**.

The JSON’s `project_id` is read automatically when loading from a file. For application-default credentials only, set **`GOOGLE_CLOUD_PROJECT`** if needed.

## Entry points

- `main.kt` — `DreamlandAppInitializer.initialize()` → `Window` → `App()` → `DreamlandTheme` → splash then `DreamlandShell()` / `DreamlandApp`.

## Gagan Jewellers billing, invoices, and PDF generation (reference for Dreamland)

Dreamland’s **Billing** tab today is a **simple Firestore-backed invoice list** (`BillingScreen` + `FirestoreBillingRepository` + `BillingViewModel`). The jewellery app implements a **full retail billing pipeline** (customer → cart → payment → receipt) and **multi-stack PDF output**. Use the paths below when you extend Dreamland billing or add hotel folios / PDF exports.

### UI flow (jewellery)

- **`composeApp/src/desktopMain/kotlin/org/example/project/ui/BillingScreen.kt`** — Step wizard: `BillingStep` = CUSTOMER → CART → PAYMENT → RECEIPT. Uses `CustomerViewModel`, `CartViewModel`, `ProductsViewModel`, `PaymentViewModel`, `ImageLoader` from `JewelryAppInitializer`.
- Related: **`PaymentScreen.kt`**, **`InvoiceScreen.kt`**, **`CustomerSelectionStep.kt`**, **`CartBuildingStep.kt`**, cart/receipt UI under the same `ui/` package.

### Domain + calculation

- **`invoice/calculation/InvoiceCalculator.kt`** — Builds a finalized `Invoice` from `Order`, customer, store info, products (and optional cart line items). Handles tax / totals / metadata used for PDF.
- **`invoice/model/Invoice.kt`**, **`InvoiceDraft.kt`** — Immutable vs editable draft shapes.
- **`viewModels/InvoiceViewModel.kt`** — Draft vs final invoice state, PDF path, loading flags; uses `InvoiceCalculator`, `InvoiceRenderer`, `JewelryAppInitializer` for Firestore/services.

### PDF rendering (multiple backends in one project)

Jewellery **`composeApp/build.gradle.kts` (desktop)** pulls in:

| Dependency | Role |
|------------|------|
| `org.apache.pdfbox:pdfbox:2.0.29` | Low-level PDF / rendering |
| `com.openhtmltopdf:openhtmltopdf-core` + `openhtmltopdf-pdfbox` | HTML → PDF |
| `org.xhtmlrenderer:flying-saucer-pdf` + `flying-saucer-core` | HTML/CSS → PDF (legacy path) |
| `com.itextpdf:itext7-core:7.2.5` | Alternative PDF API |
| `com.github.librepdf:openpdf:1.3.30` | **OpenPDF** — used for “ERP-style” programmatic invoice layout in `PdfGeneratorService` |
| `org.slf4j:slf4j-simple` | Logging for PDF pipelines |

### PDF pipeline entry points (jewellery)

- **`utils/PdfGeneratorService.kt`** — Main path: `generateInvoicePDF(order, customer, outputFile, …)` → loads store info / products → **`InvoiceCalculator`** → renders via **OpenPDF** (see inline comments: “Render PDF using OpenPDF”). Good **template** for Dreamland: swap `Order` / line items for **hotel folio** (`BillingInvoice`, stays, room charges).
- **`invoice/pdf/InvoiceRenderer.kt`** — Renders structured `Invoice` via section composables.
- **`invoice/pdf/sections/`** — `HeaderSection`, `PartySection`, `ItemsTableSection`, `TotalsSection`, `TaxSummarySection`, `PaymentSection`, `BankSection`, `ExchangeGoldSection`, `DividerSection` — each implements **`InvoiceSection`**; see **`PdfContext.kt`**, **`InvoiceSection.kt`**.

### How to reuse this in Dreamland (suggested order)

1. Add **`jvmMain` Gradle dependencies** you need (start with **OpenPDF** + optionally **PDFBox**; add OpenHTML/iText only if you mirror HTML templates).
2. Copy **adapt** (do not symlink) the **section-based** `InvoiceRenderer` pattern under something like `com.example.dreamland_reception.billing.pdf`, replacing jewellery-specific fields (gold, exchange, barcodes) with **room nights, taxes, guest name, folio number**.
3. Map **`BillingInvoice`** (or a new `Folio` model) through a small **calculator** class analogous to `InvoiceCalculator` so totals are single-sourced before PDF.
4. Write PDFs to a user-chosen path or `Files.createTempFile` on desktop; optionally upload to **Cloud Storage** later (jewellery uses `google-cloud-storage` + `StorageService` — see initializer in reference).

### Firestore / payments (jewellery)

- **`PaymentRepository`**, **`FirestorePaymentRepository`**, **`OrderRepository`**, **`FirestoreOrderRepository`** — persist orders and payment splits.
- **`PaymentViewModel`** — coordinates billing screen payment step.

These are **not** copied into Dreamland; treat them as **patterns** when you add payment recording next to `FirestoreBillingRepository`.

## Build

From repo root: `./gradlew :composeApp:run` (or use Android Studio / IntelliJ run configuration for the desktop target).
