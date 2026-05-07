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
    │   ├── MdxEngine.kt      # Dict asset copy → internal storage → trie build → lookup
    │   ├── MdxParser.kt      # Pure-Kotlin MDX binary parser (v1/v2)
    │   ├── TrieIndex.kt      # Case-insensitive prefix tree (~500K entries)
    │   ├── AppDatabase.kt    # Room DB (search_history table)
    │   └── SearchHistoryDao.kt
    └── repository/           # Implementations (DictionaryRepositoryImpl, HistoryRepositoryImpl)
```

## Navigation Flow

`MainScreen → SearchScreen → EntryScreen`
- MainScreen search bar is a `Surface` (not `TextField`) — tap navigates, no keyboard
- SearchScreen auto-focuses the search field; results trigger navigation to EntryScreen via `UiState.shouldNavigateToEntry`
- EntryScreen: 3 dictionary tabs (牛津/柯林斯/韦氏大学) with `HorizontalPager` swipe + per-tab WebView
- All 3 screens share the same `SearchViewModel` (scoped to `MainActivity`)

## Key Technical Details

### MDX Engine Initialization
- On cold start, `MdxEngine.initialize()` copies dictionary files from `assets/dictionaries/` to `filesDir/dict_indices/`, then parses MDX headers + keyword indices into a `TrieIndex`
- Assets are 177MB+ across 3 dictionaries; `.mdx`/`.mdd`/`.css`/`.js` extensions are set as `noCompress` in `build.gradle.kts` for direct mmap/random-access
- The parser (`MdxParser.kt`) handles both MDX v1 and v2 format auto-detection via heuristic (`key_id > num_entries * 2` means v1)

### WebView Pool
- `WebViewPool` pre-warms 2 `WebView` instances in `Application.onCreate()` on the main thread
- Acquire from pool in `EntryScreen.DictPage`, release on `DisposableEffect` disposal
- Only the current visible tab loads HTML (`LaunchedEffect(isCurrentPage)`); adjacent pages hold an empty WebView
- HTML loads with `baseUrl = file://{dictDir}/` so relative CSS/JS/font references resolve correctly from internal storage

### Playfair Display Font
- Variable font file at `res/font/playfair_display_regular.ttf` (downloaded from Google Fonts GitHub)
- Referenced via `WulongFonts.PlayfairDisplay` in `Theme.kt`

## Known Issues / Gotchas

### MdxEngine mdxFile overwrite bug
In `MdxEngine.initialize()`, the `copyAssetsRecursively` local function captures outer `mdxFile`/`mddFile` variables. For the Oxford dictionary, the `中文例句释义反查/` subdirectory contains `oaldZhEn.mdx` which can overwrite the correct `mdxFile` reference depending on `AssetManager.list()` ordering. This causes the trie to be built from the wrong file, making all searches return empty. **Fix**: Only assign `mdxFile`/`mddFile` for top-level directory files, not subdirectory files.

### Error message opacity
`SearchViewModel` init block catches `Exception` and shows `e.message ?: "初始化失败"`. Many exceptions have null messages (NPE, some IOExceptions). Always add `Log.e` with full stacktrace alongside the UI error, and include exception class name in the display string.
