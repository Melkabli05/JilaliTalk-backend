package com.jilali.roomcontext.infrastructure.dto.translate;

import com.jilali.core.AuthTokenHolder;
import com.jilali.crypto.Curve25519SessionGenerator.Curve25519Session;

/** The HTTP headers the AI translator endpoint requires - the {@code x-translate-*} family is
 *  the translator service's own bespoke header set. */
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
    private static final String USER_AGENT = "HelloTalk_Binary/70 CFNetwork/3860.500.112 Darwin/25.4.0";
    private static final String ACCEPT_STREAM = "text/event-stream";

    public static TranslateUpstreamHeaders forSession(Curve25519Session session, long uid, AuthTokenHolder authToken) {
        return new TranslateUpstreamHeaders(
                session.headerValue(), String.valueOf(uid), OS_IOS, BUILD, VERSION,
                USER_AGENT, "Bearer " + authToken.get(), ACCEPT_STREAM);
    }
}
