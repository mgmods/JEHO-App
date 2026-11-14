package com.Dramizo.Series.util;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.Dramizo.Series.presentation.voiceroom.RoomJoinGateActivity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects room deep links in plain text and opens {@link RoomJoinGateActivity}. */
public final class RoomLinkSpans {
    private static final Pattern ROOM_LINK = Pattern.compile(
            "(?:hamslive://room/|/open/room/)([0-9a-fA-F\\-]{8,})",
            Pattern.CASE_INSENSITIVE);

    private RoomLinkSpans() {}

    @Nullable
    public static String extractRoomId(@Nullable String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = ROOM_LINK.matcher(text);
        if (m.find()) return m.group(1);
        return null;
    }

    public static void bind(@Nullable TextView view, @Nullable String content) {
        if (view == null) return;
        if (content == null || content.isEmpty()) {
            view.setText("");
            return;
        }
        Matcher m = ROOM_LINK.matcher(content);
        if (!m.find()) {
            view.setText(content);
            view.setMovementMethod(null);
            view.setClickable(false);
            return;
        }
        SpannableString span = new SpannableString(content);
        m.reset();
        while (m.find()) {
            final String roomId = m.group(1);
            int start = m.start();
            int end = m.end();
            span.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    RoomJoinGateActivity.open(widget.getContext(), roomId);
                }

                @Override
                public void updateDrawState(@NonNull TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setUnderlineText(true);
                    ds.setColor(0xFF22D3EE);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(span);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(0x00000000);
    }
}
