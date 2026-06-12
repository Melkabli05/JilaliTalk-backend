package com.jilali.stage.dto;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Plain (decrypted) Agora RTC token carrying publisher privilege. The join token from
 * {@code voice_room_info} only grants subscriber rights; a client must renew with this
 * token before publishing audio (mirrors the original HelloTalk web client's
 * {@code switchToPublisher} flow).
 */
@Serdeable
public record PublisherTokenResponse(String token) {
}
