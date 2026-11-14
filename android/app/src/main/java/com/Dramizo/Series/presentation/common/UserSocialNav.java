package com.Dramizo.Series.presentation.common;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.Dramizo.Series.R;
import com.Dramizo.Series.presentation.friends.FriendsActivity;
import com.Dramizo.Series.presentation.profile.ProfileActivity;
import com.Dramizo.Series.presentation.profile.SocialListActivity;
import com.Dramizo.Series.presentation.profile.VisitorsActivity;

/**
 * Navigates from Mikoo user-card / profile social + property cells.
 */
public final class UserSocialNav {
    private UserSocialNav() {}

    public static final String KIND_FOLLOWERS = SocialListActivity.KIND_FOLLOWERS;
    public static final String KIND_FOLLOWING = SocialListActivity.KIND_FOLLOWING;
    public static final String KIND_FRIENDS = SocialListActivity.KIND_FRIENDS;

    /** Wire card cells: متابعون · مشجع · زائر + عائلة · داعمون · أصدقاء. */
    public static void wireUserCard(
            @NonNull FragmentActivity activity,
            @NonNull View sheet,
            @NonNull String targetUserId,
            @Nullable String myUserId,
            @Nullable Runnable dismiss) {
        final String uid = targetUserId.trim();
        if (uid.isEmpty()) return;

        View cellFollowers = sheet.findViewById(R.id.cellStatFollowers);
        View cellFans = sheet.findViewById(R.id.cellStatFans);
        View cellVisitors = sheet.findViewById(R.id.cellStatVisitors);
        View propSupporters = sheet.findViewById(R.id.propSupporters);
        View propFriends = sheet.findViewById(R.id.propFriends);

        if (cellFollowers != null) {
            cellFollowers.setOnClickListener(v -> {
                if (dismiss != null) dismiss.run();
                openFollowers(activity, uid, myUserId);
            });
        }
        if (cellFans != null) {
            cellFans.setOnClickListener(v -> {
                if (dismiss != null) dismiss.run();
                openFollowing(activity, uid, myUserId);
            });
        }
        if (cellVisitors != null) {
            cellVisitors.setOnClickListener(v -> {
                if (dismiss != null) dismiss.run();
                openVisitors(activity, uid, myUserId);
            });
        }
        if (propSupporters != null) {
            propSupporters.setOnClickListener(v -> {
                if (dismiss != null) dismiss.run();
                openFollowers(activity, uid, myUserId);
            });
        }
        if (propFriends != null) {
            propFriends.setOnClickListener(v -> {
                if (dismiss != null) dismiss.run();
                openFriends(activity, uid, myUserId);
            });
        }
    }

    public static void bindFamilyClick(
            @NonNull FragmentActivity activity,
            @Nullable View propFamily,
            @Nullable String agencyId,
            @Nullable Runnable dismiss) {
        if (propFamily == null) return;
        if (agencyId == null || agencyId.isEmpty()) {
            propFamily.setOnClickListener(v ->
                    Toast.makeText(activity, R.string.user_card_no_family, Toast.LENGTH_SHORT).show());
            return;
        }
        final String aid = agencyId;
        propFamily.setOnClickListener(v -> {
            if (dismiss != null) dismiss.run();
            AgencyFamilyInfoSheet.show(activity, aid);
        });
    }

    public static void openFollowers(
            @NonNull FragmentActivity a, @NonNull String userId, @Nullable String myId) {
        if (isSelf(userId, myId)) {
            Intent i = new Intent(a, FriendsActivity.class);
            i.putExtra(FriendsActivity.EXTRA_TAB, 2); // fans
            a.startActivity(i);
            return;
        }
        SocialListActivity.open(a, userId, KIND_FOLLOWERS, a.getString(R.string.user_card_stat_following));
    }

    public static void openFollowing(
            @NonNull FragmentActivity a, @NonNull String userId, @Nullable String myId) {
        if (isSelf(userId, myId)) {
            Intent i = new Intent(a, FriendsActivity.class);
            i.putExtra(FriendsActivity.EXTRA_TAB, 1); // following
            a.startActivity(i);
            return;
        }
        SocialListActivity.open(a, userId, KIND_FOLLOWING, a.getString(R.string.user_card_stat_fans));
    }

    public static void openFriends(
            @NonNull FragmentActivity a, @NonNull String userId, @Nullable String myId) {
        if (isSelf(userId, myId)) {
            Intent i = new Intent(a, FriendsActivity.class);
            i.putExtra(FriendsActivity.EXTRA_TAB, 0);
            a.startActivity(i);
            return;
        }
        SocialListActivity.open(a, userId, KIND_FRIENDS, a.getString(R.string.user_card_prop_friends));
    }

    public static void openVisitors(
            @NonNull FragmentActivity a, @NonNull String userId, @Nullable String myId) {
        if (isSelf(userId, myId)) {
            a.startActivity(new Intent(a, VisitorsActivity.class));
            return;
        }
        Toast.makeText(a, R.string.user_card_visitors_self_only, Toast.LENGTH_SHORT).show();
    }

    public static void openFullProfile(@NonNull FragmentActivity a, @NonNull String userId) {
        Intent i = new Intent(a, ProfileActivity.class);
        i.putExtra(ProfileActivity.EXTRA_USER_ID, userId);
        a.startActivity(i);
    }

    private static boolean isSelf(@NonNull String userId, @Nullable String myId) {
        return myId != null && !myId.isEmpty() && myId.equalsIgnoreCase(userId);
    }
}
