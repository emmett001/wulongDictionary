# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This project has no `gradlew` wrapper script. Build from Android Studio:

- **Sync Gradle** after any dependency changes
- **Build**: Android Studio → Build → Make Project (or Run button)
- **APK output**: `app/build/outputs/apk/debug/app-debug.apk`
- **Install via ADB**: `adb install -r -t app/build/intermediates/apk/debug/app-debug.apk` (debug APKs are `testOnly`, need `-t` flag)

ADB is at: `C:/Users/Lenovo-pc/AppData/Local/Android/Sdk/platform-tools/adb`

## Project Architecture

Clean Architecture with manual DI (no Hilt/Dagger):

```
app/src/main/java/com/wulong/dict/
├── MainActivity.kt           # NavHost + WindowCompat edge-to-edge
├── WulongDictApp.kt          # Application: creates AppContainer, pre-warms WebViews
├── AppContainer.kt           # Manual DI container
├── navigation/NavRoutes.kt   # Route constants: MAIN, SEARCH, ENTRY
├── ui/
│   ├── screens/
│   │   ├── MainScreen.kt     # Home: search bar (decorative), logo, slogan
│   │   ├── SearchScreen.kt   # Search input + history LazyColumn
│   │   ├── SearchViewModel.kt # UI state, debounced suggestions, async search
│   │   └── EntryScreen.kt    # TabRow + HorizontalPager + WebView per dictionary
│   ├── theme/Theme.kt        # Material3 color schemes + PlayfairDisplay font family
│   └── pool/WebViewPool.kt   # Pre-warmed WebView object pool
├── domain/
│   ├── model/                # DictionaryEntry, SearchHistory, Suggestion
│   ├── repository/           # DictionaryRepository, HistoryRepository interfaces
│   └── usecase/              # 6 use cases (search, suggest, history CRUD)
└── data/
    ├── local/
    │   ├── SqliteDictEngine.kt  # Read-only SQLite3 dictionary engine (search + suggest)
    │   ├── AppDatabase.kt       # Room DB (search_history table)
    │   └── SearchHistoryDao.kt
    └── repository/              # Implementations (DictionaryRepositoryImpl, HistoryRepositoryImpl)
```

## Navigation Flow

`MainScreen → SearchScreen → EntryScreen`
- MainScreen search bar is a `Surface` (not `TextField`) — tap navigates, no keyboard
- SearchScreen auto-focuses the search field; results trigger navigation to EntryScreen via `UiState.shouldNavigateToEntry`
- EntryScreen: 3 dictionary tabs (牛津/柯林斯/韦氏大学) with `HorizontalPager` swipe + per-tab WebView
- All 3 screens share the same `SearchViewModel` (scoped to `MainActivity`)

## Key Technical Details

### Dictionary Engine (SQLite3)
- Dictionaries are pre-built `.sqlite3` files stored in `filesDir/dictionaries/` on the device
- Each SQLite3 file has two tables: `search` (lower_key, original_key) and `entries` (key, content BLOB)
- `SqliteDictEngine.open()` opens all 3 databases in read-only mode
- Word lookup: `SELECT original_key FROM search WHERE lower_key = ?` → `SELECT content FROM entries WHERE key = ?`
- Prefix suggestions: `SELECT original_key FROM search WHERE lower_key LIKE ? LIMIT ?`
- To deploy dictionaries to device: `adb push Dictionary/* /data/data/com.wulong.dict/files/dictionaries/`

### WebView Pool
- `WebViewPool` pre-warms 2 `WebView` instances in `Application.onCreate()` on the main thread
- Acquire from pool in `EntryScreen.DictPage`, release on `DisposableEffect` disposal
- Only the current visible tab loads HTML (`LaunchedEffect(isCurrentPage)`); adjacent pages hold an empty WebView
- HTML loads with `baseUrl = file://{dictDir}/` so relative CSS/JS/font references resolve correctly from internal storage

### Playfair Display Font
- Variable font file at `res/font/playfair_display_regular.ttf` (downloaded from Google Fonts GitHub)
- Referenced via `WulongFonts.PlayfairDisplay` in `Theme.kt`

## Known Issues / Gotchas

### Error message opacity
`SearchViewModel` init block catches `Exception` and shows `e.message ?: "初始化失败"`. Many exceptions have null messages (NPE, some IOExceptions). Always add `Log.e` with full stacktrace alongside the UI error, and include exception class name in the display string.

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Uses the default five-label vocabulary (needs-triage / needs-info / ready-for-agent / ready-for-human / wontfix). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
