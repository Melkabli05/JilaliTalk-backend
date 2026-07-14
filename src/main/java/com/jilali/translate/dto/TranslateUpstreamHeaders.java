package com.jilali.translate.dto;

import com.jilali.core.JilaliProperties;
import com.jilali.crypto.Curve25519SessionGenerator.Curve25519Session;

/**
 * The eight HTTP headers the AI translator endpoint requires. Grouped into a record so the
 * upstream call site is declarative (one argument, named fields) rather than a chain of
 * {@code .header("x-...", value)} calls that obscures what's actually being sent.
 * <p>
 * The {@code x-translate-*} family is the translator service's bespoke header set — distinct
 * from the livehub's {@code x-ht-*} family and {@code DefaultHeadersClientFilter}'s defaults, so
 * these can't ride the shared filter and must be set per-request.
 *
 * @param translatePub     the {@code x-translate-pub} value: serverStaticPub + clientEphemeralPub
 * @param translateUid     the {@code x-translate-uid} value: caller's uid (derived from JWT)
 * @param translateOs      the {@code x-translate-os} value: client platform string
 * @param translateBuild   the {@code x-translate-build} value: client build number
 * @param translateVersion the {@code x-translate-version} value: client version string
 * @param userAgent        the {@code User-Agent} header
 * @param authorization    the {@code Authorization} header (Bearer-prefixed JWT)
 * @param accept           the {@code Accept} header (text/event-stream for streaming)
 */
public record TranslateUpstreamHeaders(
        String translatePub,
        String translateUid,
        String translateOs,
        String translateBuild,
        String translateVersion,
        String userAgent,
        String authorization,
        String accept
) {
    private static final String OS_IOS = "ios";
    private static final String BUILD = "70";
    private static final String VERSION = "6.3.0";
    private static final String USER_AGENT =
            "HelloTalk_Binary/70 CFNetwork/3860.500.112 Darwin/25.4.0";
    private static final String ACCEPT_STREAM = "text/event-stream";

    /**
     * Builds a complete header set for one translator request. {@code uid} is the numeric uid
     * claim of the caller — pass {@code 0} when the caller isn't authenticated (the upstream
     * treats it the same as an empty string).
     */
    public static TranslateUpstreamHeaders forSession(
            Curve25519Session session, long uid, JilaliProperties properties
    ) {
        return new TranslateUpstreamHeaders(
                session.headerValue(),
                String.valueOf(uid),
                OS_IOS,
                BUILD,
                VERSION,
                USER_AGENT,
                "Bearer " + properties.defaultAuthToken(),
                ACCEPT_STREAM
        );
    }
}