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
About 80 calls were made without any throttling. The "tab" HTML pages
(`/routes`, `/maps`, `/topos`, `/photos`, `?format=csv`) redirect anonymous clients to
a login page; the main node pages do not.

## 3. Read endpoints that work anonymously (anonweb)

Base `https://www.thecrag.com`. All return `{"data": …, "query": {…}}`.

| Endpoint | Notes | Sample |
|---|---|---|
| `GET /api/node/id/{id}` | area or route. `show=` is repeatable: `ancestors` (breadcrumb from World), `children`, `info` (beta sections: Description, Access Issues, Approach, Descent Notes, Ethic; `inheritedFrom` marks text inherited from an ancestor), `location` (`geometry`: `center`, `lat`/`long`, `point`, `bbox`, `boundary` polygon, `altitude`), `topo` (see §4), `tags`, `karma`. `markupType=html` renders markdown to `markupHTML`, default gives `markdown`. Aliases: `/api/area/id/`, `/api/route/id/`, `/api/climb/id/`. | `area_11740915_show_ancestors.json`, `area_11962579_show_location_geometry.json`, `route_12873151_show_all.json` |
| `GET /api/node/ids?id=a,b,c&show=…` | batch form | |
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
- **Route lines are `shape: null` in the JSON** for every route object seen (37 of 37).
  Route lines exist only in the HTML of the crag/route page, as
  `<div class="phototopo" data-tid data-width data-height data-view-scale data-topodata='[…]'>`
  with `<img src="https://image.thecrag.com/240x320/…" data-big="…/1200x1600/…">`.
  Sample: `../api-samples/topodata_html_sample.json`. `data-topodata` route entries: `{id,type:"route",num,grade,class:"gb2",zindex,name,stars:"★",style,order,url,points:"46 268,45 224,79 193 lower"}`.
  `points` is a comma list of `x y [type]`; types: `label, endarrow, draw, bolt, piton, thread, lower, anchor, belay, belayfixed, belaynatural, belaysemi, belayhanging, crux, warning, jumpoff`, trailing `hidden`. The site smooths the polyline in SVG (`static.thecrag.com/cids/phototopo-1.1.65.js`). The coordinate space is `data-width/data-view-scale`, which equals the API topo `width`/`height` (verified: 349/0.6345 = 550 for topo 119694216, present in both sources with the same id). Point smoothing (the site curves the polyline in SVG) still needs visual comparison.
  The main node pages are Cloudflare-cached and need no cookie, so one HTML GET per crag gives all its topos' route lines. The JSON `topos[]` ids for a node and the `data-tid`s on its page were identical for the two nodes compared (11962579, 11764567).
- Image URL: `https://image.thecrag.com/{W}x{H}/{h[0:2]}/{h[2:4]}/{hashID}`; the server
  scales to fit, keeps aspect, may answer webp or jpeg. Without the size segment it
  returns the original. No auth; needs the realistic UA.

## 5. Auth, logbook, ticking (Stage 2 inputs)

- Website login: `GET /processmap/login?return=…` → 302 → `/CIDS/cgi-bin/cids.cgi` form.
  `POST /CIDS/cgi-bin/cids.cgi` form-urlencoded with `D:Login, D:Password, State:Login, C:ResetContinueURL=1, D:LoginTarget, C:Portal=Default Licensee, D:CSRF` (CSRF scraped from the GET). Google and Facebook login exist (JS `googleBoot()`, `FBboot()`). No JSON login endpoint.
- The site's JS (`common-1.1.78.js`, `postAPI`) sends JSON writes to `/api/<x>/update?cookieAuth=1`, i.e. the session cookie authenticates `/api` calls when `cookieAuth=1` is present. Whether `POST /api/ascent/create?cookieAuth=1` accepts this is **inferred** (not tested; nothing was posted).
- Official ascent write (OAuth1): `POST /api/ascent/create` (and `/create/validate`) with `{"data":{account, node, version:2, tick, date, climbedGearStyle, grade, gradeSystem, quality, relativeDifficulty, comment, trip, numberAttempts, externalUuid, pitch[]}}` → `{"ok":{"ascentID","uri"}}`. Tick types per gear style are in `/api/config/ascent/ticktypes` (open endpoint). Quality: crap…megaclassic. Relative difficulty: soft…sand.
- Reading ascents (key/OAuth): `/api/climber/id/N/ascents`, `/api/ascent/id/N`, `/api/facet/ascents/at/N/by/A`. Self-serve: personal key `/api/logbook/ascents` (§1).
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
