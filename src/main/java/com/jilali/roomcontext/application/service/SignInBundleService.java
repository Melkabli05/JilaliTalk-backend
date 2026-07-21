package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.port.out.SignInUpstreamPort;
import com.jilali.signin.dto.RoomLevelBundleResponse;
import jakarta.inject.Singleton;

import java.util.concurrent.StructuredTaskScope;

/** Extracted from the legacy SigninController.roomLevelBundle's inline fan-out into its own
 *  application service - the new layering doesn't allow business orchestration living directly
 *  in a controller. Same StructuredTaskScope pattern, unchanged. */
@Singleton
public class SignInBundleService {

    private final SignInUpstreamPort upstream;

    public SignInBundleService(SignInUpstreamPort upstream) {
        this.upstream = upstream;
    }

    public RoomLevelBundleResponse roomLevelBundle(String cname, long hostId, int level) {
        try (var scope = StructuredTaskScope.open()) {
            var rewardTask = scope.fork(() -> upstream.roomLevelReward(cname, hostId, level));
            var configTask = scope.fork(() -> upstream.roomLevelConfig(cname, hostId));

            scope.join();

            return new RoomLevelBundleResponse(rewardTask.get(), configTask.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching room level bundle", e);
        } catch (StructuredTaskScope.FailedException e) {
            throw new RuntimeException("Upstream fetch failed during room level bundle", e.getCause());
        }
    }
}
