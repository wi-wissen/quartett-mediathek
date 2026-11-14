# Quartett Mediathek – Specification

Android TV app that combines the media libraries of **ARD, ZDF, 3sat and arte**. It opens with *your* shows and
their new episodes (like YouTube subscriptions), not only with recommendations. No own server, no account:
subscriptions, watchlist and progress live on the device.

- Visual reference: `docs/mockup.html` (open in a browser; arrow keys, Enter, Esc; buttons below the TV frame jump
  to each screen). All shows in the mockup are invented sample data.
- Code, comments, identifiers, file names: **English**. UI text: **German if the device language is German,
  otherwise English** (`values/` = English default, `values-de/` = German).
- Package / applicationId: `net.wi7.quartettmediathek` (namespace of the app module: `net.wi7.quartettmediathek.tv`).
  minSdk 26, compile/target SDK 36.

## 1. Architecture

```
shared/   Kotlin Multiplatform (androidTarget + jvm). All data logic, testable on the JVM.
  model/          Broadcaster, Show, Episode, Teaser, StreamInfo, ...
  source/         Source contracts (RecommendationSource, ShowSource, StreamResolver)
    mvw/          MediathekViewWeb client: query builder, selector syntax, models
    ard/          ARD page-gateway client
    zdf/          ZDF GraphQL client (token bootstrap from zdf.de HTML)
    arte/         arte EMAC + player client
  feed/           Title parsing, deduplication, variant merging, subscription filtering
  catalog/        Discover: mapping broadcaster rows to unified categories
  subscription/   Subscription model and MVW expression builder
  cache/          Cache policy on top of the http_cache table
  library/        SQLDelight-backed subscriptions, bookmarks, progress, settings
  repository/     Repositories.kt = THE contract between data and UI (do not change signatures casually)
  player/         SeekAccelerator (pure seek logic)
  platform/       expect/actual: HTTP client, SQL driver
app/      Android TV app: Compose for TV (androidx.tv.material3), Media3 ExoPlayer, WorkManager.
  di/             AppContainer (manual DI)
  ui/theme        Colours and type from the mockup
  ui/components   Rail, tiles, buttons, rows
  ui/screens      Home, Discover, Search, Subscriptions, Watchlist, Settings, Show, Episode
  navigation/     NavHost and routes
  player/         PlayerScreen
```

Later targets (not now): Apple TV (SwiftUI UI over the shared module), Linux (Compose Desktop). Keep `shared`
free of Android types so that stays possible. Libraries chosen for that: Ktor, kotlinx.serialization, SQLDelight.

## 2. Data sources

Which source is used for what:

| Purpose | ARD | ZDF | 3sat | arte |
| --- | --- | --- | --- | --- |
| Start page rows (Discover) | ARD gateway | ZDF GraphQL | MVW: newest shows of channel 3sat | arte EMAC |
| Show page, episode list | ARD gateway | ZDF GraphQL | MVW (topic) | MVW (topic/title) + arte player metadata |
| Stream | ARD item API (MP4 up to 1080p, HLS, WebVTT) | ZDF ptmd (up to 1080p, AD as audio track, WebVTT) | MVW `url_video_hd` / `url_video` (MP4) | arte player API (HLS) |
| Subscription feed, search | MVW | MVW | MVW | MVW |

Fallback: if a broadcaster API fails, show pages and streams fall back to MediathekViewWeb.
All requests are plain HTTPS without login. Send a neutral `User-Agent` (e.g. `QuartettMediathek/<version>`),
never personal data.

### 2.1 MediathekViewWeb (MVW)

`POST https://mediathekviewweb.de/api/query`, header `Content-Type: text/plain`, body:

```json
{"queries":[{"fields":["topic"],"query":"die anstalt"},{"fields":["channel"],"query":"zdf"}],
 "sortBy":"timestamp","sortOrder":"desc","future":false,"offset":0,"size":50,
 "duration_min":1200}
```

- `fields`: `channel`, `topic`, `title`, `description` (several fields in one query = OR across fields).
- Several entries in `queries` = AND. The same field in several entries = AND as well, so OR over channels
  needs one request per channel or one query with all channel names in `query` (words are OR inside one query –
  verify in tests).
- `duration_min` / `duration_max` in seconds. Verified: "die anstalt" with `duration_min: 1200` → 20 instead of 28.
- Response: `result.results[]` with `channel, topic, title, description, timestamp (unix s), duration (s),
  size (bytes), url_website, url_subtitle, url_video, url_video_low, url_video_hd, id`;
  `result.queryInfo.totalResults`.
- Matching is by words, case-insensitive, umlaut-tolerant. Topic "nano" returns 4,496 hits across topics
  `NANO`, `nano`, `NANO Doku`, `NANO Talk` and channels 3sat, ZDF, **SRF** (SRF is out of scope).
  Therefore always re-filter locally (exact topic, allowed channels, min duration).
- The website's selector syntax (used to display a subscription as text, and to parse the user's search):
  `!` channel, `#` topic, `+` title, `*` description, `>`/`<` duration in minutes, no selector = topic+title.
  Different selectors AND, the same selector repeated OR, commas inside one value AND
  (`#olympia,tokio`). Example: `!ard !ndr #sturm,der,liebe >20`.
- The site offers every search as an RSS feed, so periodic automated queries are expected. We use the JSON API.
- ARD episodes: the last path segment of `url_website` (`https://www.ardmediathek.de/video/{id}`) is the ARD
  item id. In a test all 9 MVW hits for "Die Friesenjunkies" matched ARD episode ids.
- Raw ARD show names must be cleaned before querying: "Die Friesenjunkies | Impro-Dramedy-Serie" returns
  **nothing**, "Die Friesenjunkies" returns 9. Remove suffixes after `|` or `·` and season markers.

### 2.2 ARD

Base `https://api.ardmediathek.de/page-gateway`. Verified 14.11.2026.

1. Start page: `GET /pages/ard/home?embedded=false` → `widgets[]` with `title`, `compilationType`,
   `links.self.href`; `teasers` is empty here. Skip `compilationType` `continuousPlay`, `persoRecos` and the title
   "Menü". 36 usable rows (e.g. "Unsere Top-Serien", "Film-Empfehlungen", "Spannende Dokus und Reportagen").
2. Row: `GET /widgets/ard/editorials/{widgetId}?pageNumber=0&pageSize=100` (= `links.self.href` without `userId`
   and `embedded`; the id contains a colon, encode as `%3A`) → `teasers[]`.
3. Teaser: `type` (`show` or `ondemand`; ignore `editorialPage`), `mediumTitle`, `longTitle`, `coreAssetType`,
   `publicationService.name`, `links.target.href`, `images.aspect16x9.src` (contains `{width}`, use 480 or 960).
4. Show: `GET /pages/ard/grouping/{id}?embedded=true` (URL from the show teaser) → `widgets[0].teasers[]` are the
   episodes (`type` = `ondemand`) with `duration` (s), `broadcastedOn`, `availableTo`, `subtitled`, `show`,
   `links.target.href`. "Die Friesenjunkies": 17 episodes (MVW has only 9).
5. Episode: `GET /pages/ard/item/{id}?devicetype=pc&embedded=true`; the widget with `mediaCollection` is the player:
   - `mediaCollection.embedded.streams[].media[]`: `url`, `mimeType`, `maxVResolutionPx`, `forcedLabel`
     (e.g. "HD 1080p", "HD 720p", "Auto" = HLS), `audios[].kind`
   - `mediaCollection.embedded.subtitles[].sources[]`: `kind` (`webvtt` or `ebutt`), `url`
   - availability: `blockedByLoginOnly`, `availableTo`, `geoblocked`, `isGeoBlocked`, `blockedByFsk`
   - ARD audio description is a separate episode with its own id and title suffix "(Audiodeskription)".

### 2.3 ZDF

Verified 14.11.2026, works without login.

1. Bootstrap: `GET https://www.zdf.de/` (HTML). It contains `apiToken` values (regex
   `apiToken\\?":\\?"([^"\\]+)`) and app ids like `zdf-web-xxxxxxxx` (regex `"(zdf-web-[0-9a-f]+)"`).
   Read them fresh at least once per day / when a request answers 401/403. Never hard-code them.
2. GraphQL: `POST https://api.zdf.de/graphql`, headers `Content-Type: application/json`,
   `Api-Auth: Bearer {apiToken}`, `Zdf-App-Id: {appId}`, body `{"query": "...", "variables": {...}}`.
   Persisted-query hashes are NOT needed; the full query text is accepted. Introspection is disabled. Field names
   used by the website are in `docs/api/zdf-web-queries.json` and `docs/api/zdf-web-fragments.graphql`
   (strip `@client` fields like `responseMetadata` before sending).
3. Start page:
   ```graphql
   { curatedCollectionByCanonical(canonical: "mt2025-startseite-100") {
       modules(first: 40) { nodes { __typename
         ... on StaticHorizontalCluster { title items(first: 26) { nodes { __typename
           ... on ISmartCollection { title canonical }
           ... on Video { title canonical } } } } } } } }
   ```
   `first` must be ≤ 26. Result: 20 rows ("Top-Serien zum Streamen", "Packende Krimis", ...), 279 distinct shows.
   Item types include `SeasonSeriesSmartCollection`, `EndlessSeriesSmartCollection`, `MovieSmartCollection`,
   `Video`, `MetaCollection`, `ExternalLink`, `ZappingChannel` (skip the last three for now).
   Images: see fragments `TeaserImage`/`ImageFragment` (`layouts { dim768X432 ... }`).
4. Show: `smartCollectionByCanonical(canonical: $c)` with
   `... on ISeriesSmartCollection { seasons(first: 1, offset: $i) { nodes { title number countEpisodes
   episodes(first: 24) { nodes { title canonical editorialDate episodeInfo { seasonNumber episodeNumber }
   teaser { enableAudioDescription enableSubtitle } currentMedia { nodes { ptmdTemplate
   ... on VodMedia { duration vodMediaType geoLocation } } } } } } } }`.
   Example "krank-berlin-100": season 1, 8 episodes.
5. Stream: take `ptmdTemplate`, replace `{playerId}` with `ngplayer_2_5`, `GET https://api.zdf.de{template}`
   with `Api-Auth`. Response `priorityList[].formitaeten[]` (`mimeType`), `.qualities[]` (`quality` e.g. fhd/hd,
   `highestVerticalResolution`), `.audio.tracks[]` (`class` main/ad, `language`, `uri`); `captions[]` (`format`
   `webvtt` / `ebu-tt-d-basic-de`, `uri`). Prefer mp4 if present, else the best available format ExoPlayer plays.
   ZDF audio description is a separate audio track of the same episode (class `ad`).
6. CORS: irrelevant for the native app.

### 2.4 arte

1. Start page: `GET https://api.arte.tv/api/emac/v4/de/web/pages/HOME/` → JSON; entries with `title` and
   `programId` are single programmes (180 entries on 14.11.2026). Walk the JSON for `zones[]`/`content.data[]`.
2. Player: `GET https://api.arte.tv/api/player/v2/config/de/{programId}` →
   `data.attributes.metadata` (title, description, images, duration, link),
   `data.attributes.streams[]` (`url` = HLS manifest on `manifest-arte.akamaized.net`, `protocol`
   `API_HLS_NG_MA`, `versions[]` with audio/subtitle language). The manifest loads (`#EXTM3U`); actual playback
   not yet verified.

### 2.5 3sat

No own API in use. Shows and episodes come from MVW (channel `3sat`). Discover row for 3sat = newest shows of the
channel from MVW.

## 3. Subscriptions, duplicates and variants

A subscription is a saved MVW search that the app filters again locally. Model: `shared/.../subscription/Subscription.kt`.

- Stores: exact topics (one or more, e.g. `NANO` + optionally `NANO Doku`, `NANO Talk`), allowed broadcasters,
  minimum duration (default 20 min), preferred variant (standard, audio description, sign language), and
  `seenUntil` (everything older is not "new").
- Feed = all subscriptions' episodes, newest first, one entry per episode in the preferred variant.
- "Adjust subscription" dialog shows toggles: only full episodes (≥20 min), include related topics
  ("X Spezial", "X Talk", "X Doku"), prefer audio description, prefer sign language; plus the resulting MVW
  selector expression as text, e.g. `!3sat #nano >20`.

Duplicate and variant rules (verified with "nano" and "Die Anstalt"):

| Case | Example | Detection |
| --- | --- | --- |
| Same broadcast on two channels | Die Anstalt 10.11.2026 on ZDF and 3sat, different titles | identical `url_video` (verified); fallback: same timestamp, duration and size |
| Other variant | "Game of Drones (Audiodeskription)" next to the standard version | same title after removing the bracket suffix, same timestamp → one episode with variants |
| Clip instead of episode | nano "Chemie-Nobelpreis": 3sat 7 min vs SRF 28 min | min duration per subscription |
| Episode number in title | `(S2026/E145)`, `(S01/E08)` | parse season/episode, strip from displayed title |

Variant suffixes to recognise (case-insensitive): `(Audiodeskription)`, `(mit Audiodeskription)`, `Hörfassung`,
`(mit Gebärdensprache)`, `(Gebärdensprache)`, `(DGS)`, `(Originalversion)`, `(OV)`, `(OmU)`, `(klare Sprache)`.
Otherwise keep titles as delivered.

## 4. Cache

| Data | Refresh |
| --- | --- |
| Start pages, subscription feed | once per day on first app start, or manually via the "Updated: …" button |
| Thumbnails | Coil disk cache, evicted oldest first |
| Stream URLs, ZDF token | fresh right before playback (they expire) |

While refreshing, old data stays visible; the button shows "Refreshing …". Settings can clear the cache.
Background refresh: WorkManager, once per day.

## 5. TV UI (see docs/mockup.html)

Remote control only: D-pad, OK, Back, Play/Pause. Everything at 1920×1080 design size (Compose dp values in the
mockup CSS px / 2 roughly, e.g. 416 px tile = 208.dp).

- **Rail (left):** logo + name at the top, then Home, Discover, Search, Subscriptions, Watchlist; Settings at the
  bottom. Expands with labels when focused.
- **Fixed header (top right):** secondary button "Updated: today, 6:02" (grey background, never focused first) +
  clock. Does not scroll with content.
- **Home:** big "Last watched" block with primary button "Resume at 36:50" (**initially focused**, plain OK
  resumes) and secondary "Go to show"; rows: Continue watching, New from your subscriptions, Watchlist, then
  Discover rows.
- **Start screen** selectable in Settings: Home, Subscriptions (own feed by date), or Watchlist.
- **Subscriptions:** round show avatars on top (dot = new episodes; select = filter), then grid "All episodes,
  newest first" (4 columns). Title on one line; only the show name is truncated, "Episode 5 · yesterday" never.
- **Discover:** broadcaster filter chips (All, ARD, ZDF, 3sat, arte) + unified category rows. Category mapping via
  a table (e.g. ARD "Unsere Top-Serien" → Series); unknown rows → Highlights.
- **Search:** on-screen keyboard (a–z, ä ö ü ß, space, delete) + result grid; searches all four via MVW.
- **Watchlist:** grid of saved shows and episodes.
- **Show page:** hero image, broadcaster, genre, description; primary Play/Resume, Subscribe/Subscribed,
  "Adjust subscription" (when subscribed), Watchlist; episode row; "More from <broadcaster>".
- **Episode page** (tile OK opens it, Play key on a tile plays directly): show, episode no., duration, date,
  "available until", badges HD / UT / AD / DGS, description, progress bar, buttons Resume/Play (primary),
  From the beginning, Watchlist, Go to show; row "More episodes".
- **Long OK on a tile:** menu with Play, Subscribe/Unsubscribe, Watchlist, Mark as watched, Go to show.
- **Settings:** start screen (radio list), clear cache.
- **Buttons:** primary = white fill; secondary = grey (`Surface2`) background, white ring when focused. Buttons
  never wrap text.
- **Tiles:** 16:9 thumbnail, broadcaster/sub-channel badge bottom left (colour per broadcaster), duration bottom
  right, "NEW" badge top left, watchlist marker top right, progress bar at the bottom, focus = scale 1.06 + white
  ring.

### Player

- Controls: Play/Pause, Speed (0.75×, 1×, 1.25×, 1.5×, 2×), Subtitles, Watchlist, Next episode. Icons only from
  `res/drawable/ic_*`.
- The progress bar is focusable. When it is focused **or the controls are hidden**: a short Left/Right press jumps
  10 s back / 30 s forward; holding scrubs with growing steps 10 s → 30 s (0.5 s) → 1 min (1 s) → 2 min (1.5 s)
  → 5 min (2.2 s), one step every 120 ms; a marker and label show the target ("+7:20 · 1 min"); the jump happens on release.
  Logic: `shared/.../player/SeekAccelerator.kt`.
- OK while controls are hidden = play/pause and show controls (never trigger a hidden button).
- Back while controls are shown hides them; Back without controls leaves the player.
- Subtitles sit 12 % above the bottom edge (TV overscan) and move above the controls while those are shown.
- Controls hide after 4 s while playing. Progress is saved on exit (≥95 % = watched).

## 6. Assets

- Icons: Material Symbols Rounded (Apache-2.0), only the ones used, as vector drawables generated by
  `tools/icons/convert.py` from SVGs in `tools/icons/material-symbols-rounded/`. To add one: download the SVG from
  `https://cdn.jsdelivr.net/npm/@material-symbols/svg-500@0.49.0/rounded/<name>.svg` into that folder, run the
  script. No icon fonts, no runtime downloads.
- Fonts: Bricolage Grotesque (display) and Figtree (body), OFL, bundled in `res/font`.
- Launcher: adaptive icon (`ic_launcher_foreground`), TV banner `drawable-xhdpi/banner.png`
  (rendered from `tools/banner/banner.html`).

## 7. Release

- GitHub Action `release.yml` (manual, input `version`, e.g. `1.2.0`): builds the signed release APK
  (`-PappVersionName=1.2.0`, versionCode = major·10000 + minor·100 + patch), tags `v1.2.0`, creates a GitHub
  release with the asset **`quartett-mediathek.apk`** (fixed name).
- `https://github.com/wi-wissen/quartett-mediathek/releases/latest/download/quartett-mediathek.apk` always points
  to the newest version; the short link **https://t1p.de/qmedia** points there (for sideloading via a downloader
  app on the TV).
- Signing secrets: `QM_KEYSTORE_BASE64`, `QM_KEYSTORE_PASSWORD`, `QM_KEY_ALIAS`, `QM_KEY_PASSWORD`.
- **Self-updater** (`app/.../update/`, pure logic in `shared/.../update/UpdateLogic.kt`): the update source is set at
  build time, never in the repository. Gradle property `updateRepo` (`owner/name`) becomes `BuildConfig.UPDATE_REPO`;
  empty = updater disabled. `release.yml` passes `-PupdateRepo=${{ vars.UPDATE_REPO || github.repository }}`, so the
  building repository is the default and the repository variable `UPDATE_REPO` can point elsewhere.
  - Check: `GET https://api.github.com/repos/{repo}/releases/latest` (unauthenticated), tag `v1.2.3` -> versionCode
    with the same formula as Gradle, compared with `BuildConfig.VERSION_CODE`; asset `quartett-mediathek.apk`.
    On app start at most once per 24 h (time in `SharedPreferences` "updater"), manually via Settings.
  - UI: dialog on start "Version x is available – Install now / Later" (Later hides it for 24 h); Settings section
    "App" (Settings order: start screen, App, Data) with version, last check, "Check for updates" and, when a newer
    version exists, "Update now" with download progress.
  - Install: download to `cacheDir/updates` (HttpURLConnection, follows the redirect to objects.githubusercontent.com),
    then `PackageInstaller` session, result in `UpdateInstallReceiver` (starts the system confirmation). Needs
    `REQUEST_INSTALL_PACKAGES`; if `canRequestPackageInstalls()` is false the "unknown apps" settings page of this
    package opens. Same signing key = update over the existing app, data is kept.
