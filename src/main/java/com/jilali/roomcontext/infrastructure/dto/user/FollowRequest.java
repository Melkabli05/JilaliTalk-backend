package com.jilali.roomcontext.infrastructure.dto.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record FollowRequest(@JsonProperty("follow_uid") long followUid, @JsonProperty("nick_name") String nickName) {}
