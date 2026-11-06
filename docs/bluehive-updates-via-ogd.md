# BlueHive updates installed by Off-Grid Drive — architecture

*Draft, 2026-11-06 (rev 2: downloads come from GitHub). Diagram: `docs/bluehive-updates-via-ogd.png`.*

## Goal

A viewer who installs BlueHive from Off-Grid Drive sees **no permission screens in BlueHive at all**, updates install **without Android's "Install this update?" question**, and BlueHive **reopens by itself** afterwards.

Today BlueHive needs two switches of its own ("Install unknown apps", required; "Display over other apps", optional) because it installs its own updates. Off-Grid Drive already holds the install switch and is BlueHive's installer of record, so it takes the job over.

## The idea in one line

BlueHive still **decides** when to update (its own server, its own rules, mandatory updates); Off-Grid Drive only **downloads from GitHub and installs**, then reopens BlueHive while its own update screen is on top.

## Servers: nothing changes, nothing connects

- **Neither backend is changed.** No new endpoints, tables or settings on either server.
- **The two servers never talk to each other**, and nothing is shared between them.
- The only new link is **on the TV**, app to app: BlueHive hands the job to the Off-Grid Drive app.
- Off-Grid Drive downloads from **GitHub only** (where it already installs add-ons from). It never contacts BlueHive's update server.

## Who does what

| | BlueHive (add-on) | Off-Grid Drive (host) | GitHub |
|---|---|---|---|
| Finds the update | ✔ `UpdateGate` + `updates.daigurrenblue.com/bluehive/version` (unchanged) | – | – |
| Decides optional / mandatory | ✔ `min_supported` (unchanged) | – | – |
| Shows the offer ("Update now?") | ✔ `SelfUpdateActivity` (unchanged) | – | – |
| Hosts the file | – | – | ✔ release `v659`, `bluehive-universal.apk` |
| Downloads, verifies, installs | only as a fallback | ✔ new `AddonUpdateActivity` | – |
| Reopens BlueHive | only as a fallback (`PackageReplacedReceiver`) | ✔ straight after the install, from the foreground | – |
| Holds "Install unknown apps" | not needed any more | ✔ already does (OGD setup step 1) | – |

## The flow

1. Viewer opens BlueHive. Off-Grid Drive does **not** need to be running — Android starts it on demand, even after a reboot.
2. `UpdateGate` finds a newer version on BlueHive's own server; viewer presses **Update** (or it's mandatory).
3. BlueHive asks: *is there a host that can install for me?* (`HostUpdater.available()`):
   - the app that installed BlueHive (`getInstallSourceInfo().installingPackageName`) answers the action `io.host.addon.action.INSTALL_UPDATE`;
   - **Yes →** `startActivityForResult` to that package with only **`version_code`**, **`sha256`** and **`mandatory`**. No download link: BlueHive can't point Off-Grid Drive anywhere.
   - **No →** today's self-update path (unchanged).
4. Off-Grid Drive's `AddonUpdateActivity` opens **in its own task** (separate `taskAffinity`, excluded from recents — the dashboard never appears):
   - checks the caller: `getCallingPackage()` must equal the package being updated, and must be an add-on Off-Grid Drive installed;
   - looks up **where it installed that add-on from** (its own record: GitHub owner/repo);
   - finds release **`v{version_code}`** on that repo (one GitHub call per update, far below GitHub's 60-an-hour limit) and downloads its `.apk` from GitHub's download network, showing "Updating *‹app name from PackageManager›*…" with progress;
   - verifies **sha256** = the value BlueHive passed, then the APK itself: same package name, **same signing certificate** as the installed copy (`ApkSignatureVerifier`), `versionCode` = the one asked for;
   - installs with `USER_ACTION_NOT_REQUIRED` (no question — it's the installer of record) and, on Android 14, `setRequestUpdateOwnership(true)`.
5. Android stops BlueHive and replaces it. Off-Grid Drive's screen is still on top.
6. Install succeeded → Off-Grid Drive starts BlueHive's launch intent. **Allowed**, because Off-Grid Drive is the foreground app (no "Display over other apps" needed), then `finishAndRemoveTask()`.
7. Viewer is back in BlueHive on the new version (~2–5 s after the download).

## Why GitHub

| | BlueHive update server | GitHub |
|---|---|---|
| Speed | one VPS | global download network |
| Load at 2,000 TVs (~19 MB each) | ~38 GB per release from the VPS that also relays video | none on your servers |
| Cost | VPS bandwidth | free |
| Separation | Off-Grid Drive would reach into BlueHive's server | Off-Grid Drive only uses GitHub, as it already does |

## Rules that keep it safe

- **Only an add-on can update itself.** The caller must be the package being updated (`getCallingPackage()`), so one app can never make Off-Grid Drive install another.
- **Off-Grid Drive chooses the source**, from its own record of where the add-on came from. BlueHive only says *which version* and *which fingerprint*.
- **Android's own guarantee:** an update signed by a different key is refused by the system. Off-Grid Drive checks it first anyway so it can explain the failure.
- **sha256 must match** what BlueHive's server published; the version must be exactly the one requested.
- **Off-Grid Drive never names BlueHive.** The action, extras and screen are generic ("io.host.addon…", app name read at runtime), the same pattern as BlueHive's `io.host.entitlement.ASSERTION`. Any future add-on gets this for free.
- The update screen **bypasses** Off-Grid Drive's setup, pairing and subscription gates: updating already-installed software must always work.

## What the release process must keep doing

- The GitHub repo (`btvhive/BlueHive`) stays **public** — Off-Grid Drive already requires that.
- Release tags stay **`v` + version number** (v656, v657, v658…).
- `publish-release.ps1` publishes to **GitHub first**, then switches BlueHive's server (it already does), so a TV is never offered a version GitHub doesn't have yet.

## Fallbacks (nothing gets stuck)

| Situation | What happens |
|---|---|
| BlueHive not installed by Off-Grid Drive (adb, Downloader, old copy) | BlueHive self-updates from its own server as today, asking for its own switch at that moment |
| Off-Grid Drive uninstalled, or too old to have the update screen | same — self-update fallback |
| GitHub unreachable, release `v{N}` missing, or sha256 mismatch | Off-Grid Drive returns "unavailable"; BlueHive falls back to its own server |
| Off-Grid Drive is installing **its own** update | the BlueHive request waits in Off-Grid Drive's install queue, then runs |
| Storage / signature / install failure | Off-Grid Drive shows the reason (existing `InstallProblems`) with **Try again** and **Open ‹app›** (old version); for a mandatory update BlueHive's gate re-offers on next open |
| Viewer presses Back mid-download | cancelled, BlueHive reopened on the old version |
| Fire TV (Android 7–9) | same flow; reopen works there too |

## Changes needed

**Off-Grid Drive (1.63)**
- New exported `AddonUpdateActivity` (generic, own task) + intent filter `io.host.addon.action.INSTALL_UPDATE`.
- Release lookup by tag (`GithubReleaseClient`), download, then reuse `AddonInstaller.install()` session code, `ApkSignatureVerifier`, `InstallProblems`; add `setRequestUpdateOwnership(true)` on Android 14.
- One install queue shared with `OgdUpdateManager`, so two installs never overlap.
- Relaunch the add-on after `STATUS_SUCCESS`; result codes back to the add-on (`ok`, `unavailable`, `failed`, `cancelled`).

**BlueHive (659)**
- `HostUpdater`: detect the host (installer of record + resolvable action), hand over `version_code` + `sha256`, and fall back to self-update on `unavailable`/`failed`.
- **Stop claiming update ownership of itself** (`setRequestUpdateOwnership(true)` in `UpdateManager`), or Off-Grid Drive's installs would trigger the question again. On Android 14, a TV where BlueHive already owns itself calls `relinquishUpdateOwnership()` once at start-up when a host exists.
- `SetupSteps`: both steps are skipped when a host updater exists → OGD-installed BlueHive shows **no setup screens**.
- Keep the self-update path, `SetupSteps` and `PackageReplacedReceiver` as the fallback.

**Order:** publish Off-Grid Drive first, then BlueHive. Old Off-Grid Drive + new BlueHive → BlueHive just falls back.

## The first update after this ships

TVs whose BlueHive already updated itself on Android 14 are "owned by BlueHive". BlueHive 659 hands ownership back at start-up, so from 660 on Off-Grid Drive installs silently. On the very first handover a TV may still see one "Install this update?" question.

## Testing (onn boxes .173 and .74)

1. Fresh path: Off-Grid Drive 1.63 → install BlueHive 659 from Add-ons → BlueHive opens with **no setup screens**.
2. Update path without publishing: a test build of BlueHive pointed at a staging manifest offering 660, with Off-Grid Drive's test build reading a **test repo** on GitHub → **Update** → Off-Grid Drive screen → BlueHive back on 660, **no question**. Repeat after a reboot without opening Off-Grid Drive first.
3. Fallbacks: BlueHive installed by adb; GitHub release missing; wrong sha256 → BlueHive updates itself from its own server.
4. Failures: different signing key, no network, Back mid-download.
5. Overlap: trigger Off-Grid Drive's own update and a BlueHive update together.

## Effort

About 1 day Off-Grid Drive, 1 day BlueHive, ½–1 day testing on both boxes.
