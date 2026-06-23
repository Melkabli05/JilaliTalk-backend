package com.jilali.auth;

import java.time.Instant;

/** A JilaliTalk platform account — independent of any HelloTalk identity (see schema.sql). */
public record AppUser(long id, String email, String passwordHash, String nickname, Instant createdAt) {}
