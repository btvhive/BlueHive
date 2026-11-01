# Live TV (zlive.st) — build plan

Status: **design C chosen by the user, 2026-10-28.** Stage 1 (VPS) was first deployed
INTO the shared movie/TV containers (18:33 UTC) and rolled back at 18:45 (all three run
the original files again, verified). It is rebuilt in **its own containers** (§3),
tested 45/45 on the exact images, deployed by the user at 19:15 UTC, and **LIVE since
19:43:49 UTC** once `own/fix-caddy-route.sh --go` loaded the Caddy route (Caddy's
container had been reading an older inode of the Caddyfile). Verified through the public
domain: `/api/live/*` → Live TV, everything else → `bluehive-api`, playlists 0.1-2.0 s,
HTTP/2 + HTTP/3 intact; movie/TV containers and files untouched.

**Stage 2 (TV app) = v484, 2026-10-28:** built with every gate (303 unit tests incl. 19 new,
method budget now covering `livetv/`, on-device verifier 12,844 classes / 0 failures) and
installed on the onn box only (APK sha256 902e4c54…). Round 1 = the user tests it.
Player overlays use the player mockup's own factor (its 334 px frame = 960 dp, so ×2.874,
one constant `P` in `LivePlayerOverlays.kt`).

**Round 1 (v484, the user zapping):** MotoGP, Sky News, Sky Sci-Fi, Animal Planet, ESPN Backup,
Sky Sports F1, Boomerang, NFL Network and NBA TV played. PBS Kids and Nicktoons failed: they sit
on zlive's `hello.transcode.cfd` proxy, whose playlist and plain-TS chunks answer 403 without
zlive's `Origin`/`Referer` (not IP-bound). Fix = API image `bluehive-live-api:20261028b`
(47/47; `own/deploy-live-api.sh` recreates only `bluehive-live-api`) + TV **v485** (the session
answer's headers go on the player's requests). The user deployed the API image at 20:46 UTC;
v485 device-verified (12,844 classes / 0) and installed on the onn box.

**Page vs mockup (v485, measured on a 1920×1080 screenshot):** chips, rows, details panel and
hint bar within 1-4 px of the ×1.412 spec; colours exact (`#2644A6`, `#222340`, News band
`#D9A21B`, panel `#12151D` over the art).
Mockups were shown in the chat (`live_tv_browse_designs_abc`, `live_tv_player_states`);
design C is built to its exact sizes and colours (spec in §4a).

**Search (v504, 2026-10-30):** the user picked design B's bar opening design C's page from
`live_tv_search_designs_abc` (same 680 × 382 px frame, so ×1.412 again).
- The guide's list starts 42 px lower, under a search bar, and shows 9 rows. Up from the first
  channel reaches the bar; the panel then reads "Search channels" with what it covers. Click
  opens the search page.
- v510: bar to list is 14 px, the same as chips to bar (the mockup's 6 px was too tight).
  Fixed in the same build: after scrolling down a list and back up, row 0 stayed half under the
  bar (`keepInView`).
- Search page: the honeycomb keyboard on the left (`HoneycombKeyboardInline`, placed by the
  page). On the right, until something is typed, 12 quick picks (Fox, Sky Sports, ESPN, beIN,
  DAZN, TNT, NBC, BBC, HBO, Showtime, Disney, Nickelodeon), each showing its channel and live
  counts. After that, results as two-column tiles.
- Every key filters on the TV, with no server call. Order: live first and off-air last; inside
  each, the name starts with the query, then a word does, then it only contains it; then A–Z.
  Spoken numbers ("fox sports one") are read as digits.
- Remote: Right off the keyboard goes to the tiles and Left goes back. Search jumps to the first
  result. Click on a pick searches it, on a channel watches it, and the page is still there
  after the player. One Back closes the page from anywhere on it (v512). Compose maps an
  unclaimed Back to `FocusDirection.Exit`, which moved the focus off the keyboard card first, so
  it took two presses. The page now takes Back as a key and closes on release.
- Kids profiles search the Kids row only. Code: `LiveSearch.kt` (pure, `LiveSearchTest`) and
  `LiveSearchPage.kt`.

**Local News (API build h + TV v583, 2026-11-01):** the user asked for "local news networks to my
state".
- **Server:** a "Local News" row before News, made from the 88 local news stations in Vizio's free
  channel list (`build_local`). Each station carries the states it serves, worked out from its
  zip codes: 43 states plus DC. One station serves everyone (Local Now, flagged US). Only the
  master playlist is relayed; the variant playlists and chunks are plain HLS on Amagi and Wurl,
  which the TV fetches directly. They are not tied to an IP: a link fetched from the VPS played
  from a home line. The check is lighter than zlive's: the best variant must list timed
  segments. Its height gives the FHD/HD tag (`live:quality`). Off switch: `LIVE_LOCAL=0`.
- **TV:** the row starts with a state entry, "Choose your state" or the state already picked.
  Clicking it opens a state list in the channel list's place; the panel shows each state's
  station count and a few of its stations. The choice is kept on the TV, not per profile.
- **TV order:** working stations first (as in every row); within that, the state's own stations,
  then neighbours' stations that also serve it, then Local Now. Back cancels the list.
- **TV, elsewhere:** the player, search and Recently watched skip the state entry, and captions
  are off in the live player (these streams mark them as on by default). Code: `LocalNews.kt`
  (pure, `LocalNewsTest`), `LocalNewsViews.kt`.
- **Also in build h / v583, zlive's third chunk wrapping:**
  - Since about 12:55 UTC that day, zlive's chunks come from enterprise.content.tenovos.io.
  - Each one is a PNG whose IDAT holds the raw MPEG-TS, uncompressed.
  - Build g's check rejected it ("chunk is not MPEG-TS", 151 feeds), so the guide showed 78 of
    237 channels as working.
  - Server fix: `png_raw_ts`. TV fix: `ChunkUnwrap.rawTsInPng`. Relayed playlists now also make
    `URI="..."` attributes absolute (News 12's captions use a relative one).
  - Tests: 94/94 on the image.

## 1. What the viewer gets

- Sidebar → Live TV opens a channel page instead of "Feature still in development".
- 229 channels in 10 rows (from zlive's 253: 4 test/broken hidden, ~20 "Backup"
  copies folded into their main channel as automatic failover).
- Click a channel → "Tuning in…" → live picture in 1080p. Up/Down changes channel,
  Click opens the channel list, Back leaves.
- Kids profiles see only the Kids row (filtered on the TV).

| Row (draft, server-side table) | Channels | Examples |
|---|---|---|
| Recently watched | per profile | last 10 channels |
| Motorsport | 9 | Sky Sports F1, DAZN F1, MotoGP Channel |
| Football | 38 | Sky Sports Premier League, DAZN LaLiga, TUDN |
| US Sports | 33 | ESPN, Fox Sports 1, NFL Network, NBA TV |
| Sky Sports | 17 | Main Event, Action, Mix, Sky Sports+ |
| World Sports | 61 | beIN, DAZN, TNT Sports, Sony Sports |
| US Networks | 12 | ABC, CBS, NBC, Fox, CW, Telemundo |
| Entertainment | 32 | FX, TBS, USA Network, Discovery, BBC One |
| Movies & Premium | 20 | HBO, Showtime, STARZ, Sky Atlantic |
| News | 8 | CNN, Fox News, BBC News, Sky News |
| Kids | 19 | Disney Channel, Nickelodeon, Cartoon Network |

## 2. How it works

```
TV                         VPS (bluehive-live-api +                 zlive / TikTok
                                bluehive-live-browser)
──                         ─────────────────────                    ──────────────
GET /api/live/channels ──► cached channels.json + row table ◄────── iptv.zlive.st (every 15 min)
POST /api/live/{id}/session ► link cached? else the live browser:
                              real Chrome opens /embed/{id}, ──────► ticket → /resolve
                              keeps the signed m3u8 (~2.5 h)  ◄────── tims-001…/secure/<sig>/<exp>/…m3u8
ExoPlayer: GET /api/live/{id}/index.m3u8?t=<grant> every ~4 s
                           ► fetch the m3u8 FROM THE VPS IP ───────► (link only works from the IP that resolved it)
                           ◄ playlist, chunk URLs untouched
ExoPlayer: GET chunk ──────────────────────────────────────────────► p16-common-sign.tiktokcdn-us.com (any IP, no headers)
ChunkUnwrapDataSource turns the WebP / pixel-PNG chunk back into MPEG-TS for ExoPlayer
```

Only the ~1 KB playlist passes through the VPS. Video goes TikTok → TV directly.

## 3. Backend (VPS) — its own containers

**The user's rule: Live TV never runs in, edits, rebuilds or restarts the movie/TV
containers** (`bluehive-api`, both `bluehive-extractor`). Compose project `bluehive-live`
(`docker-compose.live.yml`), on the existing `streaming-backend_bluehive-net` network.

| Container | Built from | Runs | Shares with movie/TV |
|---|---|---|---|
| `bluehive-live-browser` | a PINNED copy of the extractor image + `live_browser.py` | its own Chrome + profile; no settings file, no secrets; 1.2 GB cap | nothing |
| `bluehive-live-api` | a PINNED copy of the API image + `live_app.py` + `bluehive_plugin/live.py` | `uvicorn live_app` (2 workers), never `TMDB_Api_Server`; 384 MB cap | reads `/opt/bluehive/.env` in place (login check) |

Shared on purpose: the Redis server (only `live:` keys + the `bh:play:` watching note),
Postgres (read, for the login check), and Caddy: +10 lines sending `/api/live/*` to
`bluehive-live-api` (graceful reload). `undeploy-live.sh` removes all of it.

**Browser — `POST /live/resolve {channel}`** (`live_browser.py`)
- VPS IP, no proxy: opens `https://zlive.st/embed/{channel}` in a new tab, waits for the
  `/resolve` answer (1.0-1.8 s), aborts the video chunks, closes the tab.
- Fallback capture: the first `.m3u8` the page's player requests.
- Also keeps the `/streams` answer it sees (live events) for the "On now" row.
- 2 tabs at a time, 15 s timeout, relaunches its Chrome if it dies.
- Channel id must match `^[A-Za-z0-9_-]{1,64}$`; only the zlive origin is ever opened.

**API — `bluehive_plugin/live.py`** (served by `live_app.py`; every answer carries
`X-BlueHive-Live: 1`)
| Route | Does |
|---|---|
| `GET /api/live/channels` | channels.json (fallback: public `embed/manifest.json`), cached 15 min; rows from the keyword table; backups folded; hidden list; per-channel `status` |
| `POST /api/live/{id}/session` | normal device auth; ensures a fresh link (waits for a resolve if needed); returns the playlist URL with a 6 h device-bound grant |
| `GET /api/live/{id}/index.m3u8?t=` | checks the grant; fetches the upstream playlist with a Chrome user agent; 2 s shared cache so 3 TVs on one channel = 1 upstream fetch; 403/404 or <10 min to expiry → re-resolve once (Redis lock: one browser load per channel, however many TVs ask); writes the slot list's "watching" note ("Sky Sports F1 (Live)") |

- **Decoy guard:** every new link has its first chunk checked once (unwrapped TS, ≥0.4 Mbps).
  The scraper decoy was 85 kbps, real channels 4-9 Mbps (ad breaks included).
- **Backups:** a failed resolve or decoy on "X" tries "X Backup" before giving up.
- **Dead channels (build c, live 2026-10-28 23:02 UTC):**
  - Every 30 min, one worker re-resolves each channel whose last try failed, least recently tried first, at most 24.
  - That pass also covers a dead channel's backup while the backup is untried.
  - Still dead → a fresh `off`, so it stays dimmed. Working → `ok`.
  - A channel shows `ok` while either of its feeds works, so ESPN plays from its backup and is no longer dimmed.
  - An unreachable upstream counts as an outage and moves on to the backup (it was a 500).
- **Second source, cdnlivetv.tv (build d, live 2026-10-29 08:20 UTC).** cdnlivetv is the player behind ntv.cx.
  - **How it works:**
    - The channel list is a public JSON API (405 channels, ~150 online).
    - Its player page carries the playlist URL, with a signed 4 h token, as base64 pieces, so plain HTTP reads it.
    - The token is not tied to an IP. Chunks are plain MPEG-TS from its own server and go straight to the TVs.
  - **Matching:** a zlive channel gets the online cdnlivetv channel of the same name and country.
    - The match ignores quality and country tags.
    - Unflagged US rows accept only a US channel; "Sky …" channels only a UK one.
    - Result: 52 of 240 matched, e.g. ESPN2 → "ESPN 2 FHD US", ESPN → "ESPN HD US".
  - **Tuning order:** zlive's feed → zlive's backup → cdnlivetv, with feeds the recheck found dead moved last. ESPN2 tunes straight to cdnlivetv.
  - **Guide:** these channels show "Backup feed ready". The TV app needed no change.
  - **Off switch:** `LIVE_CDN=0`.
- **One entry per channel (build e, live 2026-10-29 08:43 UTC).** zlive lists a few channels twice: its own feed, plus a copy it adds from the "TimStreams" restreamer (ids `auto-…`).
  - **Rule:** same name with country words dropped, and no conflicting country → one guide entry. zlive's own feed comes first and the copy becomes a fallback, tried after zlive's backup and before cdnlivetv.
  - **Merged:** CNN USA ← CNN, Sky Sports F1 FHD ← Sky Sports F1, Fox Sports 502 AU ← Fox Sports 502 (League). The guide has 237 entries.
  - **Kept apart:** the UK and US Boomerang and Cartoon Network feeds.
  - A TV that remembers a copy's id is tuned to the merged entry.
  - **Matching side effect:** a country word at the end of a name now counts as the channel's country. The four Sky Sport NZ channels gained NZ backups, and an Irish channel is not backed by a UK one.
- **Every channel checked, slowly (build f, live 2026-10-29 10:20 UTC).**
  - Why: a census of all 237 channels on a throwaway stack (`own/census`) found 164 working and 73 dead, and every dead one looked fine until picked.
  - Every 30 s, one worker tries the channel checked longest ago, the way a tune would; the whole guide is covered about every 2 h. A dead channel is dimmed, never hidden.
  - After 14 h: 1,670 steps, 0 errors, 168 working, 69 dimmed.
  - Off switch: `LIVE_SWEEP=0`.
- **Working channels first (build g, live 2026-10-30 01:23 UTC).** Each row lists working channels, then unchecked, then dimmed ones, each group in zlive's order.
  - Football opens with its 13 working channels instead of mixing them among its 24 dead ones.
  - Channel numbers and Up/Down zapping follow this order.
- Cost: one Chrome tab per channel per ~2.3 h of viewing, plus a playlist every ~4 s, plus one tab per dead channel per 30 min.

## 4. TV app (new `livetv/` package)

| File | Does |
|---|---|
| `LiveTvScreenActivity.kt` | replaces the coming-soon activity (sidebar call unchanged) |
| `LiveTvScreen.kt`, `LiveTvStyle.kt` | the chosen design; mockup px → dp with one factor |
| `LiveTvData.kt` | channel list, rows, recently watched (per profile, local) |
| `LivePlayerActivity.kt` | ExoPlayer for live: plays ~3 chunks behind live; banner 4 s; Up/Down = channel, Click = list, Back = exit; backup switch; off-the-air card |
| `ChunkUnwrapDataSource.kt` | wraps the Cronet data source; reads the whole chunk (2-4 MB), then by content: RIFF/WEBP → the RIFF chunk holding MPEG-TS; PNG → inflate IDAT, undo the row filters, pixels = `TIKTIKPX` + u32 BE size + gzip → gunzip; TS passes through; resumes at an offset by re-reading from 0 |
| `ApiService.kt` | +2 calls (channels, session) |

- **Still watching?** Live never ends, so IdleWatcher's playback exemption would hold the TV's
  slot forever (a documented gap). After 4 h with no button press: "Still watching?"
  with a 2-minute countdown, then stop and go back to the channel page.
- **Untouched:** HlsPlayerActivity, PlayerIntents, ShelfStack, TvWideLazyRow, GoodCopyCountdown,
  MediaItem (they have uncommitted changes in this checkout).
- **Gates:** `livetv/` joins the 2,500/100 method budget; JVM tests for the unwrapper
  (synthetic WebP+TS with odd padding, pixel-PNG with every row filter, offsets,
  passthrough) and the row grouping.

## 4a. Design C spec (from the approved mockup)

Mockup frame 680 × 382 px = the TV's 960 × 540 dp, so **dp = px × 1.412** (one factor
for everything). Fonts: Asap Condensed Bold (names, headings), Inter (meta, chips),
Passion One (card buttons). Background = the home screen's `home_screen` art.

| Element | Mockup px | dp | Notes |
|---|---|---|---|
| Category chips row | left 16, top 14, height 22, gap 6, pad 0 11, font 12 | 22.6, 19.8, 31, 8.5, 0 15.5, 17 sp | selected `#2644A6` white 600; others `#222340` text `#CFD3DC`; radius 6 → 8.5 |
| Channel list | left 16, top 50, width 332 | 22.6, 70.6, 468.8 | rows 28 high, gap 2, pad 0 10, radius 6 |
| List row content | number col 34 (11 px), flag col 30, name 14 Asap, FHD chip, red dot 7 | 48, 42.4, 19.8 sp, —, 9.9 | idle row `rgba(18,20,27,.85)`; focused `#2644A6` + 2 px white ring |
| Detail panel | left 362, top 50, 302 × 250, radius 10 | 511, 70.6, 426 × 353, 14 | `rgba(20,22,30,.92)`, border 1 `#2A2E3B`, top band 4 in the row colour |
| Panel text | name 26 Asap; meta 12; "Live now" 12 `#FF8A8A`; backup line 11 `#9AA1B2` | 36.7 sp; 17; 17; 15.5 | margins 14/16 → 20/22.6 |
| Hint bar | left 362, top 316, 302 × 26, gold border 1.5 `#C9A227` | 511, 446, 426 × 36.7, 2.1 | "▲▼ channels · ‹ › categories · ↩ back", 11 → 15.5 sp |
| Chips (flag, FHD) | 11 px, pad 0 5, radius 4, `#2A2E3B` / `#D7DBE4` | 15.5 sp | |

Row colours (detail band): Motorsport `#D64545`, Football `#2E9E5B`, US Sports `#2F6FE0`,
Sky Sports `#6E3FD6`, World Sports `#1E9AA6`, US Networks `#C7962E`, Entertainment
`#D06A2A`, Movies & Premium `#8E44AD`, News `#D9A21B`, Kids `#E0559A`.

Remote: Up/Down = channel list, Left/Right = category chips, Click = watch, Back = home.

## 4b. Stage 1 results (2026-10-28, throwaway containers, real channels)

| Measured | Result |
|---|---|
| Resolve (real Chrome, warm) | 1.0-1.8 s per channel; session cold ~2 s |
| WebP channels (Sky Sports F1, Main Event, Disney, HBO, Nickelodeon) | 1080p/720p H.264 + AAC, 3-10 Mbps |
| PNG "artemis" channels (ESPN Backup) | 720p H.264 + AAC, ~3 Mbps; pixels = `TIKTIKPX` + size + gzip(TS) |
| Dead at the source (CNN, ESPN, Fox News that day) | zlive listed TikTok chunks signed 8 days earlier (403); zlive's own player failed too → shown as `off` |
| 3 TVs, one cold channel | one browser load |
| Dead link / near expiry | replaced in the same request / renewed in the background |

- `/api/live/channels` gives each channel `status`: `ok` / `off` / `unknown` (last resolve
  within 6 h). The TV dims `off` channels: grey dot `#6B7080`, name `#7D8496`.
- Kids profiles: the TV shows only the Kids row (the server does not enforce it).
- No sweep of every channel (it would cost a browser load per channel), so status comes from
  real viewing. Since build c, channels that failed are retried every 30 min: dead ones stay
  dimmed and light up on their own when zlive fixes them.
- **Dead at the source, 2026-10-28** (zlive's own player failed the same way from a home IP):
  - Fox News: frozen since 2026-10-20 12:00 UTC.
  - ESPN and ESPN2: frozen since 2026-10-25 14:00 UTC.
  - "FOX SPORTS NEWS" (id `foxnews`): one 35.6 s 1080p MP4 sent again on every request.
  - A frozen feed means the playlist stopped advancing and its TikTok chunk links have expired, so the CDN refuses them for everyone.

## 5. Stages

| Stage | What | Test | Who ships it |
|---|---|---|---|
| 1 · VPS | `bluehive-live-browser` + `bluehive-live-api` (own containers) | **done: 45/45** on the exact images in `/root/bh-live-tv-20261028/own` (`run_test_live.sh`): see §4b | you run `deploy-live.sh --go` (no movie/TV container restarts) |
| 2 · TV | `livetv/` package, player, unwrapper, still-watching | **built v484**: unit tests + all gates; installed on the onn box; round 1 = you test browse, tune, zap, Back, a 30-min watch | onn box only, not published |
| 3 · Polish | "On now" events row, round-1 fixes | round 2 on the onn box | onn box only |

## 6. Risks

| If zlive… | Then | Fix |
|---|---|---|
| changes its page or API names | resolve fails → off-the-air card | update the capture; the .m3u8 fallback covers many changes |
| moves chunks off TikTok or changes the wrapper | black screen on that channel | unwrapper is generic; else a TV fix |
| starts blocking datacenter IPs | decoy or 403 | resolve + playlist through ONE sticky residential exit (playlist-only traffic) |
| serves us the decoy | caught by the bitrate check | re-resolve, backup, then off-the-air (never the slate) |
| drops a channel mid-match | stall | auto backup, then off-the-air card |

## 7. Your calls

1. Browse design: **C (chosen 2026-10-28).**
2. Row order and names: the table in §1 is the default and is one server-side list, easy to change later.
3. Kids profiles see only the Kids row: default yes.
