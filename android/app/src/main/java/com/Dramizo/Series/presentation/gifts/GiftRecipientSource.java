package com.Dramizo.Series.presentation.gifts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/** Voice room activity exposes seated users for the gift picker. */
public interface GiftRecipientSource {
    @NonNull
    List<GiftRecipient> getGiftRecipients();

    @Nullable
    String getDefaultGiftReceiverId();

    /** Agency rooms show host signal; personal rooms show VIP frames. */
    default boolean isAgencyGiftRoom() {
        return false;
    }
}
