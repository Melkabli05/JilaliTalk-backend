package com.jilali.signin.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ClaimTaskRewardRequest(
    long host_id,
    String cname,
    int task_id
) {}
