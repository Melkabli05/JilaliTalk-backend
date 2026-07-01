# Task 2: Move `inflate`/`copyPayload` into `HtImPacketFramer`

## Context
Task 1 created shared WS utilities. Task 2 adds two methods to the existing `HtImPacketFramer` in `com.jilali.im`. This is a refactor only — no behavior change.

## File to modify
`src/main/java/com/jilali/im/HtImPacketFramer.java`

## Exact additions

### New imports to add (after the existing import block)
```java
import java.util.Arrays;
import java.util.zip.Inflater;
```

### New methods to add after the `deflate` method (before `parseHeader`)
```java
    /** Copy the payload region (after the 20-byte header) out of a raw inbound packet. */
    static byte[] copyPayload(byte[] data, int payloadLen) {
        byte[] payload = new byte[payloadLen];
        System.arraycopy(data, HEADER_LEN, payload, 0, payloadLen);
        return payload;
    }

    /** Zlib-inflate a byte array; if it isn't zlib-compressed (doesn't start with 0x78),
     *  returns it unchanged. Tries both wrapped and raw-deflate modes since HelloTalk's
     *  server has been observed sending both. */
    static byte[] inflate(byte[] data) {
        if (data == null || data.length == 0) return data;
        if ((data[0] & 0xFF) != 0x78) return data; // not zlib compressed

        for (boolean nowrap : new boolean[]{false, true}) {
            try {
                Inflater inf = new Inflater(nowrap);
                inf.setInput(data);
                byte[] out = new byte[data.length * 8];
                int n = inf.inflate(out);
                inf.end();
                return Arrays.copyOf(out, n);
            } catch (Exception ignored) {
                // try the other mode
            }
        }
        return null;
    }
```

## Test contract
Run `./gradlew compileJava` (should succeed). Run `./gradlew test` (all existing tests must pass — no new tests needed for this refactor).

## Report file
`/home/mohammed/Desktop/JilaliTalk/jilalibff/.superpowers/sdd/task-2-report.md`
## Commit message
`refactor(im): move inflate/copyPayload into HtImPacketFramer`

## Working directory
`/home/mohammed/Desktop/JilaliTalk/jilalibff`
