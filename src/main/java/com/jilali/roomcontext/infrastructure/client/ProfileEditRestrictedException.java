package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.infrastructure.dto.user.ProfileLimitationsResponse;

/**
 * Thrown by {@link ProfileUpstreamAdapter#edit} when the cached limitations payload reports
 * {@code is_modify_restricted = true}. Surfaces server-side ban to the profile-edit flow
 * without trusting the upstream response (which is unsigned JSON over TLS — the BFF's cache
 * is the source of truth).
 *
 * <p>Caller-facing semantics: the frontend should render a banner (same copy as the
 * /profile/v2/limitations banner driven by ProfileStore.isModifyRestricted) and disable
 * the edit form. The toast that ProfileStore.guardProfileEdit() fires on action-click is
 * the action-time counterpart.
 *
 * <p>This is not a typed HTTP status — Micronaut serializes RuntimeException to 500 by
 * default. The intent here is to be caught by a controller-level exception handler that
 * maps it to {@code 423 Locked} or {@code 403 Forbidden} depending on upstream convention
 * (TODO: wire that handler when the {@code profile/edit} controller is built out).
 */
public class ProfileEditRestrictedException extends RuntimeException {

    private final ProfileLimitationsResponse.LimitationsData limitations;

    public ProfileEditRestrictedException(ProfileLimitationsResponse.LimitationsData limitations) {
        super("Profile edit refused: account is currently is_modify_restricted");
        this.limitations = limitations;
    }

    public ProfileLimitationsResponse.LimitationsData getLimitations() {
        return limitations;
    }
}
