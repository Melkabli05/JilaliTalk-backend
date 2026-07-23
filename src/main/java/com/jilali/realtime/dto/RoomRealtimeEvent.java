package com.jilali.realtime.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

// Wire keys are camelCase: every record below is constructed by hand in HtNotifyMapper —
// never deserialized from JSON — so there is no upstream snake_case shape to mirror here.
// (Contrast with com.jilali.room.dto, where the same record both reads upstream's
// snake_case JSON and is returned to our frontend, so @JsonProperty(snake_case) is load-bearing.)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ConnectionState.class,    name = "connection-state"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.UserJoin.class,           name = "user_join"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.UserQuit.class,           name = "user_quit"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageJoin.class,          name = "stage_join"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageQuit.class,          name = "stage_quit"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageRaiseHand.class,     name = "stage_raisehand"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageInvite.class,        name = "stage_invite"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Comment.class,            name = "comment"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Gift.class,               name = "gift"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageDeviceControl.class, name = "stage_device_control"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModInvite.class,          name = "mod_invite"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.WhiteboardActivated.class, name = "whiteboard_activated"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.WhiteboardDeactivated.class, name = "whiteboard_deactivated"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.MicOpened.class,          name = "mic_opened"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.MicClosed.class,          name = "mic_closed"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModUnmuted.class,         name = "mod_unmuted"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.RoomKick.class,           name = "room_kick"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageKick.class,           name = "stage_kick"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModAccepted.class,         name = "mod_accepted"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModRemoved.class,          name = "mod_removed"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Follow.class,             name = "follow"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.LuckyBag.class,           name = "lucky_bag"),
    // Type 47 — topic/category share dropped into the room (e.g. "English learning" topic card).
    @JsonSubTypes.Type(value = RoomRealtimeEvent.RoomTopicShare.class,     name = "room_topic_share"),
    // Type 7 — props / bubble skin applied in the room by a user (the cosmetic-bubble prop system).
    @JsonSubTypes.Type(value = RoomRealtimeEvent.RoomPropsApplied.class,   name = "room_props_applied"),
    // LiveWSSGiftWish — goal-gift running total in the room.
    @JsonSubTypes.Type(value = RoomRealtimeEvent.GiftWish.class,           name = "gift_wish"),
    // LiveWSSRewardInfo — per-user reward envelope (the `reward_list` array).
    @JsonSubTypes.Type(value = RoomRealtimeEvent.RewardInfo.class,         name = "reward_info"),
    // LiveWSSPurchaseVip — VIP-purchase banner piggybacked onto the room.
    @JsonSubTypes.Type(value = RoomRealtimeEvent.PurchaseVip.class,        name = "purchase_vip"),
    // LiveWSSReceiveVipGifts — recipient of VIP gifts in the room.
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ReceiveVipGifts.class,    name = "receive_vip_gifts"),
    // LiveWSSTreasureReward — the big treasure/camp-reward popup (composes CampResult + RewardInfo).
    @JsonSubTypes.Type(value = RoomRealtimeEvent.TreasureReward.class,     name = "treasure_reward"),
    // FgUpgradeAward — family-group tier-upgrade reward toast.
    @JsonSubTypes.Type(value = RoomRealtimeEvent.FgUpgradeAward.class,     name = "fg_upgrade_award"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Raw.class,                name = "raw"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Error.class,              name = "error"),
})
public sealed interface RoomRealtimeEvent permits
    RoomRealtimeEvent.ConnectionState,
    RoomRealtimeEvent.UserJoin,
    RoomRealtimeEvent.UserQuit,
    RoomRealtimeEvent.StageJoin,
    RoomRealtimeEvent.StageQuit,
    RoomRealtimeEvent.StageRaiseHand,
    RoomRealtimeEvent.StageInvite,
    RoomRealtimeEvent.Comment,
    RoomRealtimeEvent.Gift,
    RoomRealtimeEvent.StageDeviceControl,
    RoomRealtimeEvent.ModInvite,
    RoomRealtimeEvent.WhiteboardActivated,
    RoomRealtimeEvent.WhiteboardDeactivated,
    RoomRealtimeEvent.MicOpened,
    RoomRealtimeEvent.MicClosed,
    RoomRealtimeEvent.ModUnmuted,
    RoomRealtimeEvent.RoomKick,
    RoomRealtimeEvent.StageKick,
    RoomRealtimeEvent.ModAccepted,
    RoomRealtimeEvent.ModRemoved,
    RoomRealtimeEvent.Follow,
    RoomRealtimeEvent.LuckyBag,
    RoomRealtimeEvent.RoomTopicShare,
    RoomRealtimeEvent.RoomPropsApplied,
    RoomRealtimeEvent.GiftWish,
    RoomRealtimeEvent.RewardInfo,
    RoomRealtimeEvent.PurchaseVip,
    RoomRealtimeEvent.ReceiveVipGifts,
    RoomRealtimeEvent.TreasureReward,
    RoomRealtimeEvent.FgUpgradeAward,
    RoomRealtimeEvent.Raw,
    RoomRealtimeEvent.Error {

    record ConnectionState(String state) implements RoomRealtimeEvent {}

    record UserJoin(String userId, String nickname, String headUrl, String nationality, boolean isBannedComment) implements RoomRealtimeEvent {}

    record UserQuit(String userId) implements RoomRealtimeEvent {}

    /**
     * Mirrors the {@code LiveWSSRoomUser} Android entity. Earlier this only carried
     * {@code user_id/nickname/head_url}, silently dropping every visual-identity field the
     * server actually sends on a {@code notify_type: 4 / 23} stage-join push (role, seat,
     * VIP crown, bubble skin, enter-effect, ripple, FG badge, …). The frontend then had
     * nothing to render stage-user badges from, and had to wait for the REST roster pull
     * to repaint them. Now the realtime push carries enough to render correctly on first
     * arrival.
     *
     * <p>Field names match the upstream snake_case keys verbatim; the mapper just lower-cases
     * the first letter when constructing. Anything missing from the wire is null/-1/false here.
     */
    record StageUserEvent(
        String userId,
        String nickname,
        String headUrl,
        String nationality,
        int role,                          // 1 = host, 2 = mod, 3 = audience (live convention; see Android smali)
        int seatId,
        String vipLogo,
        String vipLogoAnim,
        int vipType,
        int dayRankLevel,
        int giftLevel,
        int gType,
        long screenShareUid,
        int enterEffectId,
        int enterEffectAnimalType,
        String enterEffectAnimalUrl,
        int enterEffectPaid,
        int rippleAnimalType,
        String rippleAnimalUrl,
        String rippleUrl,
        boolean isOnStage,
        boolean isFirstJoin,
        boolean isInRoom,
        boolean isTurnOnMic,
        boolean isTurnOnCam,
        String inviteUserId,
        String inviteNickname,
        String inviteHeadUrl,
        String inviteNationality,
        // Bubble / FG fields also present on the comment frame — same carry-over shape.
        int bubbleId,
        String bubbleUrl,
        String bubbleColor,
        int bubbleAnimalType,
        String bubbleAnimalUrl,
        int fgLevel,
        String fgName,
        boolean fgIsActive,
        long followerId,
        long followeeId,
        int audienceTotal,
        int raiseHandCount,
        String medalWallIcon,
        long joinTime,                     // ms epoch; 0 if absent
        long createdAt,                    // ms epoch; 0 if absent
        int pinnedStatus,
        String pinnedType,
        int teamIndex,
        // Status / metadata carry-overs (used by other room-event branches on the same entity).
        int status,
        int type,
        String name,
        String label,
        int level,
        String reason,
        String notice,
        String tipText,
        String shareStatus,
        String location,
        // True only for a synthetic stage_join the BFF itself constructs — see
        // GhostPublishController. Every real upstream stage_join maps this to false;
        // upstream has no concept of a ghost publisher, so this is purely a BFF-mediated
        // signal telling the frontend "this uid is publishing audio but was never added
        // to the audience/stage roster upstream — subscribe for audio, render with the
        // ghost badge, and don't treat this as a real stage seat."
        boolean isGhost
    ) {}

    record StageJoin(StageUserEvent stageUser) implements RoomRealtimeEvent {}

    record StageQuit(String userId) implements RoomRealtimeEvent {}

    record StageRaiseHand(String userId, int raisehandType) implements RoomRealtimeEvent {}

    record StageInvite(String userId) implements RoomRealtimeEvent {}

    record ReplyInfoEvent(
        String msgId,
        long fromId,
        String fromNickname,
        String text,
        String msgType,
        long sendTime                     // ms epoch; 0 if absent (added — was missing on Android LiveWSSReplyInfo)
    ) {}

    /**
     * Carries every decoration field the live comment frame actually supplies (role, VIP, bubble
     * skin + companion animal, family-group badge, nationality) — earlier this record only had
     * id/userId/nickname/headUrl/text/ts/replyInfo, so a paying user's equipped bubble skin and
     * FG badge silently vanished from their own LIVE messages, only reappearing after a refresh
     * pulled the same comment back through the REST history endpoint, which always mapped these
     * fields correctly.
     */
    record CommentEvent(
        String id,
        String msgId,                      // also present (Android LiveWSSMessage.msg_id) — was dropped
        String userId,
        String nickname,
        String headUrl,
        String text,
        long ts,
        long serverTime,                   // ms epoch; 0 if absent (server_ts on the wire)
        long sendTime,                     // ms epoch; 0 if absent
        String msgModel,                   // client-side comment render variant id
        String source,                     // web / app / etc.
        int fromProfileTs,                 // profile-tour marker (1 = profile-tour post)
        ReplyInfoEvent replyInfo,
        String nationality,
        int role,
        int vipType,
        int dayRankLevel,
        int giftLevel,
        int fgLevel,
        String fgName,
        boolean fgIsActive,
        int bubbleId,
        String bubbleUrl,
        String bubbleColor,
        int hitBad,
        int bubbleAnimalType,
        String bubbleAnimalUrl) {}

    record Comment(CommentEvent comment) implements RoomRealtimeEvent {}

    record GiftEvent(
        String sendUid,
        String sendNickname,
        String sendHeadUrl,
        String sendNation,
        String receiverUid,
        String receiverNickname,
        String receiverHeadUrl,
        String receiverNation,
        String smallPic,
        long giftId,
        int giftNumber,
        long giftVal,
        int vipType,
        int giftLevel,
        int dayRankLevel) {}

    record Gift(List<GiftEvent> gifts) implements RoomRealtimeEvent {}

    record StageDeviceControl(String userId, int deviceType, int switchType) implements RoomRealtimeEvent {}

    record ModInvite(String userId) implements RoomRealtimeEvent {}

    // Whiteboard
    record WhiteboardActivated(String cname) implements RoomRealtimeEvent {}
    record WhiteboardDeactivated(String cname) implements RoomRealtimeEvent {}

    // Mic on/off (user-initiated, not mod-initiated)
    record MicOpened(String userId) implements RoomRealtimeEvent {}
    record MicClosed(String userId) implements RoomRealtimeEvent {}

    /**
     * Type 40 — a mod lifted a mute (the counterpart to {@code StageDeviceControl} on type 30).
     * Deliberately not folded into {@link MicOpened}: the reference client (scriptv2.js:5262)
     * explicitly does NOT flip the mic-on roster state here (that line is commented out) — being
     * allowed to speak again is not the same as actively speaking, so this only ever drives a
     * "you can speak now" toast for the affected user, never a roster update.
     */
    record ModUnmuted(String userId) implements RoomRealtimeEvent {}

    // Kicked from room
    record RoomKick(
        String userId,
        String nickname,
        String managerName,
        String cname
    ) implements RoomRealtimeEvent {}

    // Kicked from stage (by mod)
    record StageKick(
        String userId,
        String managerName,
        String cname
    ) implements RoomRealtimeEvent {}

    // Role changes
    record ModAccepted(String userId) implements RoomRealtimeEvent {}
    record ModRemoved(String userId) implements RoomRealtimeEvent {}

    // Follow
    record Follow(String userId, String nickname, String headUrl, int status) implements RoomRealtimeEvent {} // 1 = followed you, 2 = followed back

    /**
     * A lucky bag giveaway started in the room. Field names mirror {@code VoiceRoomInfoObjects.LuckBag}
     * (the REST {@code voice_room_info.luck_bag} shape) — same lucky-bag subsystem, confirmed real
     * field names from captured traffic, reused here since the push carries the same {@code lucky_bag_id}
     * marker this mapper already keys off of.
     */
    record LuckyBag(String luckyBagId, int luckyBagNumber, String cname) implements RoomRealtimeEvent {}

    /**
     * Type 47 — a topic/category card was dropped into the room (e.g. "English learning" with
     * {@code category_id=1053, topic_id=2056}). Carries enough for the frontend to render a
     * clickable topic card; tapping it deep-links into the topic's moment/topic page.
     */
    record RoomTopicShare(
        String cname,
        long categoryId,
        long topicId,
        String name
    ) implements RoomRealtimeEvent {}

    /**
     * Type 7 — a user applied a cosmetic props/bubble skin in the room. Carries the full set of
     * bubble / props URLs the Android client renders (small, list, big-background, sound-wave,
     * top-list) plus the paid-tier marker so the frontend can decide which effects to play.
     */
    record RoomPropsApplied(
        String cname,
        String userId,
        long propsId,
        int propsType,
        int animalType,
        String animalUrlV2,
        String listBackgroundUrl,
        String roomBigBackgroundUrl,
        String soundWaveUrl,
        String topListBackgroundUrl,
        int backgroundPaid
    ) implements RoomRealtimeEvent {}

    /**
     * LiveWSSGiftWish — the room's gift-wish progress (goal-gift). The Android entity is
     * shaped exactly to: {@code {gift_id, small_pic, config_gift_count, received_gift_count,
     * virtual_val}}.
     */
    record GiftWish(
        long giftId,
        String smallPic,
        int configGiftCount,
        int receivedGiftCount,
        long virtualVal
    ) implements RoomRealtimeEvent {}

    /**
     * LiveWSSRewardInfo — per-user reward envelope. Wraps one or more
     * {@link Reward} entries (the {@code reward_list} array). The Android entity also carries
     * {@code user_id/nickname/head_url/nation} identifying whose reward list this is.
     */
    record RewardInfo(
        String userId,
        String nickname,
        String headUrl,
        String nation,
        List<Reward> rewards
    ) implements RoomRealtimeEvent {}

    /**
     * LiveWSSReward — a single reward entry inside a show popup (or the {@code reward_list} of
     * a {@link RewardInfo}). The Android entity also carries {@code reward_id, award_type,
     * name, number, animal_type, animal_url, virtual_val, is_mystery_gift}.
     */
    record Reward(
        long rewardId,
        int awardType,
        String name,
        int number,
        int animalType,
        String animalUrl,
        long virtualVal,
        boolean isMysteryGift
    ) {}

    /**
     * LiveWSSPurchaseVip — VIP-purchase banner pushed onto the room channel (someone just
     * bought VIP, the room shows a celebratory banner). Mirror of the Android entity's fields.
     */
    record PurchaseVip(
        String cname,
        String sendUid,
        long giftId,
        String giftName,
        int giftType,
        int giftNumber,
        String label,
        String smallPic,
        String title
    ) implements RoomRealtimeEvent {}

    /**
     * LiveWSSReceiveVipGifts — recipient side of a VIP-gift transaction pushed onto the room
     * channel. Distinct from {@link PurchaseVip}: this fires for the receiver, not the buyer.
     */
    record ReceiveVipGifts(
        String cname,
        String sendUserId,
        String sendNickName,
        int sendType,
        long vipTime,                      // ms epoch; 0 if absent
        long showTime                      // ms epoch; 0 if absent
    ) implements RoomRealtimeEvent {}

    /**
     * Camp/vote tally nested under {@link TreasureReward#campResult()}. Mirrors
     * {@code CampResultEntity} on Android: {@code option_left_name, option_right_name,
     * option_result, vote_count_left, vote_count_right}. {@code option_result} indicates the
     * winning side (1 = left, 2 = right, 0 = tie/unknown).
     */
    record CampResult(
        String optionLeftName,
        String optionRightName,
        int optionResult,
        int voteCountLeft,
        int voteCountRight
    ) {}

    /**
     * LiveWSSTreasureReward — the big "treasure" / camp-reward popup (colored, ranked, with
     * the full set of styling fields the Android client reads to paint the popup). Composes
     * a {@link CampResult} and a {@link RewardInfo}, plus the show-style metadata.
     */
    record TreasureReward(
        String title,
        CampResult campResult,
        RewardInfo rewardInfo,
        String taskTypeNew,
        int openCycle,
        int openLevel,
        int animalType,
        String animalUrl,
        List<String> participateUserIds,
        List<String> rewardUserIds,
        List<String> noPrivilegeUserIds,
        String rewardPopupColor,
        String mainTextColor,
        String subTextColor,
        String taskDescColor
    ) implements RoomRealtimeEvent {}

    /**
     * FgUpgradeAward — a family-group tier-upgrade reward toast. Android entity fields:
     * {@code id, typ, icon, content}. The field is named {@code awardType} (not {@code type})
     * to avoid colliding with the {@code type} discriminator on this sealed union — the BFF
     * mapper reads the wire key {@code typ} and emits {@code awardType} on the JSON the
     * frontend consumes.
     */
    record FgUpgradeAward(
        long id,
        String awardType,
        String icon,
        String content
    ) implements RoomRealtimeEvent {}

    record Raw(String originalType, Object payload) implements RoomRealtimeEvent {}

    record Error(String message) implements RoomRealtimeEvent {}
}
