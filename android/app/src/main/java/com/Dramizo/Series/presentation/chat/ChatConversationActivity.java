package com.Dramizo.Series.presentation.chat;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.ChatDtos;
import com.Dramizo.Series.databinding.ActivityChatConversationBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ViewModelFactory;
import com.Dramizo.Series.presentation.gifts.GiftBottomSheet;
import com.Dramizo.Series.realtime.RealtimeClient;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.Dramizo.Series.util.DeviceTimeFormat;
import com.Dramizo.Series.util.PermissionHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class ChatConversationActivity extends ThemedActivity {
    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_PEER_ID = "peer_id";
    public static final String EXTRA_AVATAR = "avatar";
    public static final String EXTRA_HOST_BADGE = "host_badge";

    private ActivityChatConversationBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adapter;
    private String conversationId;
    private String peerId;
    private boolean peerBlocked;
    private String peerLastSeenAt;
    private boolean peerOnline;
    private boolean peerTyping;
    private boolean showOnlineAllowed = true;
    private String editingMessageId;
    private Runnable typingStop;
    private MediaRecorder mediaRecorder;
    private File voiceFile;
    private long voiceStartedAt;
    private boolean voiceCancelled;
    private float voiceDownRawY;
    private float voiceDownRawX;
    private final Handler voiceUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable voiceTick = new Runnable() {
        @Override public void run() {
            if (binding == null || voiceStartedAt <= 0 || binding.voiceRecordOverlay == null
                    || binding.voiceRecordOverlay.getVisibility() != View.VISIBLE) return;
            long elapsed = Math.max(0, SystemClock.elapsedRealtime() - voiceStartedAt);
            int totalSec = (int) (elapsed / 1000L);
            if (binding.tvVoiceTimer != null) {
                binding.tvVoiceTimer.setText(String.format(java.util.Locale.US, "%d:%02d",
                        totalSec / 60, totalSec % 60));
            }
            voiceUiHandler.postDelayed(this, 200);
        }
    };

    private final RealtimeClient.UserListener presenceListener = new RealtimeClient.UserListener() {
        @Override
        public void onPresenceOnline(String userId, String username) {
            if (peerId == null || userId == null || !peerId.equals(userId)) return;
            runOnUiThread(() -> {
                peerOnline = true;
                refreshPeerStatusUi();
            });
        }

        @Override
        public void onPresenceOffline(String userId) {
            if (peerId == null || userId == null || !peerId.equals(userId)) return;
            runOnUiThread(() -> {
                peerOnline = false;
                peerLastSeenAt = String.valueOf(System.currentTimeMillis());
                refreshPeerStatusUi();
            });
        }

        @Override
        public void onConnected() {
            requestPeerPresence();
        }
    };

    private final ActivityResultLauncher<String> pickGallery =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadAndSendImage(uri);
            });

    private final ActivityResultLauncher<Void> takePicturePreview =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
                if (bitmap != null) uploadAndSendBitmap(bitmap);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdgeHelper.apply(this);
        binding = ActivityChatConversationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsetsForKeyboard();

        conversationId = firstExtra(getIntent(), EXTRA_CONVERSATION_ID, "conversationId");
        com.Dramizo.Series.util.ActiveChatTracker.set(conversationId);
        peerId = firstExtra(getIntent(), EXTRA_PEER_ID, "peerId", "senderId", "userId");
        String title = firstExtra(getIntent(), EXTRA_TITLE, "senderName", "peerName", "name");
        String avatar = firstExtra(getIntent(), EXTRA_AVATAR, "avatarUrl", "senderAvatarUrl");
        String hostBadge = getIntent().getStringExtra(EXTRA_HOST_BADGE);
        String vipFrame = firstExtra(getIntent(), "vipBadgeUrl", "frameUrl", "peerFrameUrl");
        if (vipFrame == null || vipFrame.isEmpty()) {
            // Legacy EXTRA_HOST_BADGE was often filled with the VIP head frame URL.
            vipFrame = hostBadge;
            hostBadge = null;
        }

        bindPeerName(title, null);
        if (binding.tvPeerStatus != null) {
            binding.tvPeerStatus.setText(R.string.loading);
        }
        if (binding.imgPeerAvatar != null) {
            AvatarCosmetics.bindAvatar(binding.imgPeerAvatar, avatar);
            AvatarCosmetics.applyHostWear(
                    binding.imgPeerFrame, binding.imgPeerHostBadge, binding.imgPeerAvatar,
                    vipFrame, null, null, null);
            View.OnClickListener openPeerProfile = v -> openPeerProfile();
            binding.imgPeerAvatar.setOnClickListener(openPeerProfile);
            if (binding.imgPeerFrame != null) binding.imgPeerFrame.setOnClickListener(openPeerProfile);
            if (binding.tvPeerName != null) binding.tvPeerName.setOnClickListener(openPeerProfile);
            if (binding.imgStripAvatar != null) {
                AvatarCosmetics.bindAvatar(binding.imgStripAvatar, avatar);
                binding.imgStripAvatar.setOnClickListener(openPeerProfile);
            }
            if (binding.tvStripName != null && title != null && !title.isEmpty()) {
                binding.tvStripName.setText(title);
            }
        }
        binding.btnBack.setOnClickListener(v -> navigateUp());
        bindGiftButton();
        if (binding.btnChatMenu != null) {
            binding.btnChatMenu.setOnClickListener(v -> showChatMenu());
        }
        if (binding.btnCancelEdit != null) {
            binding.btnCancelEdit.setOnClickListener(v -> clearEditing());
        }

        viewModel = new ViewModelProvider(this, new ViewModelFactory(ContainerProvider.from(this)))
                .get(ChatViewModel.class);
        if (binding.btnCancelReply != null) {
            binding.btnCancelReply.setOnClickListener(v -> viewModel.clearReply());
        }
        com.Dramizo.Series.data.local.prefs.SessionManager session =
                ContainerProvider.from(this).getSessionManager();
        String myId = session.getUserId();
        if ((myId == null || myId.isEmpty()) && session.getUser() != null) {
            myId = session.getUser().id;
        }
        adapter = new MessageAdapter(myId, session.getUser(), new MessageAdapter.Listener() {
            @Override
            public void onLongClick(ChatDtos.MessageDto msg) {
                onMessageLongClick(msg);
            }

            @Override
            public void onSwipeReply(ChatDtos.MessageDto msg) {
                beginReply(msg);
            }

            @Override
            public void onTranslate(ChatDtos.MessageDto msg, int adapterPosition) {
                translateInline(msg);
            }

            @Override
            public void onAvatarClick(String userId) {
                if (userId != null && !userId.isEmpty()) {
                    Intent i = new Intent(ChatConversationActivity.this,
                            com.Dramizo.Series.presentation.profile.ProfileActivity.class);
                    i.putExtra(com.Dramizo.Series.presentation.profile.ProfileActivity.EXTRA_USER_ID, userId);
                    startActivity(i);
                } else {
                    openPeerProfile();
                }
            }
        });
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        binding.recycler.setLayoutManager(lm);
        binding.recycler.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        binding.recycler.setAdapter(adapter);
        attachSwipeToReply();
        binding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0 && lm.findFirstVisibleItemPosition() <= 2) {
                    viewModel.loadOlder();
                }
            }
        });

        binding.btnSend.setOnClickListener(v -> sendNow());
        setupVoiceButton();
        binding.btnAttach.setOnClickListener(v -> showAttachPicker());
        if (binding.btnChatGift != null) {
            binding.btnChatGift.setOnClickListener(v -> openChatGifts());
        }
        getSupportFragmentManager().setFragmentResultListener(
                "gift_sent_chat",
                this,
                (key, bundle) -> {
                    try {
                        String name = bundle != null ? bundle.getString("name") : null;
                        String icon = bundle != null ? bundle.getString("icon") : null;
                        if (conversationId == null || conversationId.isEmpty()) return;
                        if (name == null || name.isEmpty()) name = "هدية";
                        viewModel.sendGiftMessage(conversationId, name, icon);
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
                });
        if (binding.btnChatEmoji != null) {
            binding.btnChatEmoji.setOnClickListener(v -> showChatEmojiPicker());
        }
        binding.etMessage.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendNow();
                return true;
            }
            return false;
        });

        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshSendMicVisibility();
                if (conversationId == null) return;
                viewModel.setTyping(s != null && s.length() > 0);
                if (typingStop != null) binding.etMessage.removeCallbacks(typingStop);
                typingStop = () -> viewModel.setTyping(false);
                binding.etMessage.postDelayed(typingStop, 1500);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        refreshSendMicVisibility();

        viewModel.getMessages().observe(this, list -> {
            int previousCount = adapter.getItemCount();
            int firstVisible = lm.findFirstVisibleItemPosition();
            View firstView = lm.findViewByPosition(firstVisible);
            int firstOffset = firstView != null ? firstView.getTop() : 0;
            boolean wasNearBottom =
                    previousCount == 0 || lm.findLastVisibleItemPosition() >= previousCount - 2;
            adapter.submit(list);
            int newCount = list != null ? list.size() : 0;
            if (previousCount > 0 && newCount > previousCount && firstVisible <= 2) {
                lm.scrollToPositionWithOffset(firstVisible + (newCount - previousCount), firstOffset);
            } else if (wasNearBottom && newCount > 0) {
                binding.recycler.scrollToPosition(list.size() - 1);
            }
        });
        viewModel.getReplyTarget().observe(this, this::bindReplyBanner);
        viewModel.getPeerLastReadAt().observe(this, at -> {
            if (adapter != null && at != null) adapter.setPeerLastReadAt(at);
        });
        viewModel.getTyping().observe(this, t -> {
            peerTyping = Boolean.TRUE.equals(t);
            if (peerTyping) peerOnline = true;
            refreshPeerStatusUi();
        });
        viewModel.getError().observe(this, e -> {
            if (e == null) return;
            String msg = e;
            if (msg.contains("DM_GIFT_REQUIRED") || msg.contains("أرسل هدية أولاً")) {
                Toast.makeText(this, R.string.send_gift_to_open_chat, Toast.LENGTH_LONG).show();
                openGiftGateSheet();
                return;
            }
            if (com.Dramizo.Series.util.BalanceRedirect.looksLikeInsufficient(msg)) {
                com.Dramizo.Series.util.BalanceRedirect.handle(this, msg);
                return;
            }
            Toast.makeText(this, e, Toast.LENGTH_SHORT).show();
        });
        viewModel.getConversationDeleted().observe(this, deleted -> {
            if (!Boolean.TRUE.equals(deleted)) return;
            Toast.makeText(this, R.string.conversation_deleted, Toast.LENGTH_SHORT).show();
            finish();
        });

        RealtimeClient.getInstance().addUserListener(presenceListener);

        if (conversationId != null) {
            loadPeerHeader();
        }
    }

    private void applyInsetsForKeyboard() {
        final int headerPadL = binding.headerBar.getPaddingLeft();
        final int headerPadT = binding.headerBar.getPaddingTop();
        final int headerPadR = binding.headerBar.getPaddingRight();
        final int headerPadB = binding.headerBar.getPaddingBottom();
        final int inputPadL = binding.inputBar.getPaddingLeft();
        final int inputPadT = binding.inputBar.getPaddingTop();
        final int inputPadR = binding.inputBar.getPaddingRight();
        final int inputPadB = binding.inputBar.getPaddingBottom();
        final int voicePadL = binding.voiceRecordBar.getPaddingLeft();
        final int voicePadT = binding.voiceRecordBar.getPaddingTop();
        final int voicePadR = binding.voiceRecordBar.getPaddingRight();
        final int voicePadB = binding.voiceRecordBar.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            binding.headerBar.setPadding(headerPadL, headerPadT + bars.top, headerPadR, headerPadB);
            int bottom = Math.max(bars.bottom, ime.bottom);
            binding.inputBar.setPadding(inputPadL, inputPadT, inputPadR, inputPadB + bottom);
            // Keep "Slide to cancel" above Android nav bar / gesture bar.
            binding.voiceRecordBar.setPadding(
                    voicePadL, voicePadT, voicePadR, voicePadB + Math.max(bars.bottom, 0));
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.root);
    }

    private void sendNow() {
        String text = binding.etMessage.getText() != null ? binding.etMessage.getText().toString().trim() : "";
        if (text.isEmpty() || conversationId == null) return;
        com.Dramizo.Series.data.local.prefs.SessionManager sm =
                ContainerProvider.from(this).getSessionManager();
        if (sm.isChatPromoFilterFromServer()) {
            java.util.List<String> extra =
                    com.Dramizo.Series.util.ChatContentFilter.parseExtraKeywords(
                            sm.getChatExtraKeywordsFromServer());
            if (com.Dramizo.Series.util.ChatContentFilter.isBlocked(text, extra)) {
                Toast.makeText(
                                this,
                                com.Dramizo.Series.util.ChatContentFilter.BLOCK_REASON,
                                Toast.LENGTH_LONG)
                        .show();
                return;
            }
        }
        if (editingMessageId != null) {
            viewModel.editMessage(editingMessageId, text);
            clearEditing();
            return;
        }
        viewModel.send(conversationId, text);
        binding.etMessage.setText("");
    }

    private void beginEditing(ChatDtos.MessageDto msg) {
        if (msg == null || msg.id == null) return;
        editingMessageId = msg.id;
        String content = msg.content != null ? msg.content : "";
        binding.etMessage.setText(content);
        binding.etMessage.setSelection(content.length());
        if (binding.editBanner != null) {
            binding.editBanner.setVisibility(View.VISIBLE);
            if (binding.tvEditPreview != null) {
                binding.tvEditPreview.setText(content);
            }
        }
        binding.etMessage.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void clearEditing() {
        editingMessageId = null;
        if (binding.editBanner != null) binding.editBanner.setVisibility(View.GONE);
        if (binding.tvEditPreview != null) binding.tvEditPreview.setText("");
        binding.etMessage.setText("");
    }

    private boolean isMyMessage(ChatDtos.MessageDto msg) {
        if (msg == null) return false;
        if (msg.localPending) return true;
        if (msg.id != null && msg.id.startsWith("local:")) return true;
        String myId = ContainerProvider.from(this).getSessionManager().getUserId();
        AuthDtos.UserDto me = ContainerProvider.from(this).getSessionManager().getUser();
        if (myId == null || myId.isEmpty()) {
            if (me != null) myId = me.id;
        }
        if (myId == null || myId.isEmpty()) return false;
        if (myId.equals(msg.senderId)) return true;
        if (msg.sender != null && myId.equals(msg.sender.id)) return true;
        return false;
    }

    private void showChatEmojiPicker() {
        String[] emojis = {
                "😀", "😁", "😂", "🤣", "😃", "😄", "😅", "😆", "😉", "😊", "😋", "😎",
                "😍", "😘", "🥰", "😗", "😙", "😚", "🙂", "🤗", "🤩", "🤔", "🤨", "😐",
                "😏", "😣", "😥", "😮", "🤐", "😯", "😪", "😫", "🥱", "😴", "😌", "😛",
                "😜", "😝", "🤤", "😒", "😓", "😔", "😕", "🙃", "🤑", "😲", "☹️", "🙁",
                "😖", "😞", "😟", "😤", "😢", "😭", "😦", "😧", "😨", "😩", "🤯", "😬",
                "😰", "😱", "🥵", "🥶", "😳", "🤪", "😵", "😡", "😠", "🤬", "😷", "🤒",
                "👍", "👎", "👏", "🙌", "👐", "🤝", "🙏", "💪", "✌️", "🤞", "🤟", "🤘",
                "👌", "🤌", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋",
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕",
                "💞", "💓", "💗", "💖", "💘", "💝", "💟", "🔥", "✨", "⭐", "🌟", "💫",
                "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🎯", "🎮", "🎵", "🎶", "🌹", "🌸",
                "☕", "🍕", "🍔", "🍟", "🍰", "🎂", "🍩", "🍪", "🍎", "🍓", "🍒", "🍉"
        };
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        android.view.View sheet = getLayoutInflater().inflate(R.layout.dialog_emoji_picker, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        android.view.View stickers = sheet.findViewById(R.id.recyclerStickers);
        if (stickers != null) stickers.setVisibility(android.view.View.GONE);
        android.view.View stickersLabel = sheet.findViewById(R.id.tvStickersLabel);
        if (stickersLabel != null) stickersLabel.setVisibility(android.view.View.GONE);
        RecyclerView rv = sheet.findViewById(R.id.recyclerEmojis);
        if (rv != null) {
            rv.setNestedScrollingEnabled(true);
            rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 8));
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
                    android.view.View v = getLayoutInflater().inflate(R.layout.item_emoji_cell, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                }
                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    android.widget.TextView tv = holder.itemView.findViewById(R.id.tvEmoji);
                    tv.setText(emojis[position]);
                    holder.itemView.setOnClickListener(v -> {
                        CharSequence cur = binding.etMessage.getText();
                        binding.etMessage.setText((cur != null ? cur.toString() : "") + emojis[position]);
                        if (binding.etMessage.getText() != null) {
                            binding.etMessage.setSelection(binding.etMessage.getText().length());
                        }
                    });
                }
                @Override
                public int getItemCount() { return emojis.length; }
            });
        }
        dialog.show();
    }

    private void showAttachPicker() {
        AuraDialogHelper.list(this, getString(R.string.pick_gallery_photo),
                new String[]{getString(R.string.camera), getString(R.string.gallery)}, which -> {
                    if (which == 0) launchCameraCapture();
                    else pickGallery.launch("image/*");
                });
    }

    private void launchCameraCapture() {
        if (!PermissionHelper.hasCameraPermission(this)) {
            PermissionHelper.ensureMediaPermissions(this, true);
            return;
        }
        try {
            takePicturePreview.launch(null);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadAndSendImage(Uri uri) {
        if (conversationId == null) return;
        Toast.makeText(this, R.string.uploading_image, Toast.LENGTH_SHORT).show();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IllegalStateException("cannot open image");
                    bytes = readAll(in);
                }
                uploadImageBytes(c, bytes);
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void uploadAndSendBitmap(@NonNull Bitmap bitmap) {
        if (conversationId == null) return;
        Toast.makeText(this, R.string.uploading_image, Toast.LENGTH_SHORT).show();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                uploadImageBytes(c, out.toByteArray());
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void uploadImageBytes(AppContainer c, byte[] bytes) throws Exception {
        RequestBody body = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", "chat.jpg", body);
        Response<ApiResponse<UploadApi.UploadResult>> resp = c.getUploadApi().upload(part).execute();
        if (resp.isSuccessful() && resp.body() != null && resp.body().success
                && resp.body().data != null && resp.body().data.url != null) {
            String url = AssetCatalog.absoluteUrl(resp.body().data.url);
            runOnUiThread(() -> viewModel.sendImage(conversationId, url));
        } else {
            runOnUiThread(() ->
                    Toast.makeText(this, R.string.upload_image_failed, Toast.LENGTH_LONG).show());
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void loadPeerHeader() {
        ContainerProvider.from(this).getIoExecutor().execute(() -> {
            AuthDtos.UserDto peer = null;
            Result<ChatDtos.ConversationDto> conv =
                    ApiCall.execute(ContainerProvider.from(this).getChatApi().conversation(conversationId));
            if (conv.success && conv.data != null) {
                peer = conv.data.peer;
                if (peerId == null && peer != null) peerId = peer.id;
                final String name = resolveName(conv.data);
                final String username = peer != null ? peer.username : null;
                final String av = peer != null && peer.avatarUrl != null ? peer.avatarUrl
                        : conv.data.avatarUrl;
                final String hb = peer != null ? peer.vipBadgeUrl : null;
                final AuthDtos.UserDto headerPeer = peer;
                runOnUiThread(() -> {
                    if (binding == null) return;
                    bindPeerName(name, username, headerPeer);
                    AvatarCosmetics.bindAvatar(binding.imgPeerAvatar, av);
                    AvatarCosmetics.applyHostWear(
                            binding.imgPeerFrame,
                            binding.imgPeerHostBadge,
                            binding.imgPeerAvatar,
                            hb,
                            null,
                            null,
                            null);
                    if (adapter != null && headerPeer != null) adapter.setPeerProfile(headerPeer);
                });
            }
            if (peerId != null) {
                Result<AuthDtos.UserDto> u =
                        ApiCall.execute(ContainerProvider.from(this).getUserApi().getUser(peerId));
                if (u.success && u.data != null) peer = u.data;
            }
            final AuthDtos.UserDto finalPeer = peer;
            runOnUiThread(() -> {
                bindPeerProfile(finalPeer);
                requestPeerPresence();
            });
        });
    }

    private String resolveName(ChatDtos.ConversationDto c) {
        if (c.peer != null) {
            if (c.peer.displayName != null && !c.peer.displayName.isEmpty()) return c.peer.displayName;
            if (c.peer.username != null) return c.peer.username;
        }
        if (c.title != null && !c.title.isEmpty()) return c.title;
        return getString(R.string.chat);
    }

    private void bindPeerName(String displayName, String username) {
        bindPeerName(displayName, username, null);
    }

    private void bindPeerName(String displayName, String username, @Nullable AuthDtos.UserDto peer) {
        if (binding == null) return;
        String name = displayName != null && !displayName.isEmpty()
                ? displayName
                : (username != null && !username.isEmpty() ? username : getString(R.string.chat));
        binding.tvPeerName.setText(name);
        String publicId = peer != null ? peer.displayPublicId() : "";
        if (publicId.isEmpty() && username != null && !username.isEmpty()) {
            publicId = username;
        }
        if (!publicId.isEmpty() && binding.tvPeerUsername != null) {
            binding.tvPeerUsername.setText("(ID:" + publicId + ")");
            binding.tvPeerUsername.setVisibility(View.VISIBLE);
        } else if (binding.tvPeerUsername != null) {
            binding.tvPeerUsername.setVisibility(View.GONE);
        }
        if (binding.tvStripName != null) binding.tvStripName.setText(name);
    }

    private void bindPeerProfile(AuthDtos.UserDto peer) {
        if (binding == null) return;
        if (peer == null) {
            refreshPeerStatusUi();
            return;
        }
        if (peerId == null) peerId = peer.id;
        bindPeerName(peer.displayName, peer.username, peer);
        if (binding.imgPeerAvatar != null) {
            AvatarCosmetics.bindAvatar(binding.imgPeerAvatar, peer.avatarUrl);
        }
        if (binding.imgStripAvatar != null) {
            AvatarCosmetics.bindAvatar(binding.imgStripAvatar, peer.avatarUrl);
        }
        if (binding.imgPeerFrame != null || binding.imgPeerHostBadge != null) {
            AvatarCosmetics.applyHostWear(
                    binding.imgPeerFrame,
                    binding.imgPeerHostBadge,
                    binding.imgPeerAvatar,
                    peer.vipBadgeUrl,
                    null,
                    null,
                    null);
        }
        if (binding.imgStripFlag != null) {
            com.Dramizo.Series.util.FlagImages.bind(binding.imgStripFlag, peer.country);
        }
        if (binding.tvStripLv1 != null) {
            binding.tvStripLv1.setText(String.valueOf(Math.max(1, peer.level)));
        }
        if (binding.tvStripLv2 != null) {
            long pop = peer.popularityLevel > 0 ? peer.popularityLevel : peer.popularityScore;
            binding.tvStripLv2.setText(String.valueOf(Math.max(0, pop)));
        }
        if (binding.tvStripLv3 != null) {
            long w = peer.wealthLevel > 0 ? peer.wealthLevel : peer.wealthScore;
            binding.tvStripLv3.setText(String.valueOf(Math.max(0, w)));
        }
        // Female gradient strip when known.
        if (binding.imgPeerStripBg != null && peer.gender != null) {
            String g = peer.gender.trim().toLowerCase(java.util.Locale.US);
            if ("female".equals(g) || "f".equals(g) || "انثى".equals(g) || "أنثى".equals(g)) {
                binding.imgPeerStripBg.setImageResource(R.drawable.p2p_nvbg);
            } else {
                binding.imgPeerStripBg.setImageResource(R.drawable.p2p_nanbg);
            }
        }
        if (binding.btnUpgradeRelation != null) {
            binding.btnUpgradeRelation.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this,
                            com.Dramizo.Series.presentation.cp.CpCenterActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.upgrade_relation, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (adapter != null) {
            adapter.setPeerProfile(peer);
        }
        showOnlineAllowed = peer.showOnlineStatus == null || Boolean.TRUE.equals(peer.showOnlineStatus);
        peerLastSeenAt = peer.lastSeenAt;
        if (showOnlineAllowed) {
            java.util.Date d = DeviceTimeFormat.parse(peer.lastSeenAt);
            if (d != null && System.currentTimeMillis() - d.getTime() < 2 * 60_000L) {
                peerOnline = true;
            }
        }
        refreshPeerStatusUi();
    }

    private void requestPeerPresence() {
        if (peerId == null || !showOnlineAllowed) return;
        RealtimeClient.getInstance().checkPresence(peerId, (userId, online) -> runOnUiThread(() -> {
            if (peerId == null || !peerId.equals(userId)) return;
            peerOnline = online;
            refreshPeerStatusUi();
        }));
    }

    private void refreshPeerStatusUi() {
        if (binding == null || binding.tvPeerStatus == null) return;
        // Typing only under the name — never duplicate below the chat list.
        if (binding.tvTyping != null) binding.tvTyping.setVisibility(View.GONE);
        if (peerTyping) {
            binding.tvPeerStatus.setText(R.string.typing);
            binding.tvPeerStatus.setTextColor(getColor(R.color.text_secondary));
            return;
        }
        if (!showOnlineAllowed) {
            binding.tvPeerStatus.setText(R.string.offline);
            binding.tvPeerStatus.setTextColor(getColor(R.color.text_hint));
            return;
        }
        if (peerOnline) {
            binding.tvPeerStatus.setText(R.string.online_now);
            binding.tvPeerStatus.setTextColor(getColor(R.color.aurora_success));
            return;
        }
        binding.tvPeerStatus.setText(DeviceTimeFormat.lastSeen(this, peerLastSeenAt));
        binding.tvPeerStatus.setTextColor(getColor(R.color.text_hint));
    }

    private void openPeerProfile() {
        if (peerId == null || peerId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, com.Dramizo.Series.presentation.profile.ProfileActivity.class);
        i.putExtra(com.Dramizo.Series.presentation.profile.ProfileActivity.EXTRA_USER_ID, peerId);
        startActivity(i);
    }

    private void onMessageLongClick(ChatDtos.MessageDto msg) {
        if (msg == null || msg.id == null) return;
        boolean mine = isMyMessage(msg);
        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        View sheet = getLayoutInflater().inflate(R.layout.dialog_message_actions, null);
        AuraDialogHelper.applyContent(sheet);
        dialog.setContentView(sheet);
        View actReply = sheet.findViewById(R.id.actReply);
        View actCopy = sheet.findViewById(R.id.actCopy);
        View actEdit = sheet.findViewById(R.id.actEdit);
        View actDelete = sheet.findViewById(R.id.actDelete);
        View actTranslate = sheet.findViewById(R.id.actTranslate);
        if (actEdit != null) actEdit.setVisibility(mine ? View.VISIBLE : View.GONE);
        if (actDelete != null) actDelete.setVisibility(mine ? View.VISIBLE : View.GONE);
        if (actCopy != null) {
            boolean canCopy = msg.content != null && !msg.content.trim().isEmpty();
            actCopy.setVisibility(canCopy ? View.VISIBLE : View.GONE);
            actCopy.setOnClickListener(v -> {
                dialog.dismiss();
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && msg.content != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("message", msg.content));
                    Toast.makeText(this, R.string.message_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (actReply != null) {
            actReply.setOnClickListener(v -> {
                dialog.dismiss();
                beginReply(msg);
            });
        }
        if (actEdit != null) {
            actEdit.setOnClickListener(v -> {
                dialog.dismiss();
                beginEditing(msg);
            });
        }
        if (actDelete != null) {
            actDelete.setOnClickListener(v -> {
                dialog.dismiss();
                AuraDialogHelper.confirmRes(this,
                        R.string.delete_message,
                        R.string.delete_message_confirm,
                        android.R.string.ok,
                        () -> viewModel.unsendMessage(msg.id));
            });
        }
        if (actTranslate != null) {
            // Inline translate lives under the bubble; hide sheet action.
            actTranslate.setVisibility(View.GONE);
        }
        dialog.show();
    }

    private void translateInline(ChatDtos.MessageDto msg) {
        if (msg == null || msg.id == null) return;
        String text = msg.content;
        if (text == null || text.trim().isEmpty()) return;
        if (adapter.hasTranslation(msg.id)) {
            adapter.clearTranslation(msg.id);
            return;
        }
        String target = java.util.Locale.getDefault().getLanguage();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("text", text);
            body.put("targetLang", target);
            Result<java.util.Map<String, Object>> r = ApiCall.execute(c.getConfigApi().translate(body));
            runOnUiThread(() -> {
                if (!r.success || r.data == null) {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Object translated = r.data.get("translated");
                String out = translated != null ? String.valueOf(translated) : "";
                if (out.trim().isEmpty()) {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    return;
                }
                adapter.setTranslation(msg.id, out);
            });
        });
    }

    private void translateAndShow(String text) {
        // Kept for compatibility — prefer inline bubble translate.
        if (text == null || text.trim().isEmpty()) return;
        String target = java.util.Locale.getDefault().getLanguage();
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("text", text);
            body.put("targetLang", target);
            Result<java.util.Map<String, Object>> r = ApiCall.execute(c.getConfigApi().translate(body));
            runOnUiThread(() -> {
                if (!r.success || r.data == null) {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Object translated = r.data.get("translated");
                String out = translated != null ? String.valueOf(translated) : text;
                Toast.makeText(this, out, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void beginReply(ChatDtos.MessageDto msg) {
        if (msg == null) return;
        clearEditing();
        viewModel.setReplyTarget(msg);
        binding.etMessage.requestFocus();
    }

    private void bindReplyBanner(ChatDtos.MessageDto msg) {
        if (binding.replyBanner == null) return;
        if (msg == null) {
            binding.replyBanner.setVisibility(View.GONE);
            return;
        }
        binding.replyBanner.setVisibility(View.VISIBLE);
        if (binding.tvReplyTitle != null) {
            binding.tvReplyTitle.setText(R.string.replying_to);
        }
        if (binding.tvReplyPreview != null) {
            String preview = msg.content;
            if (preview == null || preview.isEmpty()) {
                String type = msg.type != null ? msg.type.toLowerCase() : "";
                if ("image".equals(type)) preview = getString(R.string.chat_preview_photo);
                else if ("audio".equals(type)) preview = getString(R.string.chat_preview_voice);
                else if ("gift".equals(type)) preview = getString(R.string.chat_preview_gift,
                        getString(R.string.gift_message));
                else preview = getString(R.string.chat_message_generic);
            }
            binding.tvReplyPreview.setText(preview);
        }
    }

    private void refreshSendMicVisibility() {
        CharSequence text = binding.etMessage.getText();
        boolean hasText = text != null && text.toString().trim().length() > 0;
        binding.btnSend.setEnabled(hasText);
        binding.btnSend.setAlpha(hasText ? 1f : 0.35f);
        if (binding.imgChatSend != null) {
            binding.imgChatSend.setAlpha(hasText ? 1f : 0.35f);
        }
    }

    private void attachSwipeToReply() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                ChatDtos.MessageDto msg = adapter.getItem(pos);
                adapter.notifyItemChanged(pos);
                if (msg != null) beginReply(msg);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.28f;
            }
        });
        helper.attachToRecyclerView(binding.recycler);
    }

    private boolean voiceRecordingActive;

    private void setupVoiceButton() {
        if (binding.btnVoice == null) return;
        // WhatsApp/Telegram-like: tap mic to start, then Cancel / Send on the bar.
        binding.btnVoice.setOnClickListener(v -> {
            if (voiceRecordingActive) return;
            startVoiceRecording();
        });
        if (binding.btnVoiceCancel != null) {
            binding.btnVoiceCancel.setOnClickListener(v -> {
                voiceCancelled = true;
                stopVoiceRecording(false);
            });
        }
        if (binding.btnVoiceSend != null) {
            binding.btnVoiceSend.setOnClickListener(v -> {
                voiceCancelled = false;
                stopVoiceRecording(true);
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startVoiceRecording() {
        if (!PermissionHelper.hasAudioPermission(this)) {
            PermissionHelper.ensureMediaPermissions(this, false);
            Toast.makeText(this, R.string.hold_to_record, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            stopRecorderQuiet();
            voiceFile = new File(getCacheDir(), "chat_voice_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(96000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(voiceFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            voiceStartedAt = SystemClock.elapsedRealtime();
            voiceCancelled = false;
            voiceRecordingActive = true;
            showVoiceHud(true);
        } catch (Exception e) {
            voiceRecordingActive = false;
            stopRecorderQuiet();
            hideVoiceHud();
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
        }
    }

    private void showVoiceHud(boolean recording) {
        if (binding.voiceRecordOverlay == null) return;
        binding.voiceRecordOverlay.setVisibility(View.VISIBLE);
        if (binding.tvVoiceTimer != null) {
            binding.tvVoiceTimer.setVisibility(View.VISIBLE);
            binding.tvVoiceTimer.setText("0:00");
        }
        if (binding.tvVoiceHint != null) {
            binding.tvVoiceHint.setText(R.string.press_to_record);
            binding.tvVoiceHint.setTextColor(0xFF333333);
        }
        if (binding.imgChatVoice != null) {
            binding.imgChatVoice.setColorFilter(0xFFE53935);
        }
        if (binding.voiceMicCircle != null) {
            binding.voiceMicCircle.setAlpha(1f);
            binding.voiceMicCircle.animate()
                    .scaleX(1.06f).scaleY(1.06f)
                    .setDuration(450)
                    .withEndAction(new Runnable() {
                        @Override public void run() {
                            if (binding == null || !voiceRecordingActive
                                    || binding.voiceMicCircle == null) return;
                            binding.voiceMicCircle.animate()
                                    .scaleX(1f).scaleY(1f)
                                    .setDuration(450)
                                    .withEndAction(this)
                                    .start();
                        }
                    })
                    .start();
        }
        voiceUiHandler.removeCallbacks(voiceTick);
        voiceUiHandler.post(voiceTick);
    }

    private void hideVoiceHud() {
        voiceUiHandler.removeCallbacks(voiceTick);
        voiceRecordingActive = false;
        if (binding == null) return;
        if (binding.imgChatVoice != null) binding.imgChatVoice.clearColorFilter();
        if (binding.voiceMicCircle != null) {
            binding.voiceMicCircle.animate().cancel();
            binding.voiceMicCircle.setScaleX(1f);
            binding.voiceMicCircle.setScaleY(1f);
        }
        if (binding.voiceRecDot != null) {
            binding.voiceRecDot.animate().cancel();
            binding.voiceRecDot.setAlpha(1f);
        }
        if (binding.voiceRecordOverlay != null) {
            binding.voiceRecordOverlay.setVisibility(View.GONE);
        }
    }

    private void stopVoiceRecording(boolean send) {
        voiceRecordingActive = false;
        hideVoiceHud();
        int durationSec = 0;
        if (mediaRecorder != null && voiceStartedAt > 0) {
            durationSec = Math.max(1, (int) ((SystemClock.elapsedRealtime() - voiceStartedAt) / 1000L));
        }
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (Exception ignored) {
            send = false;
        }
        stopRecorderQuiet();
        if (!send || voiceCancelled || voiceFile == null || !voiceFile.exists()) {
            if (voiceFile != null) {
                //noinspection ResultOfMethodCallIgnored
                voiceFile.delete();
            }
            voiceFile = null;
            return;
        }
        if (durationSec < 1) {
            //noinspection ResultOfMethodCallIgnored
            voiceFile.delete();
            voiceFile = null;
            Toast.makeText(this, R.string.hold_to_record, Toast.LENGTH_SHORT).show();
            return;
        }
        uploadAndSendVoice(voiceFile, durationSec);
        voiceFile = null;
    }

    private void stopRecorderQuiet() {
        if (mediaRecorder != null) {
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            mediaRecorder = null;
        }
        voiceStartedAt = 0;
    }

    private void uploadAndSendVoice(File file, int durationSec) {
        if (conversationId == null || file == null) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            try {
                byte[] bytes;
                try (InputStream in = new FileInputStream(file)) {
                    bytes = readAll(in);
                }
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                RequestBody body = RequestBody.create(bytes, MediaType.parse("audio/mp4"));
                MultipartBody.Part part = MultipartBody.Part.createFormData("file", "voice.m4a", body);
                Response<ApiResponse<UploadApi.UploadResult>> resp = c.getUploadApi().upload(part).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().success
                        && resp.body().data != null && resp.body().data.url != null) {
                    String url = AssetCatalog.absoluteUrl(resp.body().data.url);
                    runOnUiThread(() -> viewModel.sendAudio(conversationId, url, durationSec));
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void bindGiftButton() {
        if (binding.btnChatGift != null) {
            binding.btnChatGift.setVisibility(View.VISIBLE);
        }
        // Gift icon comes from activity_chat_conversation.xml android:src.
    }

    private void openGiftGateSheet() {
        openChatGifts();
    }

    private void openChatGifts() {
        if (peerId == null || peerId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        GiftBottomSheet.showForChat(getSupportFragmentManager(), peerId);
    }

    private void showChatMenu() {
        if (binding.btnChatMenu == null) return;
        PopupMenu menu = new PopupMenu(
                new android.view.ContextThemeWrapper(this, R.style.ThemeOverlay_AuraLive_PopupMenu),
                binding.btnChatMenu);
        menu.getMenuInflater().inflate(R.menu.menu_chat_conversation, menu.getMenu());
        MenuItem blockItem = menu.getMenu().findItem(R.id.action_block_user);
        if (blockItem != null) {
            blockItem.setTitle(peerBlocked ? R.string.unblock_user : R.string.block_user);
        }
        try {
            java.lang.reflect.Field field = menu.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object popup = field.get(menu);
            popup.getClass()
                    .getDeclaredMethod("setForceShowIcon", boolean.class)
                    .invoke(popup, true);
        } catch (Exception ignored) {}
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_archive_chat) {
                archiveConversation();
                return true;
            }
            if (id == R.id.action_delete_chat) {
                confirmDeleteConversation();
                return true;
            }
            if (id == R.id.action_block_user) {
                if (peerBlocked) confirmUnblockPeer();
                else confirmBlockPeer();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void archiveConversation() {
        if (conversationId == null) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, Boolean> body = new java.util.HashMap<>();
            body.put("archived", true);
            Result<Object> r = ApiCall.execute(c.getChatApi().archive(conversationId, body));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, R.string.conversation_archived, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void confirmDeleteConversation() {
        AuraDialogHelper.confirmRes(this,
                R.string.delete_conversation,
                R.string.delete_conversation,
                android.R.string.ok,
                this::deleteConversation);
    }

    private void deleteConversation() {
        if (conversationId == null) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getChatApi().deleteConversation(conversationId));
            runOnUiThread(() -> {
                if (r.success) {
                    Toast.makeText(this, R.string.conversation_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void confirmBlockPeer() {
        if (peerId == null || peerId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        AuraDialogHelper.confirmRes(this,
                R.string.block_user,
                R.string.block_user,
                android.R.string.ok,
                this::blockPeer);
    }

    private void confirmUnblockPeer() {
        if (peerId == null || peerId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        AuraDialogHelper.confirmRes(this,
                R.string.unblock_user,
                R.string.unblock_user,
                android.R.string.ok,
                this::unblockPeer);
    }

    private void blockPeer() {
        if (peerId == null) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getUserApi().block(peerId, new java.util.HashMap<>()));
            runOnUiThread(() -> {
                if (r.success) {
                    peerBlocked = true;
                    Toast.makeText(this, R.string.user_blocked, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void unblockPeer() {
        if (peerId == null) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getUserApi().unblock(peerId));
            runOnUiThread(() -> {
                if (r.success) {
                    peerBlocked = false;
                    Toast.makeText(this, R.string.user_unblocked, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, r.error != null ? r.error : getString(R.string.error_generic),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void refreshPeerBlockStatus() {
        if (peerId == null || peerId.isEmpty()) return;
        AppContainer c = ContainerProvider.from(this);
        c.getIoExecutor().execute(() -> {
            Result<com.Dramizo.Series.data.remote.dto.MiscDtos.ListResult<AuthDtos.UserDto>> r =
                    ApiCall.execute(c.getUserApi().blocked());
            boolean blocked = false;
            if (r.success && r.data != null && r.data.items != null) {
                for (AuthDtos.UserDto u : r.data.items) {
                    if (u != null && peerId.equals(u.id)) {
                        blocked = true;
                        break;
                    }
                }
            }
            boolean finalBlocked = blocked;
            runOnUiThread(() -> peerBlocked = finalBlocked);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;
        setIntent(intent);
        String nextId = firstExtra(intent, EXTRA_CONVERSATION_ID, "conversationId");
        if (nextId == null || nextId.isEmpty()) return;
        if (nextId.equals(conversationId)) {
            if (viewModel != null) viewModel.load(conversationId);
            return;
        }
        com.Dramizo.Series.util.ActiveChatTracker.clear(conversationId);
        conversationId = nextId;
        com.Dramizo.Series.util.ActiveChatTracker.set(conversationId);
        peerId = firstExtra(intent, EXTRA_PEER_ID, "peerId", "senderId", "userId");
        String title = firstExtra(intent, EXTRA_TITLE, "senderName", "peerName", "name");
        String avatar = firstExtra(intent, EXTRA_AVATAR, "avatarUrl", "senderAvatarUrl");
        String hostBadge = intent.getStringExtra(EXTRA_HOST_BADGE);
        bindPeerName(title, null);
        AvatarCosmetics.bindAvatar(binding.imgPeerAvatar, avatar);
        AvatarCosmetics.applyHostWear(
                binding.imgPeerFrame, binding.imgPeerHostBadge, binding.imgPeerAvatar,
                hostBadge, null, null, null);
        if (adapter != null) adapter.submit(java.util.Collections.emptyList());
        if (viewModel != null) {
            viewModel.clearReply();
            viewModel.load(conversationId);
        }
        refreshPeerBlockStatus();
        loadPeerHeader();
    }

    private static String firstExtra(Intent intent, String... keys) {
        if (intent == null || keys == null) return null;
        for (String key : keys) {
            String v = intent.getStringExtra(key);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.Dramizo.Series.util.ActiveChatTracker.set(conversationId);
        refreshPeerBlockStatus();
        if (conversationId != null && viewModel != null) {
            viewModel.load(conversationId);
        }
    }

    @Override
    protected void onPause() {
        // Keep mute while this chat stays on the back stack (Home / lock screen).
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        com.Dramizo.Series.util.ActiveChatTracker.clear(conversationId);
        RealtimeClient.getInstance().removeUserListener(presenceListener);
        if (typingStop != null && binding != null) binding.etMessage.removeCallbacks(typingStop);
        if (conversationId != null && viewModel != null) viewModel.setTyping(false);
        if (adapter != null) adapter.releasePlayer();
        hideVoiceHud();
        stopRecorderQuiet();
        voiceUiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
