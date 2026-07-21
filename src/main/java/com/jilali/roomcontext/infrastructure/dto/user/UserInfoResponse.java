package com.jilali.roomcontext.infrastructure.dto.user;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@Serdeable
public record UserInfoResponse(
    int code,
    @Nullable String msg,
    @Nullable UserInfoData data
) {
    @Serdeable
    public record UserInfoData(@JsonProperty("list") List<UserInfoItem> list) {}

    @Serdeable
    public record UserInfoItem(
        @JsonProperty("user_id") long userId,
        @Nullable BaseInfo base,
        @Nullable PointsInfo points,
        @Nullable TagsInfo tags,
        @Nullable LocationInfo location,
        @Nullable RelationInfo relation,
        @Nullable PrivilegesInfo privileges,
        @JsonProperty("online_state") @Nullable OnlineStateInfo onlineState,
        @JsonProperty("live_state") @Nullable LiveStateInfo liveState,
        @JsonProperty("default") @Nullable DefaultInfo defaults,
        @JsonProperty("gift_level") @Nullable Integer giftLevel,
        @JsonProperty("pay_info") @Nullable PayInfo payInfo,
        @Nullable RemarkInfo remark
    ) {}

    @Serdeable
    public record BaseInfo(
        @Nullable String username,
        @Nullable String nickname,
        @JsonProperty("full_py") @Nullable String fullPy,
        @JsonProperty("short_py") @Nullable String shortPy,
        @JsonProperty("native_lang") @Nullable Integer nativeLang,
        @JsonProperty("account_type") @Nullable String accountType,
        @JsonProperty("user_type") @Nullable Integer userType,
        @Nullable Integer sex,
        @Nullable String nationality,
        @Nullable String birthday,
        @Nullable Integer age,
        @Nullable String signature,
        @Nullable Integer timezone,
        @JsonProperty("timezone48") @Nullable Integer timezone48,
        @JsonProperty("reg_time") @Nullable Long regTime,
        @JsonProperty("reg_days") @Nullable Integer regDays,
        @JsonProperty("teach_langs") @Nullable List<LangInfo> teachLangs,
        @JsonProperty("learn_langs") @Nullable List<LangInfo> learnLangs,
        @JsonProperty("head_url") @Nullable String headUrl,
        @JsonProperty("voice_url") @Nullable String voiceUrl,
        @JsonProperty("voice_duration") @Nullable Integer voiceDuration,
        @JsonProperty("userinfo_ts") @Nullable Long userinfoTs,
        @JsonProperty("cover_picture_url") @Nullable String coverPictureUrl,
        @JsonProperty("photo_cover_url") @Nullable String photoCoverUrl,
        @JsonProperty("vip_type") @Nullable Integer vipType,
        @JsonProperty("vip_expire_time") @Nullable Long vipExpireTime,
        @JsonProperty("vip_logo") @Nullable String vipLogo,
        @JsonProperty("vip_logo_anim") @Nullable String vipLogoAnim,
        @JsonProperty("hw_vip") @Nullable Boolean hwVip,
        @JsonProperty("english_ai_vip") @Nullable Boolean englishAiVip,
        @JsonProperty("ripple_anim_url") @Nullable String rippleAnimUrl,
        @JsonProperty("ripple_thumb") @Nullable String rippleThumb,
        @JsonProperty("language_ai_vip") @Nullable Boolean languageAiVip,
        @JsonProperty("bubble_animal_url_dark") @Nullable String bubbleAnimalUrlDark,
        @JsonProperty("bubble_text_color_dark") @Nullable String bubbleTextColorDark,
        @JsonProperty("bubble_animal_url") @Nullable String bubbleAnimalUrl,
        @JsonProperty("bubble_text_color") @Nullable String bubbleTextColor,
        @JsonProperty("bubble_id") @Nullable Integer bubbleId,
        @JsonProperty("user_extra_info") @Nullable UserExtraInfo userExtraInfo
    ) {}

    @Serdeable
    public record LangInfo(
        @JsonProperty("lang_id") @Nullable Integer langId,
        @Nullable Integer level,
        @JsonProperty("short_name") @Nullable String shortName,
        @JsonProperty("full_name") @Nullable String fullName
    ) {}

    @Serdeable
    public record UserExtraInfo(
        @JsonProperty("hide_vip_identity") @Nullable Integer hideVipIdentity,
        @JsonProperty("is_expert") @Nullable Boolean isExpert,
        @JsonProperty("is_new_user") @Nullable Boolean isNewUser,
        @JsonProperty("vip_plus_expire_ts") @Nullable Long vipPlusExpireTs,
        @JsonProperty("vip_plus_logo") @Nullable String vipPlusLogo,
        @JsonProperty("vip_plus_logo_anim") @Nullable String vipPlusLogoAnim
    ) {}

    @Serdeable
    public record PointsInfo(
        @Nullable Integer collect,
        @Nullable Integer correct,
        @Nullable Integer exchange,
        @Nullable Integer read,
        @JsonProperty("speech_to_text") @Nullable Integer speechToText,
        @JsonProperty("text_translate") @Nullable Integer textTranslate,
        @Nullable Integer transliterate,
        @Nullable Integer word,
        @Nullable Integer translate
    ) {}

    @Serdeable
    public record TagsInfo(
        @Nullable List<TagItem> hobby,
        @Nullable List<TagItem> occupation,
        @Nullable List<TagItem> education,
        @Nullable List<TagItem> hometown,
        @Nullable List<TagItem> travelling,
        @Nullable List<TagItem> mbti,
        @JsonProperty("zodiac_sign") @Nullable List<TagItem> zodiacSign,
        @JsonProperty("blood_type") @Nullable List<TagItem> bloodType
    ) {}

    @Serdeable
    public record TagItem(
        @JsonProperty("tag_id") @Nullable Integer tagId,
        @Nullable Integer tid,
        @Nullable Integer type,
        @Nullable Integer cate,
        @Nullable String tag,
        @Nullable String icon
    ) {}

    @Serdeable
    public record LocationInfo(
        @JsonProperty("allow_location") @Nullable Integer allowLocation,
        @Nullable String city,
        @JsonProperty("full_country") @Nullable String fullCountry,
        @JsonProperty("country_code") @Nullable String countryCode,
        @Nullable String longitude,
        @Nullable String latitude,
        @JsonProperty("map_lat") @Nullable String mapLat,
        @JsonProperty("map_lng") @Nullable String mapLng,
        @JsonProperty("goto_map_url") @Nullable String gotoMapUrl,
        @JsonProperty("map_image_url") @Nullable String mapImageUrl,
        @JsonProperty("image_url") @Nullable String imageUrl,
        @JsonProperty("update_ts") @Nullable Long updateTs,
        @JsonProperty("force_show") @Nullable Integer forceShow,
        @JsonProperty("addr_id") @Nullable String addrId,
        @JsonProperty("search_city_val") @Nullable String searchCityVal
    ) {}

    @Serdeable
    public record RelationInfo(
        @Nullable Integer followers,
        @Nullable Integer following,
        @Nullable Integer likes,
        @Nullable Integer moments,
        @Nullable Integer comments,
        @Nullable Integer visitors,
        @JsonProperty("recent_visitors") @Nullable Integer recentVisitors,
        @JsonProperty("invisible_visitors") @Nullable Integer invisibleVisitors,
        @JsonProperty("moment_view_count") @Nullable Integer momentViewCount
    ) {}

    @Serdeable
    public record PrivilegesInfo(
        @JsonProperty("hide_age") @Nullable Integer hideAge,
        @JsonProperty("hide_city") @Nullable Integer hideCity,
        @JsonProperty("hide_online") @Nullable Integer hideOnline,
        @JsonProperty("hide_location") @Nullable Integer hideLocation,
        @JsonProperty("hide_live_status") @Nullable Integer hideLiveStatus,
        @JsonProperty("hide_web_profile") @Nullable Integer hideWebProfile,
        @Nullable Integer privileges,
        @JsonProperty("show_zodiac_sign") @Nullable Integer showZodiacSign
    ) {}

    @Serdeable
    public record OnlineStateInfo(
        @JsonProperty("online_state") @Nullable Integer onlineState,
        @JsonProperty("area_code") @Nullable String areaCode,
        @JsonProperty("terminal_type") @Nullable Integer terminalType,
        @JsonProperty("online_ts") @Nullable Long onlineTs
    ) {}

    @Serdeable
    public record LiveStateInfo(
        @JsonProperty("status_type") @Nullable Integer statusType,
        @Nullable String cname
    ) {}

    @Serdeable
    public record DefaultInfo(
        @JsonProperty("consecutive_days") @Nullable Integer consecutiveDays,
        @JsonProperty("is_scammer") @Nullable Integer isScammer,
        @JsonProperty("profile_share_url") @Nullable String profileShareUrl,
        @JsonProperty("user_flag") @Nullable Integer userFlag
    ) {}

    @Serdeable
    public record PayInfo(
        @JsonProperty("background_type") @Nullable Integer backgroundType,
        @Nullable String background,
        @JsonProperty("background_black") @Nullable String backgroundBlack,
        @JsonProperty("medal_icon") @Nullable String medalIcon
    ) {}

    @Serdeable
    public record RemarkInfo(
        @JsonProperty("remark_full_py") @Nullable String remarkFullPy,
        @JsonProperty("remark_short_py") @Nullable String remarkShortPy,
        @JsonProperty("remark_name") @Nullable String remarkName,
        @JsonProperty("remark_info") @Nullable String remarkInfo
    ) {}

    public UserInfo toUserInfo() {
        if (data == null || data.list() == null || data.list().isEmpty()) {
            return null;
        }
        var item = data.list().get(0);
        var base = item.base();
        return new UserInfo(
            item.userId(),
            base != null ? base.username() : null,
            base != null ? base.nickname() : null,
            base != null ? base.birthday() : null,
            base != null ? base.accountType() : null,
            base != null ? base.fullPy() : null,
            base != null ? base.age() : null,
            base != null && base.sex() != null ? UserInfo.mapSex(base.sex()) : null,
            base != null ? base.nationality() : null,
            item.location() != null ? item.location().city() : null,
            item.location() != null ? item.location().fullCountry() : null,
            item.onlineState() != null ? item.onlineState().areaCode() : null,
            base != null ? base.regDays() : null,
            item.liveState() != null ? item.liveState().cname() : null,
            flattenTags(item.tags()),
            item
        );
    }

    private static List<String> flattenTags(TagsInfo tags) {
        if (tags == null) return List.of();
        return java.util.stream.Stream.of(
                tags.hobby(), tags.occupation(), tags.education(), tags.hometown(),
                tags.travelling(), tags.mbti(), tags.zodiacSign(), tags.bloodType()
        )
        .flatMap(List::stream)
        .map(TagItem::tag)
        .filter(t -> t != null && !t.isBlank())
        .toList();
    }
}
