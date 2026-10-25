# theCrag data access: research notes (2026-10-20)

Everything below was verified with `curl` from the dev container on 2026-10-20 unless
marked **inferred**. Trimmed sample responses are in `../api-samples/`.
The site is behind Cloudflare and is sensitive to the `User-Agent`: curl's default
UA and the bare string `Mozilla/5.0` get HTTP 403; a full desktop-browser UA and
`okhttp/4.12.0` both work. The app must send a stable, realistic UA.

## 1. Two APIs, one URL space

| | Official partner API | What the web UI uses |
|---|---|---|
| Docs | <https://www.thecrag.com/en/article/api>, WADL at `/api-wadl.xml` (140 resources) | undocumented, same `/api/...` paths |
| Auth | app key (`?key=` or `X-CData-Key: key=…;`), OAuth **1.0a** HMAC-SHA1 for user data | anonymous web session (see §2) |
| Availability | "THE API IS CURRENTLY CLOSED FOR NON-COMMERCIAL APPLICATIONS"; key only after a signed agreement; sandpit.thecrag.com is down (522) | works today, no key |
| Terms | <10 calls / 10 s across all installs; no caching/storing unless negotiated; attribute contributors; check publisher `copyrightID` on descriptions/photos/topos; data is CC BY-NC-SA | site terms forbid screen scraping and say programmatic access must go via the API; the API article says the site's own secondary endpoints are "not for client apps without special permission" |

Self-serve official path that exists today: **personal API keys** (supporters only,
Settings > API Keys) giving read-only access to the user's *own* logbook:
`GET /api/logbook/ascents?page=N&since=<epoch>` with `X-CData-Key: key=<key>`,
250 per page, hidden weekly token budget. Docs: `/en/article/logbookreadapi`.

## 2. Access levels (the key finding)

Error bodies name the level an endpoint needs. What grants each level, verified:

| Level | Granted by | Unlocks |
|---|---|---|
| none | browser-like UA only | `/api/lookup/*`, `/api/config/*` |
| `thecragreferrer` | header `Referer: https://www.thecrag.com/<anything>` | `/api/map/*` |
| `anonweb` | Referer **and** the anonymous session cookie `ApacheSessionID` (set by a plain `GET /home`; `userstatus=anon`, `acceptedterms=0` come with it; ~2 year expiry) | `/api/node/id/*`, `/api/node/ids`, `/children/*`, `/api/country/list`, every `show=` option tried |
| `node` | not reachable anonymously | `/api/facet/*`, `/api/index/*` (the bulk/offline endpoints) |
| `endpoint` | not reachable anonymously | `/api/area/id/N/topos`, `/photos`, `/beta` |
| key / OAuth | partner key | everything, writes |

Errors are HTTP 400 with `{"error":…,"errorLabel":"insufficient-api-permissions"|"invalid-api-key"|…}`.
**Throttling (observed 2026-10-20):** after a few dozen anonymous HTML node-page loads
from one IP, every node page answers `302` to `swamp.processing.thecrag.com`, which
redirects to `/processmap/login?throttled=1`, which serves the login page with HTTP 200.
It is per IP (a fresh session cookie or no cookie makes no difference) and hits only
HTML; `/api/*` kept answering normally throughout. The "tab" HTML pages
(`/routes`, `/maps`, `/topos`, `/photos`, `?format=csv`) redirect anonymous clients to
a login page; the main node pages do not.

## 3. Read endpoints that work anonymously (anonweb)

Base `https://www.thecrag.com`. All return `{"data": …, "query": {…}}`.

| Endpoint | Notes | Sample |
|---|---|---|
| `GET /api/node/id/{id}` | area or route. `show=` is repeatable: `ancestors` (breadcrumb from World), `children`, `info` (beta sections: Description, Access Issues, Approach, Descent Notes, Ethic; `inheritedFrom` marks text inherited from an ancestor), `location` (`geometry`: `center`, `lat`/`long`, `point`, `bbox`, `boundary` polygon, `altitude`), `topo` (see §4), `tags`, `karma`. `markupType=html` renders markdown to `markupHTML`, default gives `markdown`. Aliases: `/api/area/id/`, `/api/route/id/`, `/api/climb/id/`. | `area_11740915_show_ancestors.json`, `area_11962579_show_location_geometry.json`, `route_12873151_show_all.json` |
| `GET /api/node/ids?id=a,b,c&show=…` | batch form, `data` is an array; `show` comma-separated. Verified 2026-10-25 with 105 ids and `show=topo,info,ancestors`: 600 kB in 0.3 s, ~5.7 kB per route. The offline downloader uses it in batches of 50 | |
| `GET /api/node/id/{id}/children` `/children/area` `/children/route` | sub-areas (with `geometry`) or routes; `flatten=data[id,name,…]` trims fields | `area_11764567_children_area.json`, `area_11962579_children_route.json` |
| `GET /api/country/list` | country → node id + default grade context | `country_list.json` |
| `GET /api/lookup/node?search=&ancestor={id}&page-size=&page=` | search box; `ancestor` is mandatory (World = `7546063`); route `qualifier` is the grade | `lookup_node.json` |
| `GET /api/lookup/crag?search=` | crags only; `&opensearch=1` gives names only | |
| `GET /api/map/summary/{id}` (Referer only) | `[nodeId, lon*1e6, lat*1e6, unlocatedRoutes?, relDepth]` rows for all located descendants | `map_summary_11740915.json` |
| `GET /api/map/bbox/heirachy?s=minLon,minLat,maxLon,maxLat&f=geometry,numberRoutes,numberAscents,gearStyles&v=<fov>` (sic, Referer only) | nested tree of areas intersecting the bbox with `center`, `bbox`, leaf `geometry` (polygon as a JSON string); `&o=id,id` loads deltas | `map_bbox_heirachy.json` |
| `GET /api/map/tile/poipath?lat=&long=` | POIs / approach paths; returned empty at every place tried | |
| `GET /api/config/grade/{system,type,context}`, `/api/config/ascent/{ticktypes,quality,relativedifficulty}`, `/api/config/structured-tags` | static config, no auth | |

Not available anonymously: `/api/index/summary|detail` (bulk sync), `/api/facet/*`
(sorted/filtered lists), `/topos`, `/photos`, `/beta`, route ascents, CSV export.

### Node model
- One numeric id space for areas and routes. Ids exceed 2^31 and sometimes arrive as
  JSON strings (`"id":"12873151"`, `childIDs` mixes both). Parse as `Long` leniently.
- Area fields: `name, asciiName, type:"area", subType (Region|Area|Crag|Cliff|Sector|Boulder…), urlStub, urlAncestorStub, parentID, depth, childIDs, subAreaCount, numberRoutes, numberTopos, numberPhotos, ascentCount, hasTopo, averageHeight [27,"m"], seasonality[12], gbRoutes[6]/gbAscents[6] (per grade band), priceCategory, isTLC/tlc (top level crag), altNames, unique (blurb), siblingLabel (number in the guide)`.
- Route fields: `grade, gradeInContext, context ("AU"), gradeBand (0-5), gradeStyle, rawGrade, gradeAtom, systems{systemId:[grade]}, style (Sport|Trad|Boulder|…), styleStub, flags, stars (0-3), qualityScore, popularity, ascentCount, bolts, height ["18","m"], pitches, siblingLabel, history[{type:"First Ascent", climbers, date}], beta[], topos[]`.
- `beta[]` entries carry `copyrightID`/`publisherID` when the text comes from a third-party publisher (e.g. ACA Route Register) → must be attributed.
- Web page URLs: `/en/climbing/{urlStub}`; nodes without a stub use `/en/climbing/{urlAncestorStub}/{slug}/route/{id}` or `/area/{id}`. The server resolves pages by id regardless of the path, so `/en/climbing/world/area/{id}` and `/en/climbing/world/route/{id}` return the page directly (HTTP 200, no redirect); the app uses these.

## 4. Topos

`show=topo` on an area or route returns `topos[]`:

```
{ id, hashID (40-hex sha1 of the image), node, name, type:"topo",
  width, height            // base coordinate space of the overlay (e.g. 413 x 550)
  origWidth, origHeight    // original image pixels (1200 x 1600)
  aspectFlipped, lastUpdated, positionMinLabel, positionMaxLabel, linked,
  insideNode { …full area node… },
  objects[] { objectID, objectType:"Area"|"Route", parentObjectID, shape, siblingLabel, …node fields… } }
```

- **Area outlines** come with `shape`: first token `x y <opts> <urlencoded label>` where
  opts = halign(l/c/r) valign(t/m/b) visibility(v/h) line(n/y/p) expand(d/a); the rest are
  polygon vertices `x y`.
- **Route lines are in the JSON after all**: route objects have `shape: null` but carry
  `points` (`"298 401 label,283 292,…,226 60 lower"`), byte-identical to the `data-topodata`
  the website embeds in its pages. The first research pass only looked at `shape`, and
  M4 scraped `div.phototopo[data-topodata]` from the page HTML until page loads turned
  out to be throttled (above). Samples: `../api-samples/route_12873151_show_topo.json`,
  `../api-samples/area_2306004858_show_topo.json`.
  `points` is a comma list of `x y [type]`; types: `label, endarrow, draw, bolt, piton, thread, lower, anchor, belay, belayfixed, belaynatural, belaysemi, belayhanging, crux, warning, jumpoff`, trailing `hidden`. The site smooths the polyline in SVG (`static.thecrag.com/cids/phototopo-1.1.65.js`, which just applies a server-supplied `viewScale`, "scale of max size of topo").
  **Coordinate space** = the original image fitted into a 550x550 box. Verified on five
  topos: 898x823 -> 550x504 (page `data-width/data-view-scale` = 349/0.6345), 428x700 ->
  336x550, 4624x3468 -> 550x412 (Shengavit, five topos), 7000x5250 -> 550x412 (Ohanavan
  area outlines). The JSON `width`/`height` is unreliable: it is the fitted size for old
  topos and the original size for newer ones; use `origWidth`/`origHeight`.
  The route number drawn on the topo is `siblingLabel`; `gradeBand` is the `gbN` class.
- Image URL: `https://image.thecrag.com/{W}x{H}/{h[0:2]}/{h[2:4]}/{hashID}`; the server
  scales to fit, keeps aspect, may answer webp or jpeg. Without the size segment it
  returns the original. No auth; needs the realistic UA.

## 5. Auth, logbook, ticking (Stage 2 inputs)

- Website login: `GET /processmap/login?return=…` → 302 → `/CIDS/cgi-bin/cids.cgi` form.
  `POST /CIDS/cgi-bin/cids.cgi` form-urlencoded with `D:Login, D:Password, State:Login, C:ResetContinueURL=1, D:LoginTarget, C:Portal=Default Licensee, D:CSRF` (CSRF scraped from the GET). Google and Facebook login exist (JS `googleBoot()`, `FBboot()`). No JSON login endpoint.
- The site's JS (`common-1.1.78.js`, `postAPI`) sends JSON writes to `/api/<x>/update?cookieAuth=1`, i.e. the session cookie authenticates `/api` calls when `cookieAuth=1` is present. Whether `POST /api/ascent/create?cookieAuth=1` accepts this is **inferred** (not tested; nothing was posted).
- Official ascent write (OAuth1): `POST /api/ascent/create` (and `/create/validate`) with `{"data":{account, node, version:2, tick, date, climbedGearStyle, grade, gradeSystem, quality, relativeDifficulty, comment, trip, numberAttempts, externalUuid, pitch[]}}` → `{"ok":{"ascentID","uri"}}`. Tick types per gear style are in `/api/config/ascent/ticktypes` (open endpoint). Quality: crap…megaclassic. Relative difficulty: soft…sand.
- Reading ascents (key/OAuth): `/api/climber/id/N/ascents`, `/api/ascent/id/N`, `/api/facet/ascents/at/N/by/A`. Self-serve: personal key `/api/logbook/ascents` (§1).
- **authweb level (verified 2026-10-20 from error messages, no login performed)**: with `cookieAuth=1`,
  `/api/(climber|user|account)/id/(me|N)`, `.../ascents`, `.../target-lists`, `/api/ascent/id/N`,
  `POST /api/ascent/create` and `/create/validate` all require "minimum level authweb", i.e. a
  logged-in session cookie. `/api/logbook/ascents` needs level `logbook` (the personal key).
  The login form `GET /CIDS/cgi-bin/cids.cgi?C:HideNavigation=1&C:State=13216&D:LoginTarget=…`
  is served to a plain client (200, sets `ApacheSessionID`, carries `D:CSRF`). A wrong login
  answers 200 with the form again and `<div class="alert alert-danger">Your username is not
  listed</div>`; success is expected to be a 302 to `D:LoginTarget` (inferred, untested).
- **Verified live (2026-10-20, throwaway account, test area only):** the login POST answers
  `302` to `cids.cgi?C:State=Home&D:LoginTarget=…` and re-sets `ApacheSessionID` (two Set-Cookie
  headers, the second with a 3-year expiry); no email verification is needed for API use.
  `GET /api/account/id/me?cookieAuth=1` -> `data{id, login, name, slug, private, ascentCount,
  country…}` (`api-samples/account_me.json`). `GET /api/climber/id/me/ascents?cookieAuth=1` ->
  `data[]{id, date (ISO datetime), logDate, markdown, climbedGearStyle, tick{label,name},
  route{id, name, gradeBand, stars, style, ancestors[]}, account{…}}`, no paging info in
  `query` (`account_me_ascents.json`). `POST /api/ascent/create/validate?cookieAuth=1` with
  the v2 body answers **202** echoing `data` (no `ok`); `POST /api/ascent/create` answers 202
  with `ok{ascentID, eventStats, isFirstAscent}`; `POST /api/ascent/delete` wants
  `{"data":{"ascent":id}}` (`{"id":…}` -> 400 `ascentError: mandatory field`) and answers
  202 `ok{}`. Rejections are HTTP 400 with `error{field: reason}`.
- OAuth 1.0a: `/oauth/request_token` → `/oauth/authorize?oauth_token=` → `/oauth/access_token`; consumer key needs the partner agreement. A FOSS app cannot keep a consumer secret private anyway.

## 6. Terms and licensing summary

- User content: CC BY-NC-SA, attribution must link to the theCrag page. Third-party
  publisher content (guidebook descriptions, photos, topos) carries `copyrightID`/`publisherID`
  and separate rights.
- Site terms: no screen scraping; programmatic access through the API; personal,
  non-commercial use.
- API article: caching on a mobile client is "typically" fine, server-side storage is
  not; offline storage (Stage 3) must be negotiated; <10 calls/10 s across all installs.
- Nothing in the terms addresses FOSS explicitly. Contact: support@thecrag.com.

## 7. Prior art

- No existing Android/Kotlin/Flutter client. Only HTML scrapers (`0x49b/thecragloader`), a CSV logbook differ (`dnnr/thecrag-sync`), a Chrome extension (`balicat/shade-on-thecrag`), an abandoned 2011 official JS stub, and `cragpy` (PyPI, personal-key logbook wrapper).
- Official mobile presence: Topo Guru "powered by theCrag" (closed, offline topos are premium), Redpoint Apple Watch app, Chalk (iOS) two-way sync.
- Best FOSS architectural precedent: YacGuide (F-Droid, GPL-3.0, Kotlin + Room, downloads whole regions for offline use).
- Maps: osmdroid is archived; MapLibre Native is active (BSD-2). OSM tile policy forbids bulk prefetch and requires a distinct UA; offline tiles need another source (PMTiles extracts, OpenFreeMap, paid keys).

## 8. Open questions

1. Will theCrag tolerate a FOSS client on the anonweb endpoints? (Ask before release; the terms as written say no.)
2. Is the Referer + session gating stable? It is an implementation detail of the website.
3. Real rate limits for anonweb (none hit in ~80 calls).
4. Topo polyline smoothing needs visual comparison with the site (coordinate space is verified).
5. Where `poipath` (approach paths) has data; whether any anonweb `show=` exposes photos.
6. Whether `cookieAuth=1` works for `ascent/create`, and whether a custom-scheme `oauth_callback` is accepted.

## Caching (checked 2026-10-20)

API (`/api/node/...`, `/api/map/...`) and node page responses carry no
`Cache-Control`, `ETag` or `Expires`; only `Vary: Accept-Encoding` and
`cf-cache-status: DYNAMIC` (pages: `HIT`, with `Last-Modified`). Images on
`image.thecrag.com` send `cache-control: max-age=172800`. The app therefore
stamps `Cache-Control: public, max-age=3600` on successful GETs in a network
interceptor so OkHttp's disk cache keeps them, and retries with
`only-if-cached, max-stale=90d` when the network fails.

## Offline copies (2026-10-25)

Starred areas are not re-modelled: the app pins the raw responses of the exact URLs
the UI requests (node detail with its `show=` list, `show=topo`, the two children
listings, topo images at 1200x1600) and an application interceptor answers them
from disk. Node bodies come from the batch endpoint above and are split into
`{"data": node}` per id; a body fetched with `show=ancestors,info,location,topo`
parses fine for both the detail and the topo request since unknown keys are
ignored. Topo images at 1200x1600 are ~400 kB (`content-type: image/jpeg`,
`max-age=172800`).
