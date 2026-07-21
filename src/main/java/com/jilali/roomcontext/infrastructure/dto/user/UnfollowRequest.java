package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UnfollowRequest(@JsonProperty("unfollow_uid") long unfollowUid, @JsonProperty("nick_name") String nickName) {}
