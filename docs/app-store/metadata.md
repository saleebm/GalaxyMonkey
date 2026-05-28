# Galaxy Monkey — App Store Connect metadata (copy-paste)

Reference for filling out the listing at
[appstoreconnect.apple.com](https://appstoreconnect.apple.com) → **My Apps →
Galaxy Monkey**. Field character limits are noted in parentheses.

## App identity (already fixed in the project)

| Field | Value |
|-------|-------|
| Bundle ID | `dev.copt.GalaxyMonkey` |
| Team ID | `L7U86T3YRV` |
| Version (CFBundleShortVersionString) | `1.0.0` |
| Build (CFBundleVersion) | `1` |
| Platforms | iPhone + iPad (universal), iOS 18.0+ |
| Orientation | Landscape only |

## App Information (app-level)

- **Name:** `Galaxy Monkey`
- **Subtitle (≤30):** `Twin-stick space shooter`
- **Primary category:** Games → **Action**
- **Secondary category:** Games → **Arcade**
- **Content rights:** "This app does not contain, show, or access third-party
  content." (no third-party content)

## Version information (1.0.0)

- **Promotional text (≤170):**
  ```
  Pilot a monkey through endless waves of alien ships and gorilla bosses. Dual-stick controls, golden bananas, and a black-hole finish to every kill.
  ```

- **Keywords (≤100, comma-separated, no spaces after commas):**
  ```
  space,shooter,arcade,twin stick,monkey,galaxy,bullet,retro,action,survival,banana,boss
  ```

- **Description:**
  ```
  Galaxy Monkey is a fast, fluid twin-stick arcade shooter set in deep space.

  Pilot a monkey-crewed starfighter through procedurally spawned waves of
  enemy ships and gorilla bosses. The left thumb flies, the right thumb aims
  and auto-fires — touch anywhere on each side and the sticks come to you.

  • Dynamic dual joysticks with momentum-based drift
  • Endless escalating waves and gorilla boss fights
  • Golden banana pickups and score chasing
  • Punchy haptics, arcade SFX, and a black-hole warp on every kill
  • Built natively with SpriteKit for iPhone and iPad

  No ads. No tracking. No accounts. Just pure arcade dogfighting.
  ```

- **What's New in This Version:** `Initial release.`
- **Copyright:** `© 2026 <your legal / developer name>`  ← EDIT
- **Support URL:** `<your existing support URL>`  ← EDIT
- **Marketing URL (optional):** leave blank or your site
- **Privacy Policy URL:** `<hosted privacy-policy.html URL>`  ← EDIT (see privacy-policy.html)

## App Privacy (app-level → "Get Started")

Answer: **"Data is not collected from this app."**

Justification (verified in code — no networking, analytics, tracking, IAP,
GameKit, or third-party SDKs): the app stores only local game state (best
score + settings) in `UserDefaults`, which never leaves the device. This
matches `GalaxyMonkey/PrivacyInfo.xcprivacy` (`NSPrivacyTracking = false`,
empty `NSPrivacyCollectedDataTypes`). The two MUST agree, so do not select any
collected data types.

## Age rating questionnaire

Apple computes the final rating from your answers. For this game:

| Question | Answer |
|----------|--------|
| Cartoon or Fantasy Violence | **Infrequent/Mild** |
| Realistic Violence | None |
| Prolonged graphic or sadistic realistic violence | No |
| Horror / Fear themes | None |
| Mature / Suggestive themes | None |
| Profanity or Crude Humor | None |
| Alcohol, Tobacco, or Drug Use | None |
| Sexual Content or Nudity | None |
| Gambling (simulated or real) | No |
| Contests | No |
| Unrestricted Web Access | No |
| User-generated content / messaging | No |

**Expected result: ≈ 9+** (cartoon/fantasy violence only).

## Export compliance

No action needed. `Info.plist` sets `ITSAppUsesNonExemptEncryption = false`,
so App Store Connect skips the encryption question automatically. The app uses
no custom cryptography.

## Pricing & availability

- **Price:** Free (or set a tier)
- **Availability:** All territories (or your selection)
