# Gate H — Offline / SDK / network-source audit (2026-10-12)

**State: CODE_STATIC_VERIFIED_ONLY after PR integration; RUNTIME_NETWORK_NOT_TESTED; DATA_SAFETY_NOT_APPROVED.**

## What was actually inspected

- `app/index.html` used to issue external Google Fonts preconnect + CSS requests to `fonts.googleapis.com` and `fonts.gstatic.com`. **This was a genuine third-party network surface on app bootstrap**, despite a "local-only" product premise.
- The privacy-hardening branch removes all remote font links and sets an Android/system installed font stack in `app/src/App.css`. This avoids changing Java/Android protection logic and keeps Chinese fallback fonts available.
- Code searches across first-party `app/src` and Android Java sources found no direct `fetch('https://...')`, XMLHttpRequest, WebSocket, OkHttpClient, or HttpURLConnection invocation. That is a code-search finding only — dynamic URLs, Android system network activity and transitive SDKs are **not ruled out**.
- `app/android/app/src/main/AndroidManifest.xml` still declares `android.permission.INTERNET`. Do not remove it without proving Capacitor/WebView and Android plugin startup are compatible. INTERNET alone does not prove transmission, but it prevents signing off "no internet capability."
- `app/android/app/build.gradle` declares Capacitor Android, AndroidX Room/AppCompat/core-splashscreen, test-only Robolectric/AndroidX test. Build script optionally applies Google services plugin **only if an app/google-services.json exists**; repository search did not identify that file in tracked source. This does not exclude binaries/plugins injected in future or at build time.
- Product `app/package.json` contains React, React Router, Capacitor Core/Android, Recharts, Zustands etc. No declared mobile advertising or analytics SDK. Note that package declarations are not an exhaustive compiled binary software bill of materials.
- The app's exported JSON uses the Android system `ACTION_SEND` chooser in `NativeFlowDataManager`. The user controls the destination; once given to another application, FlowBreak cannot delete that app's copy. This is distinct from automatic app-server collection.
- Domestic optional Accessibility uses both window package and window class (WeChat video channel), derives local Boolean and timestamp. This has been clarified in the in-app disclosure and privacy documents.
- The app contains crash-log viewing; an empty crash folder is **not proof of no crash**.

## Repeatable automated evidence

`npm run test:privacy-static` runs `node --test scripts/__tests__/audit-network-surface.test.mjs` followed by `node scripts/audit-network-surface.mjs`. The static scan:
- tests against a simulated Google Fonts HTML link, CSS @import and literal fetch, asserting rejection;
- verifies the current first-party HTML/CSS/JS/TS/Java and root Capacitor config do not include obvious third-party bootstrap or literal outbound clients;
- reports `androidInternetPermission:true` rather than falsely claiming zero network privileges;
- marks its output `NOT_RUNTIME_NETWORK_PROOF` and reports Android INTERNET permission; it is static only.
- Explicitly rejects `capacitor.config.ts` `server.url` remote-runtime configuration; rejects literal cross-origin bootstrap in HTML/CSS and obvious JS remote asset assignments (including scheme-relative URLs).

This check runs in ordinary GitHub Android `verify`, which contains **no production signing secrets** and builds both channels. It does not simulate Android Usage Access, signed upgrade, HyperOS background execution, or third-party socket activity.

## External / independent closure work

1. **Exact final AAB/APK**: after release-commander source freeze, retrieve release artifact SHA, package versions, merged release manifests, native dex/so and actual dependency inventory for Play and Domestic. Verify static resources contain no external font, script or CSS origins after bundling. Different commits' test results cannot be mixed.
2. **Runtime traffic**: use a disposable Android emulator with a correctly installed approved test/debug build under controlled Wi-Fi and a packet/TLS-aware capture that records destinations/metadata without user data; record baseline system traffic and app UID/socket deltas. Verify idle startup, onboarding, export UI, target selection, REST flow and error paths. Emulator can support an initial **non-phone** report, but cannot certify Xiaomi/Redmi ROM behavior or final production-signed binary.
3. **Distributions / SDK list**: resolve Android runtime classpath and final binary manifests for Play + Domestic. Any Google services, crash SDK, ad or analytics plugin added at build time must be explicitly reviewed before approving Data Safety.
4. **Platform declarations**: determine for each of Google Play's Data safety categories whether data **leaves the device**, what user-initiated sharing falls under, retention and encryption in transit. Do not fill "nothing collected/shared" solely from a static pass.
5. **Policy/contact**: final public policy URL, verified publisher/operator and support email are owner-required. APP备案 applicability and channels require issuer verification. All Gate H sign-off remains blocked until actual submitted/approved forms.

## Decision

- `SOURCE_REMOTE_GOOGLE_FONTS=REMOVED_IN_PR`
- `STATIC_EXTERNAL_BOOTSTRAP_GATE=AUTOMATED` (upon CI pass); the scan covers complete HTML tags including newline attributes and unquoted/protocol-relative URLs, obvious browser clients and CSS remote resources, **not** dynamic obfuscation or transitively linked SDK network activity.
- `ANDROID_INTERNET_PERMISSION=PRESENT`
- `FINAL_BINARY_NETWORK_CAPTURE=NOT_EXECUTED`
- `FINAL_SDK_AND_DATA_SAFETY_APPROVAL=NOT_EXECUTED`
- `GATE_H=PENDING`, `STORE_RELEASE=NOT_APPROVED`

**No user's phone, credentials, signing identity, or personal usage records are needed to implement this static check.**
