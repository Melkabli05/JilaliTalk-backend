# Signup workflow — full reverse-engineering reference

> Source: `/home/mohammed/Desktop/JilaliTalk/re_output/` (the unpacked HelloTalk Android APK reverse-engineering output). The original flow was the production Android client at `com.hellotalk.sign.register.*`. All file:line citations below point into that tree.
>
> This doc is the canonical source of truth for any signup-flow change in `jilalibff` or `JilaliTalk-angular-frontend`. If a code change disagrees with anything here, the RE wins — verify the new upstream behavior with a live capture before accepting the deviation.

## 0. Module map (smali)

| Class | Role |
|---|---|
| `com/hellotalk/sign/register/mvp/ui/NewSignUpActivity` | UI entry point; the activity the user sees |
| `com/hellotalk/sign/register/mvp/ui/i` (or `c`) | Presenters/View contracts |
| `com/hellotalk/sign/register/mvp/ui/INewSignUpView` | Interface the activity implements |
| `com/hellotalk/sign/register/data/n` | Local form-state holder; tracks validity, errors, branches (the form's "learnlang1" / "learnlang2" / "branch" / "branchcn" / "country" / "sex" / "birthday" — verified at r21/m.smali:482–651) |
| `com/hellotalk/sign/register/data/i` | DTO `SignCheckReqV3` (the body of `/v3/check`) — class file at smali_classes22 |
| `com/hellotalk/sign/register/data/SignCheckResp` | DTO, response of `/v3/check` |
| `com/hellotalk/sign/service/LoginService` | Retrofit interface; the actual HTTP call sites for the whole auth subsystem |
| `com/hellotalk/sign/service/AccountVerifyService` | Sister interface for `reg/prepare` + `reg/profile_check` + `send_email_code` |
| `j21/b` | Pure-Kotlin helper object containing the **password character-class regexes** and the **`onSignUpClicked` flow** |
| `y11/w` | IM-layer auth flow (not used for signup but worth knowing exists) |
| `r21/m` | The 7,107-line `LiveHub` master API client. The signup flow is one of many inside it. |

## 1. The user's two UI states

The signup activity is a **two-step flow**, not three. Per `signup-page.component.ts` (the BFF's matching frontend code) and the RE smali at `j21/b.smali:472–592`:

1. **Account** — email + password entry, then "Send verification code"
2. **Verify** — 4-digit code entry, then "Create account"

There's no third "pick your nickname" step. The RE confirms: "**`/v3/check`'s upstream request is `{email, password, email_verify_code}` only**" — the nickname check is an *independent* API (`/v3/reg/profile_check`) and is **not** a gating step in the email-signup pipeline. The old mocked flow had a nickname step; the real one doesn't.

## 2. The complete HTTP call chain (the wire-level truth)

The canonical email+password signup pipeline is **5 calls** in sequence, of which 1 is best-effort and 2 are no-ops for the email flow:

```
[1] POST /user_register_center/v3/reg/prepare             (best-effort, log-and-continue on fail)
        body: {bind_id: deviceId, irisk_token: ""}
   resp: {irisk_token: "HTIRISK_<UUID>"}                 ← captured for step 4

[2] POST /user_register_center/v3/send_email_code        (always, before step 3)
        body: {behavior_validate: <token>, email: <e>, scene: "new_device_login"}
   resp: {next_send_temp_pwd_seconds: <cooldown>}

[3] [USER ENTERS THE 4-DIGIT CODE IN THE UI]

[4] POST /user_register_center/v3/check                   (terminal, requires irisk_token from step 1)
        body: SignCheckReqV3{login_type=1, email, password, email_verify_code,
                            terminaltype=1, version, client_lang,
                            device_id, t, htntkey, operator, sim_country_code,
                            third_party_login: {email_password: {email, password, email_verify_code}},
                            irisk_token: <from step 1>}
   resp: SignCheckResp{user_info, area_code, region_display_policy?, verify_token, banned_info?}
        NOTE: NO access_token / NO jwt — must fall through to step 5

[5] POST /user_register_center/v3/pre_login           (always, after step 4)
        body: {login_type: 1, email, os_type: 0, device_id, client_version, sign}
   resp: PreLoginResp{cnonce, nonce}

[6] POST /user_register_center/v3/login                 (always, after step 5 — reuses login pipeline)
        body: EmailLoginReq{login_type, email, password, os_type, device_id, device_detail,
                            appstore_country, sign, watchman_token, jail_break, net_type,
                            is_vpn, behavior_validate, irisk_token, account_login: {email, password}}
   resp: LoginResp{user_info{jwt, userId, ...}, countdown_info}
        NOW we have the JWT. The session is established.
```

The post-step-4 fallthrough into step 5/6 is what the RE calls "the standard email login pipeline" — and what `HelloTalkAuthService.signup` already does (`return switch (login(email, password)) { ... }`).

## 3. Endpoint details (smali-verified)

### 3.1 `POST /user_register_center/v3/reg/prepare` — `Ls21/e;`

**Source**: `apktool_out/smali_classes22/com/hellotalk/sign/service/LoginService.smali:89` (method `b`, returns `Object` from suspend, annotated `@POST("/user_register_center/v3/reg/prepare") @Headers({"ht-content-type:ht/encbin"})`).

**Request**:
```json
{
  "bind_id": "<deviceId>",
  "irisk_token": ""
}
```

**Response** (captured by us, format inferred from FINDINGS.md §7.3 description):
```json
{
  "irisk_token": "HTIRISK_<UUID>"
}
```

**Status**: best-effort. The BFF's `regPrepare` is allowed to fail — if it does, `Optional.empty()` is returned and `HelloTalkAuthService.signup` proceeds with an empty `irisk_token`. The upstream then treats the absent field as "no token" per the Gson-doesn't-serialize-null semantics.

### 3.2 `POST /user_register_center/v3/send_email_code` — `Ls21/f;`

**Source**: `apktool_out/smali_classes22/com/hellotalk/sign/service/AccountVerifyService.smali` (search for `b(Ls21/f;`).

**Request**:
```json
{
  "behavior_validate": "<captcha_token_or_empty>",
  "email": "<e>",
  "scene": "new_device_login"
}
```

**Correction from FINDINGS.md line 263**: `scene` is hardcoded `"new_device_login"`. There is no `"register"` or other variant — the constructor takes a single string.

**Response**:
```json
{
  "next_send_temp_pwd_seconds": <cooldown_seconds>,
  ...
}
```

The cooldown shows in the UI's "Resend code" button as a countdown.

### 3.3 `POST /user_register_center/v3/reg/profile_check` — `Ls21/d;` (NICKNAME — INDEPENDENT)

**Source**: `apktool_out/smali_classes22/com/hellotalk/sign/service/AccountVerifyService.smali` (method `d`).

**Correction from FINDINGS.md line 268**: Despite the endpoint name "profile_check", this is a **nickname availability/validity** check. Body is just `{nickname}`. The endpoint does **not** gate `/v3/check`; it can run independently. Used in the legacy register flow but **not in the modern email-signup pipeline** (which doesn't capture a nickname at signup time per the `SignCheckReqV3` field set).

### 3.4 `POST /user_register_center/v3/check` — `Ls21/i;` (TERMINAL SIGNUP)

**Source**: `apktool_out/smali_classes22/com/hellotalk/sign/service/LoginService.smali:49` (method `a`, the only `/v3/check` call in the auth subsystem).

**Request** (`SignCheckReqV3`):
```json
{
  "login_type": 1,
  "email": "<e>",
  "password": "<plaintext_password>",
  "email_verify_code": "<4-digit-code>",
  "terminaltype": 1,
  "version": "<versionName>",
  "client_lang": "<>",
  "device_id": "<DVId>",
  "t": <System.currentTimeMillis()>,
  "htntkey": "<MD5(DVId + loginType + t + 'abccdfef#*')>",
  "operator": "<TelephonyManager.getSimOperatorName() or empty>",
  "sim_country_code": "<getSimCountryIso().toUpperCase() or empty>",
  "third_party_login": {
    "email_password": {
      "email": "<e>",
      "password": "<plaintext_password>",
      "email_verify_code": "<4-digit-code>"
    }
  },
  "irisk_token": "<from step 1, or empty>"
}
```

**Field-by-field notes** (from FINDINGS.md §7.2 + the smali at j21/b.smali:472–592):

- `login_type: 1` — **always 1** for email. **10** for SMS, which uses a different `partyValue` shape (phone_number + sms_code + phone_code). The BFF's email path is locked to 1.
- `partyName`/`partyValue` — the `j21/b.f` constructor explicitly repackages these into `third_party_login` (not top-level). This is a **corrected finding** — an earlier pass of the BFF emitted literal top-level `partyName`/`partyValue` JSON keys and `third_party_login: null` simultaneously. Caught by re-reading the smali constructor bytecode.
- `htntkey` = `MD5(deviceId + loginType + t + "abccdfef#*")`. Computed locally; the secret key is in `libhellotalk-tea.so` table index 3 (`HtntKeyUtil.compute` in `HelloTalkAuthClientImpl`).
- `terminaltype: 1` — Android. iOS would be 0.
- `operator` / `sim_country_code` — may be empty on devices without a SIM (Wi-Fi-only emulator). Send empty string rather than omitting the field.
- `irisk_token` — **REQUIRED on this flow** (vs. the login flow's `behavior_validate`). When missing, upstream returns "code is incorrect" and refuses to create the account. The BFF's `c4cb9c3` commit added the field plumbing; the `6fcb52e` commit added the rejection logging so we can see upstream's actual error next time.

**Response** (`SignCheckResp`):
```json
{
  "user_info": {
    "bind_id": "<deviceId>",
    "birthday": "<yyyy-MM-dd>",
    "cnonce": "<server-cnonce>",
    "email": "<e>",
    "head_url": "<avatarUrl>",
    "nationality": "<iso-2>",
    "nickname": "<autoGenerated or null>",
    "password": "<plaintext>",   // ⚠️ upstream echoes the password back — never log this
    "sex": <int>
  },
  "area_code": "<212>",
  "region_display_policy": {...}?,  // optional
  "verify_token": "<token>",     // NEVER null on success — caller checks this
  "banned_info": {...}?           // optional, populated on region/device bans
}
```

Field set verified by reading `r21/m.smali` iput instructions in `SignCheckResp$UserInfoBean.smali`:
- `bind_id`, `birthday`, `cnonce`, `email`, `head_url`, `nationality`, `nickname`, `password`, `sex` (the integer one) — 9 fields total.

**No `access_token` / `jwt` in this response.** The caller must do steps 5+6 (pre_login + login) to mint the actual JWT.

**Note on `password` echo**: the upstream HelloTalk service echoes the plaintext password back in the `user_info.password` field. This is a real security smell at the upstream — never log this field, never store it in BFF state. The BFF's `UserInfo.password` mapping (if any) should explicitly drop it.

### 3.5 `POST /user_register_center/v3/pre_login` — reuses `Ls21/c;` (login DTO, not signup)

**Source**: `apktool_out/smali_classes22/com/hellotalk/sign/service/LoginService.smali` — pre_login is shared between login and signup.

**Request**:
```json
{
  "login_type": 1,
  "email": "<e>",
  "os_type": 0,
  "device_id": "<DVId>",
  "client_version": "<versionName>",
  "sign": "<MD5('client_version=' + v + '&deviceid=' + d + '&login_type=' + 1 + '&ts=' + t + SECRET)>"
}
```

**Response**:
```json
{
  "cnonce": "<server-1>",
  "nonce": "<server-2>"
}
```

`cnonce` + `nonce` are the two server-supplied salts the BFF uses to obfuscate the password hash on the next login call. The BFF's `EmailPreLoginRequest` record has the exact field set.

### 3.6 `POST /user_register_center/v3/login` — `Ls21/b;`

**Source**: `apktool_out/smali_classes22/com/hellotalk/sign/service/LoginService.smali:153` (overload `d`, returns `LoginResponse` from suspend).

**Request** (full `EmailLoginReq`):
```json
{
  "login_type": 1,
  "email": "<e>",
  "password": "<MD5(MD5(raw) + cnonce)>",     // double-MD5 with the cnonce from step 5
  "os_type": 0,
  "device_id": "<DVId>",
  "device_detail": "<model-json>",
  "appstore_country": "<iso-2>",
  "sign": "<see FINDINGS line 174>",
  "watchman_token": "",
  "jail_break": 0,
  "net_type": 0,
  "is_vpn": 0,
  "behavior_validate": "<captcha_token_or_empty>",
  "irisk_token": "",
  "account_login": {
    "email": "<e>",
    "password": "<MD5(MD5(raw) + nonce)>"        // double-MD5 with the nonce from step 5
  }
}
```

**Response** (`LoginResp`):
```json
{
  "user_info": {
    "userId": <long>,
    "jwt": "<access_token>",     // THIS is what the BFF stores in auth_session
    "nickname": "...",
    "headUrl": "...",
    "deviceId": "...",
    ...
  },
  "countdown_info": {...}?
}
```

The BFF's `emailLoginRequest` builder in `HelloTalkAuthClientImpl` does the `MD5(MD5(raw) + cnonce)` and `MD5(MD5(raw) + nonce)` operations and packs them into the request.

## 4. The UI state machine (smali-verified)

`NewSignUpActivity.onSignUpClicked` (mapped to `j21/b.f`):

### 4.1 Password local validation (r21/m.smali:480–606 / j21/b.smali:57–169)

The password must satisfy:
- ≥ 6 chars AND ((≥ 2 char-classes {digit, upper, lower, special} AND ≥ 8 chars) OR (1 char-class AND ≥ 10 chars))

The 4 character-class regexes (from `j21/b.smali:7–11, 20–49`):
- digit: `".*\\d+.*"`
- uppercase: `".*[A-Z]+.*"`
- lowercase: `".*[a-z]+.*"`
- special: `".*[~!@#$%^&*()_+|<>,.?/:;'\\[\\]{}\"]+.*"` — the full list is `~ ! @ # $ % ^ & * ( ) _ + | < > , . ? / : ; ' [ ] { } "`. The escape `\\\\\\\\` and the `\\\"` are smali escapes for a literal backslash and double-quote.

**The BFF's `validatePasswordMin` in `auth-validation.util.ts` checks only `length < 8`** (frontend). The Android smali enforces the stricter 2-class-or-10-char rule. The BFF's frontend validation is therefore **less strict** than the original Android client. For maximum safety on the BFF, the rule should be lifted to match the smali logic (≥2 classes AND ≥8 chars OR ≥1 class AND ≥10 chars).

### 4.2 UI field validation (r21/m.smali:622–988)

In order, the activity validates:
- `learnlang1` (line 622) — first language preference; must be a valid `LearnLang` int. Empty return path at 638 / 645.
- `learnlang2` (line 645) — second language preference; same enum.
- `sex` (line 813) — required, int. Empty return at 813 / 828. The form has a "select gender" radio that the BFF's frontend also surfaces.
- `birthday` (line 742) — required, formatted "yyyy-MM-dd". Empty return at 710.
- `country` (line 752) — required, ISO 3166-1 alpha-2.
- `branch` (line 828) — required, the "I'm learning language" branch choice.

This is the **extended profile signup** flow (r21/m). The simpler email-password path (j21/b) skips all of these — it only collects email + password + verification code, then hits `/v3/check` directly. The BFF's `signup` endpoint matches the email-only path, which is why the `frontend/signup-page.component.ts` has only 2 steps (Account → Verify) and doesn't surface a profile-completion step.

## 5. Anti-cheat & captcha (FINDINGS.md §7.4)

- The bundled "captcha" SDK is **NetEase Yidun risk scoring**, not visual captcha. The visual-captcha library (`com.netease.nis.captcha.Captcha`) is **vendored but never invoked** in the auth flow.
- `HTIRISK_<UUID>` tokens are produced by NetEase `HTProtect` and cached client-side via `IAuthApi.getCaptchaValidateCache()`. On Android, this is the iOS-bundled library since `captcha_validate` is a Yidun attestation; on the BFF, we ship empty strings (treated as "no token" per the Gson-doesn't-serialize-null semantics, which is sufficient for the login flow but NOT for the signup flow — the `irisk_token` field is what gates `/v3/check`).
- `HTProtectConfig` constants (from smali): `PRODUCT_ID=YD00585661468511`, `BUSINESS_ID_V1=aa8b30a27e01e68da891275451237887`, `BUSINESS_ID_V2=8414d72da83bf18800cfe60569d89496`. Wrapped by `HTNeteaseProtectHelper` (also at `com.hellotalk.sign.netease.*`).

## 6. Endpoint inventory (FINDINGS.md §7.5)

| Endpoint | Content-Type | Purpose | In BFF's flow? |
|---|---|---|---|
| `v3/login` | `bin/cc2018` | login (4 overloads: email/password, passkey, signup, social — by `login_type`) | yes (step 6) |
| `v3/pre_login` | `bin/cc2018` | get `cnonce`/`nonce` for password obfuscation (email/phone) | yes (step 5) |
| `v3/check` | `ht/encbin` | terminal signup check (returns `verify_token`, not JWT) | yes (step 4) |
| `v3/reg/prepare` | `ht/encbin` | bind `irisk_token` at signup start | yes (step 1, best-effort) |
| `v3/reg/profile_check` | `ht/encbin` | server-side profile validation | no (independent; not in email flow) |
| `v3/reg/nationality_recommend` | `ht/encbin` | recommend nationality during signup | no |
| `v3/reg/native_lang_recommend` | `ht/encbin` | recommend native language during signup | no |
| `v3/reg/reg` (and `v2/register`) | `ht/encbin` | legacy register paths (under `br/d.smali`) | no |
| `v3/send_email_code` | `ht/encbin` | send email verification code | yes (step 2) |
| `v3/send_sms_code` | `ht/encbin` | send SMS verification code (legacy; new flow uses in-app captcha) | no |
| `v3/send_temp_password` | `ht/encbin` | send temporary password (forgot password) | no |

## 7. The BFF's current state and what changed

### 7.1 The pre-fix problem

Before commit `c4cb9c3`, the BFF's `HelloTalkAuthService.signup` called `signupCheck` with no `irisk_token` field at all (the `SignCheckRequest` record didn't have it). The upstream then returned "code is incorrect, or the account could not be created" because the `/v3/check` handler requires the field. The `regPrepare` call was best-effort and discarded the response.

### 7.2 The fix (`c4cb9c3`)

1. New `RegPrepareResponse` record (`irisk_token: String`) captures the upstream's `HTIRISK_<UUID>`.
2. `HelloTalkAuthClient.regPrepare` returns `Optional<String>` with the token (was `void`).
3. New `encbinExchange` Object-overload so the call can actually read the response (existing `byte[]` overload discarded it).
4. `SignCheckRequest` gains an `irisk_token` field (last in the positional list, matching the wire order in FINDINGS.md). `forEmailSignup` factory takes the token as a new last param.
5. `signupCheck` signature gains the `iriskToken` parameter.
6. `HelloTalkAuthService.signup` calls `regPrepare` first, threads the token (or `""` if the bind failed) into `signupCheck`.

### 7.3 The follow-up logging (`6fcb52e`)

When the upstream returns no `verify_token`, the BFF now logs:
- whether the response was empty (transport / envelope error)
- whether the parsed response lacked a `verify_token` (server accepted but refused to create the account)
- whether the supplied `irisk_token` was non-blank
- the full parsed response object's `toString()` for downstream debugging

If a future test still fails, the BFF log line will show exactly what upstream returned — making any remaining field missing (e.g. `behavior_validate` on the `/v3/check` path, a `htntkey` mismatch, or an upstream `login_type` value mismatch) obvious.

## 8. The frontend's matching code (BFF `JilaliTalk-angular-frontend`)

`/home/mohammed/Desktop/JilaliTalk/JilaliTalk-angular-frontend/src/app/features/auth/pages/signup-page/signup-page.component.ts`:
- **Step 1 (Account)**: `onAccountSubmit` → `signupPrepare` (best-effort, fire-and-forget) + `signupSendEmailCode`. Validates email + password ≥ 8 chars.
- **Step 2 (Verify)**: `onCodeSubmit` → `signupCheck` with `{ email, password, emailVerifyCode }`. Falls through to login on success.
- 4-digit code (matches Android's `CODE_LENGTH = 4`, not 6).

`/home/mohammed/Desktop/JilaliTalk/JilaliTalk-angular-frontend/src/app/core/auth/auth.service.ts` exposes `signupPrepare()`, `signupSendEmailCode(email)`, `signupCheck(req)`, `signupCheckNickname(nickname)`. All four map directly to the BFF routes which proxy to the upstream endpoints above.

## 9. Known gaps and what was verified vs assumed

| Item | Status |
|---|---|
| Email + password signup pipeline (5 steps) | Verified via smali + RE doc — covered |
| `regPrepare` returns `irisk_token` and feeds it into `/v3/check` | Verified via FINDINGS.md §7.3 + smali at `LoginService.b` — now wired (`c4cb9c3`) |
| Password character-class validation | Partially — frontend checks length-only, smali enforces 2-class-or-10-char. BFF `auth-validation.util.ts` has only `length < 8` |
| `behavior_validate` on `/v3/check` | Not confirmed from smali. The RE explicitly says "behavior_validate ... was not re-tested under this finding; treat it independently" (line 133). The BFF sends empty string; if upstream rejects for this, the `6fcb52e` log will surface it |
| `operator` / `sim_country_code` | Both default to `""` (no SIM) in `HelloTalkAuthClientImpl` line ~210. May be required non-blank on real Android devices |
| `htntkey` recomputation | `HtntKeyUtil.compute(deviceId, loginType=1, t)` — uses the embedded secret `"abccdfef#*"` and key table index 3 from `libhellotalk-tea.so`. Verified computed locally |
| `SignCheckReqV3` exact field set | Captured from data class bytecode (FINDINGS.md lines 272–305) — the `partyName`/`partyValue` correction at line 296 is verified |
| `SignCheckResp` exact field set | Captured (FINDINGS.md line 306) — `{user_info, area_code, region_display_policy?, verify_token, banned_info?}` |
| Step-1 `bind_id` value | Uses the BFF's own `device_id` config (FINDINGS.md line 254) — set in `application.yml` via `jilali.device-id` |
| `irisk_token` placeholder | The BFF sends `""` when `regPrepare` fails. Per FINDINGS.md line 129, Gson doesn't serialize null fields, so absent is treated as "no token". Sending empty string is functionally equivalent to absent. If upstream strictly requires field presence with non-empty value, the `6fcb52e` log will surface the error |

## 10. When changing anything in this file

Any change that diverges from the RE must be verified with a live capture:
1. Use a real HelloTalk account + email + a 4-digit code received during testing
2. Capture the actual upstream request/response (mitmproxy or logcat with the iOS app — the smali is iOS-derived per FINDINGS.md line 131)
3. Update the BFF code + this doc in the same commit
4. Run the existing test suite (`./gradlew test`) to catch any regressions

The BFF's auth subsystem has no end-to-end test — it's all mocked at the wire level. Any new test that goes through the actual HelloTalk endpoint should be marked as a manual integration test (not run in CI).
