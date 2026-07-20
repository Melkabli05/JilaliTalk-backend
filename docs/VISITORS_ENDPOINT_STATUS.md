# Profile Visitors Endpoint — Investigation Status

**Endpoint**: `POST /user_profile_visitor/v2/my_history`
**Account**: uid `169335562`
**Status**: ❌ NOT WORKING (upstream rejects with `{"code":400,"msg":"no data currently"}`)

## What we got right (verified against `re_output` smali)

Both fixes are byte-perfect against the smali ground truth and pass our test suite:

1. **Sign field** (`/v2/my_history` request body) — commit `ce94272`
   - **Algorithm**: `sign = MD5(jid + jid + client_ts).toLowerCase()` where `jid` is the account's own numeric uid
   - **Source**: `bq0/c.smali:1007-1049` + `bq0/g.smali:190-270` (sibling visit endpoint, byte-identical scheme)
   - **Verified live**: independently reproduced byte-for-byte with a standalone Python MD5 computation
   - **Bonus fix**: `update_ts` was `int` (would silently overflow at epoch-ms ~1.78T); changed to `long`
2. **User-Agent header** — commit `bcea416`
   - **Format**: `android;<clientVer>;<deviceModel>;<osVersion>;<jid>`
   - **Source**: `vm/b.smali:170-213` + `org/slf4j/helpers/o.smali:3894-3927` + `constraintlayout/a.smali:137-146`
   - **Verified live**: outbound now reads `android;6.3.70;Redmi Note 13 Pro+#1220X2712#446#438#973#n#6.5;18.5;169335562`
3. **`x-ht-uid` header** — already correct (set by `DefaultHeadersClientFilter.deriveCallerUid()` from the configured JWT; verified in live TRACE capture as `169335562`)
4. **Payload field shape** — `device_type`, `client_ts`, `index`, `device_id`, `sign`, `client_ver`, `update_ts=client_ts`, `client_os` — matches smali lines 953-1005 of `bq0/c.smali`

## What we couldn't fix

Even with sign + User-Agent + UID correct, upstream returns `{"code":400,"msg":"no data currently"}` — the same misleading message as before. This strongly implies **the upstream is rejecting the request as unauthenticated** for a reason we can't reach via static analysis:

- **`X-HT-Session` header** is sent by the real Android client (`vm/b.smali:173-179` + `ja/r.smali:2737+`). It's computed as `base64(xTEA(json{uid, os, version, area_code, session}))`.
- The xTEA encryption key is **not in the smali** — it lives in the native `.so` library (`Lcom/hellotalk/utils/TeaUtils;->xTEAEncryptWithApi`, declared `.method public static synchronized native`). All xTEA methods in `TeaUtils.smali` are native; the Java-side methods are thin wrappers that just delegate to JNI.
- Reimplementing this requires either:
  1. Decompiling `libhellotalk-utils.so` (or whichever `.so` contains the actual encryption logic) with Ghidra/IDA — multi-hour reverse-engineering effort
  2. Running the real APK in an Android emulator with Frida attached, hooking `xTEAEncryptWithApi` to capture the key live

Neither is feasible mid-session without either external setup or a long native-binary reverse-engineering pass.

## Possible other causes (not verified)

- **Device-id blacklisted**: our `device_id` `0cb9173191e58a7bad9bbdc9fede8b9e` was registered for a real iOS device whose original account uid may differ from the uid we now authenticate as. The upstream may tie device-id to a specific jid.
- **JWT expiry/mismatch**: our JWT (`eyJhbGciOi...`) has `exp: 1785467809` (~mid 2026); if that has since expired upstream rejects silently with `code:400`.
- **Different endpoint in production**: the `br/d` URL map in `re_output` shows `/user_profile_visitor/v3/history` exists alongside our targeted v2 path; v3 may have superseded v2 in production while our build still calls v2.

## Recommendation

To fully resolve this, run the real APK in an Android emulator with Frida, hook `TeaUtils.xTEAEncryptWithApi`, capture the key + a sample `X-HT-Session` header value, and reimplement xTEA with that key in our Java BFF. That is the only path the static smali analysis can't reach.
