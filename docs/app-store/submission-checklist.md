# Galaxy Monkey — App Store submission checklist

End-to-end steps to get **1.0.0 (build 1)** into review. Copy-paste field
values live in [metadata.md](./metadata.md). The privacy policy page is
[privacy-policy.html](./privacy-policy.html).

## Status at last update (2026-05-27)

- [x] Version set to `1.0.0`, build `1` (`GalaxyMonkey/Info.plist`)
- [x] Black-hole warp VFX committed (`4286c3d`)
- [x] Signed Release archive verified — `build/export/GalaxyMonkey.ipa`
      (Cloud Managed Apple Distribution, `1.0.0 (1)`, team `L7U86T3YRV`)
- [x] App icon valid (1024×1024, opaque, no alpha)
- [x] `PrivacyInfo.xcprivacy` present, no data collected
- [x] Screenshot script targets installed sims (`2a3e50e`)
- [ ] Privacy policy hosted at a public URL
- [ ] Screenshots captured
- [ ] Build uploaded to App Store Connect
- [ ] Metadata + age rating + App Privacy entered
- [ ] Submitted for review

## 1. Host the privacy policy

1. Edit the three `/* EDIT */` fields in `privacy-policy.html` (legal name,
   contact email, effective date).
2. Publish it at a stable, **public, login-free** URL, e.g.
   `https://copt.dev/galaxymonkey/privacy`.
3. Open it in a private browser window to confirm it loads with no auth.
   Apple's reviewer opens this URL directly; a 404 or login wall = rejection.

## 2. Capture screenshots

iPhone-only app — only the **6.9" iPhone** slot is required (no iPad), landscape.

```bash
scripts/capture-screenshots.sh
```

The script boots each simulator, launches the app, and waits. Drive the game
and press **enter** to capture; output lands in
`build/screenshots/<device>/`. Capture 2–3 strong frames per device:

- Title / start prompt
- Mid-combat (enemies on screen, bullets flying)
- A gorilla boss fight
- Game-over overlay with a score

Audio is silent on the simulator — that's expected and fine for stills.

## 3. Upload the build

The `.ipa` is already built at `build/export/GalaxyMonkey.ipa`.

**Option A — altool (scripted):**
```bash
export ASC_APPLE_ID='saleebmina@copt.dev'
export ASC_APP_PASSWORD='xxxx-xxxx-xxxx-xxxx'   # app-specific password
scripts/upload.sh
```
Generate the app-specific password at
[account.apple.com](https://account.apple.com) → Sign-In and Security →
App-Specific Passwords. `upload.sh` validates first, then uploads.

**Option B — Transporter:** open **Transporter.app** (Mac App Store) and drag
`build/export/GalaxyMonkey.ipa` into it.

After upload, Apple processes the build (~5–30 min). It appears under
**TestFlight** and the version page once it reaches "Ready to Submit".

> If a later upload is rejected with "build number already used," bump it:
> `scripts/bump-version.sh build`, then re-run `scripts/archive.sh`.

## 4. Fill out the listing in App Store Connect

Go to **My Apps → Galaxy Monkey**. Using [metadata.md](./metadata.md):

1. **App Information:** subtitle, primary (Action) + secondary (Arcade)
   category, content rights.
2. **App Privacy → Get Started:** select **"Data is not collected."**
3. **Age rating:** answer the questionnaire (Cartoon/Fantasy Violence =
   Infrequent/Mild, everything else None/No) → ≈ 9+.
4. **1.0.0 version page:** promotional text, description, keywords, support
   URL, privacy policy URL, copyright, "What's New."
5. **Screenshots:** drag in the 6.9" iPhone captures (and the App Preview video).
6. **Build:** click **+** / "Add Build" and select the processed `1.0.0 (1)`.

## 5. Submit for review

1. Set release option: **Manually release** (recommended for a first launch so
   you control go-live) or **Automatically**.
2. (Optional) App Review notes: *"Single-player arcade game. No login, no
   accounts, no in-app purchases. Tap to start; left half moves, right half
   aims and fires."* — speeds review.
3. Click **Add for Review** → **Submit for Review**.
4. Status should move to **Waiting for Review**.

## Reference

- Archive pipeline: `scripts/archive.sh` → `scripts/upload.sh`
- Version bump: `scripts/bump-version.sh patch|minor|major|build`
- Export config: `scripts/ExportOptions.plist` (method `app-store-connect`)
