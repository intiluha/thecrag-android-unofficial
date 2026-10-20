# theCrag Android client: plan

Status: Stage 1 feature-complete (2026-10-20). M1–M5 run on a phone (browse, routes, search, topos, map). M6 polish added recent items, a disk-cache offline fallback, scoped search, an About screen and a minified release build; the release build has not run on a device yet. Next: Stage 2 (login, logbook, ticks). Research is in `research/api.md`; sample
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
- **Topos** come entirely from `show=topo`: image hash, route lines (`objects[].points`)
  and area outlines (`objects[].shape`). Coordinates are in the original image fitted
  into 550x550 (see research). No HTML page loads: those are throttled per IP after a
  few dozen views (302 to a login page), the JSON API is not.
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
    repo/       NodeRepository, TopoRepository + TopoMapper, MapRepository, RecentStore
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
OkHttp 4, kotlinx.serialization, Room 2.7, Coil 3, MapLibre Native Android
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
4. **M4 topos**: topo overlay from `show=topo` (first cut parsed page HTML, replaced
   2026-10-20 when page loads turned out to be throttled); verify scaling against the
   website on three topos (Arapiles, a Fontainebleau boulder, an alpine multi-pitch).
5. **M5 map**: MapLibre screen, bbox hierarchy loading on viewport change with debounce,
   polygons for leaf crags, markers with route counts, "map" button on an area screen.
6. **M6 search + polish** (done): search scoped to the current area, recent items, offline
   fallback from the OkHttp disk cache (theCrag sends no cache headers, so the app stamps
   a 1 h max-age and serves stale copies when the network fails), About screen with
   attribution and cache clearing, minified release build signed with the debug key.
   Skipped: F-Droid metadata (`fastlane/`), since no release is planned.

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
`shell.nix` composes platform 35 + build-tools 35.0.0 only (the default
`androidenv.androidPkgs` pulls the NDK and emulator images). JDK via
`shell.nix` too; recipes are in the `Justfile` (`just build`, `just test`, `just install`). No emulator in
the container; UI verification happens on the owner's device or via screenshots of
Compose previews (Paparazzi can run headless and is worth adding at M3).
