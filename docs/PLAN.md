# theCrag Android client: plan

Status: Stage 1 in progress. M1 skeleton, M2 API client and a first cut of M3 browse/route/search build and pass unit tests (2026-10-20); M4 topos and M5 map are next. Research is in `research/api.md`; sample
payloads in `api-samples/`.

## 1. Decisions (settled 2026-10-20)

| # | Question | Decision |
|---|---|---|
| 1 | App licence | Beerware (rev. 42), see `LICENSE`. Data stays CC BY-NC-SA and is not bundled |
| 2 | Name / package id | `thecrag-android-unofficial`; applicationId `org.thecragunofficial.android`; launcher label "theCrag unofficial" |
| 3 | Contact theCrag? | No release planned; possibly a single user. Not contacting for now. Keep traffic polite (rate limiter, caches) so this stays a non-issue; revisit if it is ever published |
| 4 | Map tiles | Default: MapLibre Native with OpenFreeMap vector tiles (no key, no registration, OSM data; attribution "© OpenMapTiles © OpenStreetMap contributors"). Options kept open in settings later: OpenTopoMap raster (contours, CC BY-SA, small volunteer servers), OSM raster (meant for osm.org, no prefetch), Thunderforest Outdoors / MapTiler Topo (best hiking maps, need a user-supplied key), Esri World Imagery (satellite, attribution required). Offline tiles for Stage 3: Protomaps PMTiles extracts, which MapLibre can read |
| 5 | minSdk / target | 26 / 35 |
| 6 | DI | Hand-rolled `AppGraph` instead of Hilt: fewer build plugins, single-user app |

## 2. Data strategy (the core constraint)

- **Primary source: the website's own JSON endpoints at anonweb level.** Every API
  call carries a realistic `User-Agent`, `Referer: https://www.thecrag.com/`, and the
  anonymous `ApacheSessionID` cookie obtained by one `GET /home` on first run
  (persisted; refreshed on any `insufficient-api-permissions` error).
- **Route lines on topos** are not in the JSON. For a crag with topos, fetch the
  cached HTML page once and parse `div.phototopo[data-topodata]` with jsoup. The
  topo image and the area outlines come from `show=topo`; both sources are joined on
  topo id (`data-tid` == `topos[].id`).
- **Coordinates**: `show=location` for a node, `/api/map/bbox/heirachy` for the map
  viewport, `/api/map/summary/{id}` for all located descendants of an area.
- **Search**: `/api/lookup/node?ancestor=7546063` (global) or scoped to the current area.
- **Politeness**: client-side token bucket (≤1 request/s, burst 3), OkHttp disk cache
  honouring server headers plus a Room cache with a TTL (7 days for nodes, 30 for
  topos/config), ETag reuse. This stays under the partner API's "<10 calls / 10 s"
  guideline per install by a wide margin.
- **Attribution**: every screen with content shows "Data © theCrag contributors,
  CC BY-NC-SA" with a link to the node's page; `beta[]` entries with `publisherID`
  show the publisher name; topos/photos link back to the source.
- **Not used**: `/api/index/*` and `/api/facet/*` (need a key), tab pages (throttled),
  CSV export.

## 3. Architecture

Single-module to start, split into Gradle modules when Stage 3 lands. Dependencies are wired by hand in `AppGraph` (no DI framework).

```
app/
  data/
    remote/     OkHttp + Retrofit, kotlinx.serialization (lenient Long ids), interceptors:
                UA + Referer + cookie jar, rate limiter, session bootstrap/retry
    html/       jsoup parser for data-topodata -> TopoOverlay
    local/      Room: nodes, children edges, topos, topo objects, favourites (Stage 3)
    repo/       NodeRepository, SearchRepository, MapRepository, TopoRepository
  domain/       Node (Area|Route), Grade (context + systems), Topo, TopoPath, GeoBox
  ui/           Jetpack Compose + Material 3, single-activity, type-safe navigation
    browse/     area screen: header, breadcrumb, sub-areas, routes table (grade band colour, stars, style, height, bolts)
    route/      route screen: grade in context, description/access/ethic, FA, topos with this route highlighted
    topo/       zoomable image (Coil) with Canvas overlay: smoothed polylines, icons per point type, labels; tap to select route
    map/        MapLibre: area polygons/markers from bbox hierarchy, tap -> area
    search/     global + scoped lookup
```

Libraries: Kotlin 2.x, Compose BOM, Material 3, Navigation Compose, Retrofit 2 +
OkHttp 4, kotlinx.serialization, Room 2.7, Coil 3, jsoup, MapLibre Native Android
11.x, kotlinx.coroutines. Tests: JUnit 5 + kotlinx-serialization fixtures from
`docs/api-samples/`, MockWebServer for the session/retry logic.

## 4. Stages and milestones

### Stage 1: anonymous online guide
1. **M1 project skeleton**: Gradle + version catalog, nix build in the container, CI
   workflow (assembleDebug + unit tests), app icon, licence headers.
2. **M2 API client**: session bootstrap, interceptors, models for node/children/
   lookup/location/topo, lenient id parsing, fixtures-based tests.
3. **M3 browse**: World → regions → crags → routes; breadcrumb; route table; route
   screen with beta sections rendered from markdown; grade context from `country/list`.
4. **M4 topos**: HTML topodata parser; overlay renderer; verify scaling against the
   website on three topos (Arapiles, a Fontainebleau boulder, an alpine multi-pitch).
5. **M5 map**: MapLibre screen, bbox hierarchy loading on viewport change with debounce,
   polygons for leaf crags, markers with route counts, "map" button on an area screen.
6. **M6 search + polish**: lookup search, recent items, error/offline states, attribution
   screen, F-Droid metadata (`fastlane/`), release build.

### Stage 2: account, logbook, ticks
- Login in a WebView on `/processmap/login`; capture `ApacheSessionID` from the
  CookieManager; the API client sends `?cookieAuth=1` on writes. First task is to
  verify `POST /api/ascent/create?cookieAuth=1` against a throwaway account (needs an
  owner-approved test, not done yet). Form fields and the ascent schema are documented.
- Logbook read: `/api/climber/id/{me}/ascents` with the cookie if it works at that
  level, else the supporter personal key path (`/api/logbook/ascents`) as an opt-in.
- Tick UI: tick type list from `/api/config/ascent/ticktypes` filtered by gear style,
  date, grade with system, quality, comment; queued offline, sent when online.
- If theCrag grants a partner key: swap the auth layer for OAuth 1.0a (signpost-style
  signing, custom-scheme callback); keep the cookie path as fallback.

### Stage 3: offline favourites
- "Star" an area: background WorkManager job walks the subtree (children/area +
  children/route, batched with `node/ids`), stores nodes + topos in Room, prefetches
  topo images into Coil's disk cache at 1200px, parses and stores route lines.
- Size guard and progress UI; manual refresh with `lastUpdated` comparisons.
- Map tiles stay online unless a prefetch-friendly tile source is adopted.
- This stage is the one most clearly outside theCrag's written terms; gate it on
  their answer (see decision 3).

## 5. Risks

| Risk | Mitigation |
|---|---|
| theCrag objects or blocks the UA/Referer trick | Keep the data layer behind one interface; obey any request to stop; ask early |
| Gating details change (Referer, cookie, levels) | Detect `insufficient-api-permissions`, re-bootstrap session, surface a clear error; endpoints covered by fixture tests |
| Cloudflare bot rules on image/API hosts | Realistic UA; no bursts; exponential backoff on 403/429 |
| Topo coordinate space wrong | M4 includes side-by-side verification before building on it |
| Publisher-copyrighted descriptions | Show attribution, allow hiding publisher content in settings |
| Ids > 2^31 and mixed string/number | Lenient `Long` serializer, tested with `9091907751` |

## 6. Build environment in the container

No JDK or SDK is preinstalled. `nix` is available:
`docs/build-env.nix` composes platform 35 + build-tools 35.0.0 only (the default
`androidenv.androidPkgs` pulls the NDK and emulator images). JDK via
`nix shell nixpkgs#jdk17_headless`. Gradle wrapper from the project. No emulator in
the container; UI verification happens on the owner's device or via screenshots of
Compose previews (Paparazzi can run headless and is worth adding at M3).
