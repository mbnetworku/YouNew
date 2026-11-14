# QUEUE-BACKPRESSURE.md — Queue Backpressure & Overload Protection Audit

| | |
|---|---|
| Project | YouTv Plus (app id `com.younew.tv`, fork of SmartTube), version 33.15 |
| Audited revision | git `ac37965` (branch `claude/vigilant-cray-jhfa3t`) |
| Type of audit | Read-only code inspection. **No test was run, no load was generated, no source file was changed.** |
| Evidence labels | **Confirmed** = read in the source and quoted with file and line. **Potential** = the code allows it, but the trigger conditions were not reproduced. **Unknown** = not enough evidence. |

Line numbers are those of revision `ac37965`.

---

## 1. Executive Summary

This project is an **Android client application** (Android 5.0+ TV box, phone, tablet). It has **no server, no message broker, no database server and no durable job queue**. Because of this, the classic backpressure questions (broker queue depth, consumer lag, dead-letter queues) do not apply. What does apply:

- the **in-process work queue** of the RxJava thread pool that runs every network call (`RxHelper`),
- the **Android main-thread message queue** (`Handler.postDelayed`) used for timed reloads,
- **single-flight guards** (one request of a kind at a time) in several features,
- **retry loops** around playback, and
- **one global lock** around the "video data" request.

Overall result:

| Area | Result |
|---|---|
| Pool of background threads | **Bounded** (4–8 threads plus a bounded wait queue of 8–16 tasks). Overflow is handled by `CallerRunsPolicy`, which can run work on the calling (often the UI) thread. |
| Admission control (rejecting or delaying new work) | Per feature, by "is one already running" flags. No global admission control and no client-side request-rate limit. |
| Retries | Network errors are retried by the player error handler **with a fixed 1 s delay and no attempt limit** for most error types (finding C-01). |
| Video-data requests | **Serialized by one global lock**, and the in-flight HTTP call is **not cancelled** when its caller gives up (finding C-02). |
| Observability | **None** for queues or throughput. Only log lines. |

Counts: **6 confirmed findings, 5 potential risks** (sections 11 and 12). No Critical finding. Two Medium confirmed findings (C-01, C-02) and one Medium potential risk (P-01).

---

## 2. Project Architecture Overview

| Item | What was found | Evidence |
|---|---|---|
| Languages | Java and Kotlin (about 2,500 source files in the repository, including vendored libraries) | `find` over `*.java`, `*.kt` |
| Platform | Android, `minSdk 21` (legacy build) / `26` (modern build), single flavor `stfdroid` | `smarttubetv/build.gradle` (`productFlavors`, `modernBuild`) |
| UI | Android Leanback (`BrowseSupportFragment`, `VerticalGridView`), presenters in `common/` | `common/src/main/java/.../app/presenters/` |
| Async model | RxJava 2 (`Observable`, `Disposable`), wrapped by `RxHelper` | `SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/rx/RxHelper.java` |
| HTTP | OkHttp 3 + Retrofit, **synchronous `execute()`** on the Rx pool thread | `MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/googlecommon/common/helpers/RetrofitHelper.java:118` |
| Player | ExoPlayer 2.10.6 fork (DASH, SABR) | `exoplayer-amzn-2.10.6/`, `common/.../exoplayer/other/ExoPlayerInitializer.java` |
| Images | Glide, 10 MB disk cache | `smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/util/GlideCachingModule.java` (`CACHE_SIZE`) |
| Persistent / scheduled work | AndroidX WorkManager periodic backup workers | `common/.../misc/GDriveBackupWorker.java`, `LocalDriveBackupWorker.java` |
| Local storage | `SharedPreferences` and files only (no database server) | `MediaServiceData.java` (`persistData`) |
| Remote server | YouTube's public web APIs (youtubei, lounge, googleapis). The app is a **consumer**, not a server. | `RetrofitOkHttpHelper.kt` (`apiPrefixes`) |
| Telemetry | Crashlytics plugin only for the `stbeta` and `ststable` flavors and only if `google-services.json` exists. The shipped flavor `stfdroid` has none. | `smarttubetv/build.gradle:39-42` |

Note on duplicated source: the shared library code exists twice (`SharedModules/` and `MediaServiceCore/SharedModules/`). `settings.gradle` includes the root copy (`./SharedModules`), and `diff -rq` between the two copies reported no difference at audit time. This audit cites the root copy (finding C-06).

---

## 3. Queue and Workload Discovery

| # | Workload | Entry point | Sync / async | Can work accumulate? | Buffer or guard | If capacity is exhausted |
|---|---|---|---|---|---|---|
| W1 | Browse, search, channel, "next" API calls | Presenters → `RxHelper.create/fromCallable` | Async on Rx pool (`RxHelper.java:359-365`) | Yes, until the pool queue is full | Bounded pool (see section 6) | `CallerRunsPolicy`: runs on the caller thread (P-01) |
| W2 | Video data for playback (`getFormatInfo`) | `VideoLoaderController.loadFormatInfo` (`:248`) | Async, but **global lock** | Yes, threads wait for the lock | `FormatInfoWrapper.kt:36` `synchronized(lock)` | Waits behind the running request (C-02) |
| W3 | Video data prefetch while a card has focus | `VideoPrefetcher.onCardSelected` → `MediaServiceManager.prefetchFormatInfo` | Async | One at a time (newer disposes older) | `RxHelper.disposeActions(mPrefetchFormatInfoAction)` (`MediaServiceManager.java:273`) | Older call is disposed but its HTTP call keeps running (C-02) |
| W4 | Endless Home rows | `BrowsePresenter.onRowsEnd` (`:542`) | Async | No (single-flight) | `mLoadingMoreRows` flag (`:543`) | Ignores further requests until done |
| W5 | Playback retry / reload after errors | `ErrorFixerController.applyEngineErrorAction` (`:225`) | Timer (`Utils.postDelayed`) | No queue, but repeats forever (C-01) | `loadFormatInfo` disposes the previous request (`VideoLoaderController.java:248-266`) | Keeps retrying every ~1 s |
| W6 | Media chunk download | ExoPlayer `Loader` | Async (ExoPlayer thread) | No: bounded by buffer settings | `DefaultLoadControl` durations (`ExoPlayerInitializer.java:110-146`) | Stops loading when the buffer is full |
| W7 | App self-update check and download | `AppUpdater` | Async, own thread | No | `sBusy` flag (`AppUpdater.java:59, 199-202, 357-360`) | A second request is ignored |
| W8 | Remote control ("cast") listener | `LoungeService.startListening` (`:100`) | Long-running loop on its own thread | One loop | `while (true)` with `Thread.sleep(3_000)` on the normal path (`:106`) | See P-03 |
| W9 | Live chat polling | `LiveChatServiceInt.kt` | Own thread, `Thread.sleep(5_000)` between polls | One loop per chat | sleep | Bounded by the sleep |
| W10 | SponsorBlock skip polling | `SponsorBlockController` (`POLL_INTERVAL_MS = 1_000`, `:34`; `RxHelper.interval`, `:208`) | Timer | One per video | Disposed with the player | See P-04 |
| W11 | PoToken (JavaScript in V8) interval timers | `PoTokenV8.kt:97-110` → `RxHelper.startInterval` | Timer | Per JS `setInterval` | Disposable map | See P-04 and section 14 |
| W12 | Cloud / local backup | WorkManager periodic work | Scheduled by the OS | No (unique work name) | `enqueueUniquePeriodicWork` | WorkManager default retry backoff (not overridden) |
| W13 | OAuth device sign-in polling | `OAuth2Service.java:16-17, 65-66` | Own thread | One | 200 attempts × 5 s | Stops after about 16.7 minutes |
| W14 | Thumbnails | Glide (its own executors) | Async | Glide manages | 10 MB disk cache | Not inspected further (section 14) |

No payments, e-mail, uploads, webhooks, server-side scheduled jobs, AI/LLM calls or database operations exist in this project.

---

## 4. Producer–Consumer Flow

```
User input (key / touch / mouse)          Timers (Handler.postDelayed, Rx interval)
        │                                              │
        ▼                                              ▼
  Presenters (main thread) ───── subscribe() ───► RxHelper.setup():
                                                   subscribeOn(cached pool)  ── bounded pool, queue, CallerRuns
                                                   observeOn(main thread)
                                                         │
                                                         ▼
                                        Retrofit/OkHttp  Call.execute()   (blocking, 20 s timeouts)
                                                         │
                                           video data: FormatInfoWrapper.getFormatInfo
                                                   synchronized(lock)  ← one at a time
                                                   up to 10 clients tried one after the other
                                                         │
                                                         ▼
                                           ExoPlayer Loader → media chunks (buffer-limited)
                                                         │
                       error ──► ErrorFixerController ──► reloadVideo / restartEngine (1 s delay) ─┐
                                                         ▲                                         │
                                                         └─────────────────────────────────────────┘
```

Producers are the user (bounded by human speed) and timers/retries (not bounded by anything except fixed delays). Consumers are the Rx pool threads and the single ExoPlayer loader. There is no persistent queue: when the app is closed, pending work is lost, and nothing is replayed at start-up.

---

## 5. Backpressure Implementation Analysis

| Mechanism | Present? | Where / how | Assessment |
|---|---|---|---|
| Bounded work queue | **Yes** | `RxHelper.getCachedScheduler()` (`RxHelper.java:37-61`): `LinkedBlockingQueue<>(queueCapacity)` | Good. |
| Overflow policy | **Yes** | `ThreadPoolExecutor.CallerRunsPolicy` (`RxHelper.java:55`) | Slows the producer, but the producer is often the UI thread (P-01). |
| Maximum pending jobs per feature | Partly | Flags such as `mLoadingMoreRows` (`BrowsePresenter.java:79, 543`), `sBusy` (`AppUpdater.java:59`), disposing the previous action (`RxHelper.disposeActions`) | Good where used. Not global. |
| Global admission control | **No** | — | Not needed for a single-user client unless retries run away (C-01). |
| Client-side request rate limit | **Disabled** | `RateLimitInterceptor` exists (sleeps 1 s after HTTP 429), but its registration is commented out: `OkHttpCommons.java:293` (`//enableRateLimiter(okBuilder)`) | See C-05. |
| Load shedding | Partly | Prefetch only runs after 600 ms of rest on a card (`VideoPrefetcher.java`, `REST_DELAY_MS`); a newer card cancels the older wait. | Good. |
| Flow control for the media download | **Yes** | ExoPlayer `DefaultLoadControl` buffer limits (`ExoPlayerInitializer.java:110-146`); `mMaxBufferBytes = deviceRam / 18` only for `BUFFER_HIGHEST` (`:48`, `:125`) | Good. For the other buffer modes ExoPlayer defaults apply (not inspected, section 14). |
| Processing deadlines | Partly | OkHttp connect/read/write 20 s each (`OkHttpCommons.java:43-45, 94-96`). **No `callTimeout`.** No time limit on a whole Rx task. | See C-04. |
| Overload response to the user | Partly | Error messages through `MessageHelpers` in `ErrorFixerController`. No "too busy" state. | Acceptable for a client. |

---

## 6. Queue Capacity and Concurrency

### 6.1 The cached Rx scheduler (`RxHelper.getCachedScheduler`, `SharedModules/.../rx/RxHelper.java:37-61`)

```
cores           = Runtime.getRuntime().availableProcessors()
corePoolSize    = max(2, min(4, cores))
maxPoolSize     = max(4, min(8, cores * 2))
queueCapacity   = corePoolSize * 4
keepAlive       = 60 s, allowCoreThreadTimeOut(true)
rejection       = CallerRunsPolicy
```

Computed from those formulas (not measured on a device):

| CPU cores | Core threads | Max threads | Queue | Tasks outstanding before the first rejection |
|---|---|---|---|---|
| 1–2 | 2 | 4 | 8 | 12 |
| 4 | 4 | 8 | 16 | 24 |
| 8 or more | 4 | 8 | 16 | 24 |

`ThreadPoolExecutor` starts threads above the core size only when the queue is full, so the maximum is reached only under load. The source comment says this is deliberate.

### 6.2 Other executors and threads

| Mechanism | Bound | Used by | Evidence |
|---|---|---|---|
| `Schedulers.io()` (RxJava's cached pool, unbounded) | **None** | `RxHelper.runAsync`, `runAsyncUser` | `RxHelper.java:168-198` |
| `Schedulers.newThread()` | **None** (one new thread per subscription) | `RxHelper.createLong` and `RxHelper.interval` (`:371-378`) | `RxHelper.java:342, 371` |
| OkHttp `Dispatcher` | Default (not changed; only async calls are limited) | Not used for the app's calls, which use `execute()` | `RetrofitHelper.java:118` |
| OkHttp connection pool | 20 idle connections, 5 minutes | Shared client | `OkHttpCommons.java:104` |

Users of `createLong`/`interval` (all long-lived): `YouTubeLiveChatService.java:27`, `YouTubeRemoteControlService.java:40`, `YouTubeAccountManager.java:77`, `GoogleSignInService.java:34`, `SponsorBlockController.java:208`, `PoTokenV8.kt:101`.

### 6.3 Concurrency of video-data requests

`FormatInfoWrapper.getFormatInfo` wraps the work in `synchronized(lock)` (`FormatInfoWrapper.kt:36`), so **at most one video-data request runs at a time in the whole app**, whoever asked (player, menu, prefetch). Inside it, `VideoInfoService.firstInfoWith` tries the clients of `CLIENT_LIST` one after the other (`VideoInfoService.java:33-48, 118-132`). There are **10 active entries** in `CLIENT_LIST`, and `firstPlayable` (`:108-112`) runs the loop a second time if the first pass finds nothing playable. Upper bound from constants only: 2 × 10 = **20 sequential requests** for one video, each limited to 20 s by the OkHttp timeouts. This is arithmetic, not an observed behaviour.

---

## 7. Retry and Failure Handling

| Item | Finding | Evidence |
|---|---|---|
| Player error → reload | Every branch ends in `restartEngine()` or `reloadVideo()`, both with a **fixed 1 s delay** | `ErrorFixerController.java:320-325`; `VideoLoaderController.java:395-400` (`restartEngine()`, `reloadVideo()` call the `…(1_000)` variants) |
| Attempt limit | Only the SABR "reload player" and "backoff" paths count attempts (`MAX_SABR_ERROR_FIX_ATTEMPTS = 3`, `ErrorFixerController.java:27, 178`), and after 3 attempts they **fall through to the generic path**, which retries again | `ErrorFixerController.java:176-183, 225-326` |
| Backoff / jitter | **None** for the generic path (fixed delay). SABR server-requested backoff is honoured (`applySabrBackoffAction`). | same |
| HTTP 403 | `BufferingDetector` decides: if nothing played yet → `switchNextClientNow()`; otherwise `switchNextClient()` (which first tries to reset the PoToken cache) | `ErrorFixerController.java:276-283`; `VideoInfoService.java:135-144` |
| HTTP 429 / 500 | Disables subtitles or high-bitrate formats first, then falls through to the same reload | `ErrorFixerController.java:271-275` |
| In-flight guard | `loadFormatInfo` calls `disposeActions()` before a new request, so a reload does not stack requests | `VideoLoaderController.java:248-266` |
| Remote-control loop | After a **normal** return: sleep 3 s. After a **generic exception**: the `catch (Exception e)` only logs and loops again **without sleeping** | `LoungeService.java:100-124` (P-03) |
| Dead-letter / failed-job retention | **Not applicable** (no queue) | — |
| Idempotency / duplicates | API calls are reads, except the playback "history" ping; a new `resetClientPlaybackNonce` is generated for every video info (`VideoInfoService.java`, `getVideoInfo`). Duplicates were not analysed further. | section 14 |
| Poison item | An unplayable video is reloaded again and again (C-01). In "formatInfo is unplayable" case the next video is started after 5 s (`VideoLoaderController.java`, `loadNextVideo(5_000)`) | `VideoLoaderController.java:286-299` |
| Worker crash recovery | WorkManager workers return `Result.retry()` (`GDriveBackupWorker.java:105`, `LocalDriveBackupWorker.java:72`); the backoff is WorkManager's default (no `setBackoffCriteria` anywhere in `common/src/main`) | grep |
| Self-update | One download at a time (`sBusy`); failure removes the partial file and shows a message | `AppUpdater.java:357-396` |

A prior observation recorded in `docs/AI_MEMORY.md` ("THIRD SHORTS RESULT"): on the API 26 emulator, which cannot decode video, the app log showed only OkHttp lines repeating every 1–2 s. This matches the C-01 pattern but was an emulator observation, not a real-device measurement.

---

## 8. Resource Protection

| Resource | Protection found | Gap |
|---|---|---|
| Threads | Bounded Rx pool (6.1). | `Schedulers.io()` and `newThread()` are unbounded (6.2). |
| Memory – media buffer | `DefaultLoadControl` durations 5–100 s depending on the buffer setting; byte cap `deviceRam / 18` for the highest setting | Default byte target for other settings not inspected. |
| Memory – images | Glide manages its memory cache; disk cache 10 MB | — |
| Memory – lists | Each list has pagination; Home rows are appended on scroll | **Endless Home has no cap on rows kept in memory** (P-02). |
| Connections | OkHttp pool of 20 idle connections | — |
| Time | 20 s connect/read/write timeout | No call timeout (C-04). Lounge uses 60 s read timeout on purpose (`LoungeService.java:176`). |
| Isolation between workloads | Video data requests share one lock (C-02). Everything else shares the 4–8 thread pool (a slow API call can occupy a thread the Home feed needs). Long-lived loops use their own threads. | Partial isolation only. |
| Cleanup after failure | Download file deleted on failure (`AppUpdater.java`, `download`); Rx errors are caught by a global error handler (`RxHelper.setupGlobalErrorHandler`) | — |
| External API quotas | Not tracked. HTTP 429 is handled only for subtitles / formats. No global quota logic. | C-05. |
| Database connections | Not applicable | — |

---

## 9. Observability and Monitoring

| Metric | Exists? | Where |
|---|---|---|
| Queue depth / pending jobs | **No** | The executor in `RxHelper` is private and unmonitored |
| Queue waiting time | No | — |
| Job duration | No (only log lines in debug builds: `HttpLoggingInterceptor` BODY and the OkHttp profiler are added only when `BuildConfig.DEBUG`, `OkHttpCommons.java:320-340`) | — |
| Worker utilization | No | — |
| Failed / retry counts | No (one counter: `mSabrErrorFixAttempts`, used only for logic) | `ErrorFixerController.java` |
| Rejected requests | No | — |
| Alerts / dashboards | No | — |
| Crash reporting | Only for beta/stable flavors with `google-services.json`; `Thread.setDefaultUncaughtExceptionHandler` in `MainApplication.java:148` | `smarttubetv/build.gradle:39-42` |
| What is available | `Log` lines and the CI emulator log (`logcat.txt`) read by `.github/scripts/smoke-test.sh` | — |

For an offline client app this is normal, but it means nobody can see retry loops or saturation on user devices (C-03).

---

## 10. Real-World Failure Scenarios

All results come from reading the code. **None of these scenarios was tested.** "Client" wording is used because there is no server.

| Scenario | Classification | Reasoning (evidence) |
|---|---|---|
| **A. 10× burst of user actions** (very fast scrolling, rapid key presses, many cards focused) | **Partially protected** | Prefetch is debounced (600 ms) and single-flight; Home loading is single-flight; the Rx pool is bounded. But the overflow policy can run work on the UI thread (P-01). |
| **B. Background threads slow down** (network is slow) | **Partially protected** | Pool and queue are bounded (12–24 tasks). Beyond that, the caller runs the task (P-01). Calls time out after 20 s per phase. |
| **C. All background threads unavailable / blocked** | **Partially protected** | New work queues up to 8–16 tasks, then runs on the caller. Nothing grows without limit in memory, but the UI can stall. |
| **D. YouTube API slow** | **Partially protected** | 20 s timeouts; one video-data request at a time. However the lock plus up to 10–20 sequential client attempts means one slow video can delay all others (C-02), and slow responses are retried every 1 s (C-01). |
| **E. Database connection exhaustion** | **Not applicable** | No database server. Local `SharedPreferences` writes happen on each `persistData()`; their contention was not analysed (section 14). |
| **F. Retry storm** | **Vulnerable (pattern confirmed, impact not measured)** | Playback errors reload every ~1 s without an attempt cap or backoff (C-01). Concurrency is limited to one request (disposal + lock), so it is a steady request rate, not a flood. The lounge loop can spin without sleeping after repeated generic exceptions (P-03). |
| **G. Large payloads** | **Partially protected** | The media download is buffer-limited. Size limits for JSON responses were not found (Retrofit converters read the full body). **Unknown** for very large responses. |
| **H. Recovery after an outage** | **Protected** (with one caveat) | There is no durable backlog to replay: work is created again by user actions or timers, so nothing floods the network after recovery. Caveat: a screen that was in a retry loop (C-01) keeps retrying at 1 s while the network returns. |

---

## 11. Confirmed Findings and Evidence

### C-01 — Playback error handling retries forever with a fixed 1 s delay  
**Severity: Medium** · **Component:** player error handling

- **Files:** `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ErrorFixerController.java` (`applyEngineErrorAction`, `:225-326`), `.../VideoLoaderController.java` (`restartEngine()` `:395`, `reloadVideo()` `:399`, delayed variants `:339-367`).
- **Evidence:** every branch of `applyEngineErrorAction` ends with `restartEngine()` or `reloadVideo()`. Both use a hard-coded 1 000 ms delay. The only counter, `MAX_SABR_ERROR_FIX_ATTEMPTS` (`:27`), applies to two SABR-specific paths and, once exceeded, returns `false` so the code continues into the generic path (`:176-183`).
- **Explanation:** a video that cannot play (HTTP 403 on every client, an unplayable Short, a dead network) is requested again every 1–2 seconds until the user leaves the player.
- **Impact:** battery and data use, repeated requests to YouTube that may worsen rate limiting, endless error toasts. Concurrency stays at one request, so it does not exhaust threads.
- **Trigger:** a persistent playback error.
- **Existing protection:** previous request is disposed before the next (`VideoLoaderController.java:248-266`); SABR server backoff is honoured; the client list is rotated (`switchNextClient`).
- **Mitigation:** count attempts per video and stop with a clear message after a limit; use exponential backoff with jitter. Trade-off: some transient errors recover on the 3rd–5th retry, so the limit must be generous; the player UI needs a "tap to retry" state.

### C-02 — One global lock serializes video-data requests, and an abandoned request is not cancelled  
**Severity: Medium** · **Component:** video data (`FormatInfoWrapper`, `VideoInfoService`, prefetch)

- **Files:** `MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/internal/FormatInfoWrapper.kt:36` (`synchronized(lock)`), `.../videoinfo/V2/VideoInfoService.java:33-48, 108-132` (`CLIENT_LIST`, `firstPlayable`, `firstInfoWith`), `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/misc/MediaServiceManager.java:268-280` (`prefetchFormatInfo`), `.../misc/VideoPrefetcher.java`.
- **Evidence:** (1) the lock is held for the whole request, including up to 10 sequential client attempts (20 in the second pass). (2) Rx disposal is used to "cancel" earlier requests (`RxHelper.disposeActions(mPrefetchFormatInfoAction)`, `MediaServiceManager.java:273`), but no code registers a cancellation of the underlying OkHttp call: `setCancellable`/`setDisposable` do not appear in `MediaServiceCore/youtubeapi/src/main` or `common/src/main`, and `RetrofitHelper.getResponse` calls `wrapper.execute()` directly (`RetrofitHelper.java:118`).
- **Explanation:** when the user moves from card A to card B and presses OK, a prefetch of A that already holds the lock keeps running (disposal does not abort a blocked socket read, as far as the source shows). The player's request for B waits behind it.
- **Impact:** slower video start in the worst case. The prefetch was added in this project to make starts faster, so it is a new producer of the same lock. Upper bound from constants is large (section 6.3) but was not observed.
- **Trigger:** slow or failing network during fast browsing.
- **Existing protection:** prefetch is debounced (600 ms) and keeps only one in-flight request; a finished prefetch is cached (`FormatInfoWrapper.mCachedFormatInfo`).
- **Mitigation:** cancel the OkHttp call on disposal (`Call.cancel()` via `Observable` cancellation); let the player request skip or pre-empt a prefetch (for example a lock with `tryLock` and timeout, or per-video-id locking). Trade-off: `FormatInfoWrapper` has shared mutable state (`mCachedFormatInfo`, `mTryInnertubeFirst`) that the lock currently protects, so removing the lock needs care.

### C-03 — No queue, retry or latency metrics exist  
**Severity: Low (Informational for a client app)** · **Component:** whole app

- **Evidence:** no metric counters or tracing for the Rx pool or for retries (section 9). Crashlytics is not wired in the shipped flavor (`smarttubetv/build.gradle:39-42`).
- **Impact:** overload or retry loops on real devices cannot be seen; the only data is what testers report.
- **Mitigation:** an opt-in, local counter of reload attempts and pool rejections shown in an About/debug page would suffice. No remote telemetry is suggested (privacy: see `PRIVACY.md`).

### C-04 — Per-phase timeouts only; no call timeout and no task deadline  
**Severity: Low** · **Component:** HTTP client

- **Evidence:** `OkHttpCommons.java:43-45, 94-96` set connect/read/write to 20 s. `callTimeout` is not set anywhere in the OkHttp setup (`OkHttpCommons.java`, `RetrofitOkHttpHelper.kt`).
- **Impact:** a response that trickles data can keep a pool thread busy longer than 20 s. Combined with C-02, the lock holder can be slow.
- **Mitigation:** add a whole-call timeout for API calls (not for media chunks). Trade-off: too small a value would cut legitimate slow responses on weak networks.

### C-05 — Client-side rate limiter exists but is switched off; no global request-rate control  
**Severity: Informational** · **Component:** HTTP client

- **Evidence:** `RateLimitInterceptor.java` sleeps 1 s after an HTTP 429; its registration is commented out (`OkHttpCommons.java:293`).
- **Impact:** none by itself. It matters only together with C-01 (retries) if YouTube starts answering 429.
- **Mitigation:** do not simply re-enable it (a fixed `Thread.sleep` on the caller thread can block the UI thread when `CallerRunsPolicy` applies). Prefer backoff in the retry logic (C-01).

### C-06 — The shared library code exists in two identical copies  
**Severity: Informational** · **Component:** build layout

- **Evidence:** `SharedModules/` and `MediaServiceCore/SharedModules/` both exist; `settings.gradle` selects the root one. `diff -rq SharedModules MediaServiceCore/SharedModules` reported no difference at audit time.
- **Impact:** a future fix applied to one copy only would not take effect or would diverge.
- **Mitigation:** document which copy is built (done here); keep them in sync or remove one.

---

## 12. Potential Risks (not confirmed)

### P-01 — `CallerRunsPolicy` can run network work on the UI thread  
**Severity: Medium (potential)** · `RxHelper.java:55`, used through `setup()` at `:359-365`

When 12–24 tasks are outstanding (section 6.1), the 13th–25th `subscribe()` call executes its task on the thread that called it. Presenters usually subscribe from the main thread, so a blocking `Call.execute()` could run on the UI thread (up to 20 s per phase). This is how `ThreadPoolExecutor` and RxJava's `ExecutorScheduler` normally behave, but it was **not reproduced** here. Likelihood in normal use is low (it needs a slow network and many simultaneous loads). Possible mitigation: reject-and-drop for low-priority work (prefetch, suggestions), or a larger queue. Trade-off: dropping work needs each caller to handle rejection.

### P-02 — Endless Home list has no memory cap  
**Severity: Low (potential)** · `BrowsePresenter.onRowsEnd` (`:542-589`), `YouTubeContentService.getHomeMoreRowsObserve` (`:427-455`, `HOME_MORE_BATCH = 4`, `:404`)

Each scroll to the end adds up to 4 rows (4 sequential searches). Nothing removes old rows. Guards exist: single-flight flag `mLoadingMoreRows`, at most 3 empty retries (`retryMoreRowsIfEmpty`, `:591-597`), Glide's bounded caches. On a TV box with little RAM, a very long scrolling session may increase memory use. The CI run showed 196 different titles after 16 scrolls (`docs/AI_MEMORY.md`) but no memory measurement exists. Mitigation: drop rows far above the viewport.

### P-03 — Remote-control listener may loop without a pause  
**Severity: Low (potential)** · `LoungeService.startListening` (`:100-124`), `startListeningInt` (`:150-182`)

On the normal path the loop sleeps 3 s (`:106`). If `startListeningInt` throws a non-timeout, non-interrupt exception after a successful session bind, `catch (Exception e)` (`:119`) only logs and loops again at once. Each pass also creates a new `OkHttpClient` (`:173`), with its own connection pool and dispatcher. A "connection reset" repeated quickly could produce a tight loop. Not reproduced; whether remote control is enabled by default was not verified.

### P-04 — Unbounded threads for long-lived loops; timer callbacks run on the main thread  
**Severity: Low (potential)** · `RxHelper.java:342, 371-378`

`createLong` and `interval` use `Schedulers.newThread()` and then `observeOn(AndroidSchedulers.mainThread())`. The number of such loops is small and fixed by features (section 6.2). The interval callbacks (SponsorBlock every 1 s; the PoToken V8 timer, which executes a JavaScript call) are delivered on the main thread. Risk is UI jitter, not saturation.

### P-05 — A 10 s sleep on the media loader thread  
**Severity: Informational (potential)** · `TrackErrorFixer.fixEmptyChunk` (`TrackErrorFixer.java:239-252`)

For live streams whose position is ahead of the timeline the ExoPlayer loader thread sleeps 10 s. It blocks that stream's loading only.

---

## 13. Risk Severity Matrix

| ID | Type | Severity | Likelihood (normal use) | Impact | Section |
|---|---|---|---|---|---|
| C-01 | Confirmed | Medium | Medium (any persistent playback error) | Data/battery, rate-limit risk | 11 |
| C-02 | Confirmed | Medium | Low–Medium (slow network + fast browsing) | Slower video start | 11 |
| C-03 | Confirmed | Low | — | Blind to field issues | 11 |
| C-04 | Confirmed | Low | Low | Longer thread occupation | 11 |
| C-05 | Confirmed | Informational | — | Context for C-01 | 11 |
| C-06 | Confirmed | Informational | Low | Maintenance | 11 |
| P-01 | Potential | Medium | Low | UI stall / ANR | 12 |
| P-02 | Potential | Low | Low | Memory growth | 12 |
| P-03 | Potential | Low | Low | Request loop | 12 |
| P-04 | Potential | Low | Low | UI jitter | 12 |
| P-05 | Potential | Informational | Low | One stream | 12 |

---

## 14. Recommended Mitigations (only where the architecture justifies them)

| Priority | Change | Fixes | Trade-offs / compatibility |
|---|---|---|---|
| 1 | Attempt counter and exponential backoff with jitter for playback reloads, with a final "could not play, press OK to retry" state | C-01 | Must not break SABR/backoff behaviour; needs a limit that still lets slow recoveries succeed. |
| 2 | Cancel the underlying OkHttp call when an Rx subscription is disposed | C-02 | Touches `RetrofitHelper` (shared by all calls). Changes error timing, so tests of the sign-in and Home flows are needed. |
| 3 | Let a playback request pre-empt a prefetch (timed `tryLock`, or skip prefetch when a request holds the lock) | C-02 | `FormatInfoWrapper` shared state must stay consistent. |
| 4 | `callTimeout` for API calls only | C-04 | Value must suit slow TV-box networks. |
| 5 | Drop or reject low-priority work (prefetch, suggestions) when the pool queue is full instead of `CallerRunsPolicy` | P-01 | Each caller must handle rejection. |
| 6 | Backoff after a failed iteration in the lounge loop; reuse one `OkHttpClient` | P-03 | Low risk; keep the 3 s minimum. |
| 7 | Optional local counters (reload attempts, pool rejections) in an About/debug screen | C-03 | No remote telemetry (privacy). |
| 8 | Keep a limit on rows kept in memory for the endless Home list | P-02 | Scroll position restore must still work. |

Not recommended: adding a message broker, dead-letter queue, server-style rate limiter or circuit breaker library. The app has no server side to justify them.

---

## 15. Missing Information and Unknowns

1. **Real-device behaviour.** No measurement was made on a TV box or phone: thread counts, memory use, whether the UI ever stalls (P-01).
2. **RxJava details.** The behaviour of `CallerRunsPolicy` through `ExecutorScheduler`, and whether disposal interrupts a thread blocked in a socket read, were taken from general library behaviour, not from a test or the library source in this repository.
3. **PoToken timer unit.** `PoTokenV8.kt:97-110` passes the JavaScript delay to `RxHelper.startInterval(callback, periodSec)`, which uses `TimeUnit.SECONDS` (`RxHelper.java:160-166`). Whether the JavaScript side passes seconds or milliseconds was not verified.
4. **Remote control default.** Whether the lounge listener (P-03) is on by default was not verified.
5. **Default ExoPlayer byte target** for buffer modes other than "highest", and Glide's memory-cache size, were not inspected.
6. **Response size limits.** No explicit size limit for JSON responses was found; whether the converters stream or read fully was not analysed.
7. **`SharedPreferences` write frequency** (`persistData()` on every setter) and its effect on the main thread was not measured.
8. **YouTube's server behaviour** (rate limits, 403 policy) is outside the project and was not assessed.
9. **Duplicates / idempotency** of the playback history ping on retries was not analysed.

---

## 16. Production Readiness Assessment

For a consumer client app, the design is **partially ready**:

- Strengths: bounded thread pool and queue, single-flight guards in most features, debounced prefetch, buffer-limited media loading, a global Rx error handler, unique scheduled work.
- Main gaps: unbounded retry with a fixed delay (C-01), one global lock with non-cancelled requests (C-02), UI-thread overflow policy (P-01), no field visibility (C-03).
- Nothing found would corrupt data or crash the app by itself. The confirmed issues affect speed, battery and data use under bad networks. A real-device test of a persistent 403 or of a slow network is the most useful next check.

---

## 17. Final Audit Checklist

| Check | Status |
|---|---|
| Existing source code modified | **No** |
| Existing documentation modified | **No** (this file is new; `docs/AI_MEMORY.md` was not edited, to keep the audit read-only) |
| Dependencies, environment, database, infrastructure changed | **No** |
| Tests or load run | **No** (analysis from source only) |
| File paths and symbol names verified against the source | Yes (see the validation note in the delivery message) |
| Confirmed findings | 6 (C-01 … C-06) |
| Potential risks | 5 (P-01 … P-05) |
| Unknowns listed | 9 (section 15) |

---

## 18. Fixes applied after this audit (version 33.16)

The audit itself was read-only. After it, the owner asked for the issues to be fixed, and these changes were made. They are **built and run through the emulator smoke tests, but not tested on a real device**, and the emulators cannot decode video, so the playback loop (C-01) was not exercised end to end.

| ID | Change | Files | Status |
|---|---|---|---|
| C-01 | Automatic reloads after a playback error now wait 1, 2, 4, 8, 16, 20 s (plus up to 25 % random jitter) and stop after 8 attempts per video, with one message ("Could not play this video. Press play to try again."). The counter restarts when the video plays, when another video is opened, or when the user tries again later (an error more than 10 s after the app gave up). | `ErrorFixerController.java` (`nextReloadDelayMs`, `giveUpReloading`, `reloadVideoLimited`, `restartEngineLimited`), `VideoLoaderController.java` (`reloadVideo(int)` and `restartEngine(int)` made public), strings `younew_playback_gave_up` (EN/AR) | Fixed in code |
| C-02 | When the caller of a video-data request gives up (Rx disposal interrupts the thread), the client loop stops at once and no second pass runs, so the global lock is released after the request in progress, not after up to 20. The lock itself and the cancellation of the HTTP call in flight are **unchanged**. | `VideoInfoService.java` (`firstPlayable`, `firstInfoWith`) | Partly fixed (the long case); depends on the thread being interrupted by RxJava (Unknown 2, section 15) |
| C-04 | API calls (the Retrofit client only, not media) have a 60 s whole-call timeout. | `RetrofitOkHttpHelper.kt` (`createClient`) | Fixed in code |
| P-01 | A full pool no longer runs a task on the main thread: from the main thread the task goes to an overflow thread (`Schedulers.io()`); from other threads the old caller-runs behaviour stays. Changed in both identical copies of the file (C-06). | `SharedModules/.../rx/RxHelper.java`, `MediaServiceCore/SharedModules/.../rx/RxHelper.java` (`sOverflowPolicy`) | Fixed in code |
| P-03 | The remote-control loop waits 3 s after a failed pass, and one shared `OkHttpClient` is used for all passes. | `LoungeService.java` (`startListening`, `getListenClient`) | Fixed in code |

Not changed, with the reason:

| ID | Why it was left |
|---|---|
| C-03 (no metrics) | Needs a design decision (local counters or none); remote telemetry was rejected for privacy. |
| C-05 (rate limiter disabled) | Re-enabling a fixed sleep on the caller thread would be worse than the problem; C-01 now provides backoff. |
| C-06 (two copies of the shared code) | Only a documentation point; the P-01 change was applied to both copies so they stay identical. |
| P-02 (endless Home memory) | Removing old rows can make the list jump; it needs a memory measurement on a real TV box first. |
| P-04, P-05 (main-thread timer callbacks, 10 s loader sleep) | Informational; no evidence of harm, and changing them risks the PoToken and live-stream code. |
