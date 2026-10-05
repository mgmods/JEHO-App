package com.Dramizo.Series.di;

import android.content.Context;

import androidx.room.Room;

import com.Dramizo.Series.BuildConfig;
import com.Dramizo.Series.billing.BillingHelper;
import com.Dramizo.Series.data.local.db.AuraDatabase;
import com.Dramizo.Series.data.local.prefs.EncryptedFeatureCache;
import com.Dramizo.Series.data.local.prefs.SessionManager;
import com.Dramizo.Series.data.remote.api.AgencyApi;
import com.Dramizo.Series.data.remote.api.AuthApi;
import com.Dramizo.Series.data.remote.api.ChatApi;
import com.Dramizo.Series.data.remote.api.ConfigApi;
import com.Dramizo.Series.data.remote.api.ContestsApi;
import com.Dramizo.Series.data.remote.api.EventsApi;
import com.Dramizo.Series.data.remote.api.CosmeticsApi;
import com.Dramizo.Series.data.remote.api.TasksApi;
import com.Dramizo.Series.data.remote.api.GiftApi;
import com.Dramizo.Series.data.remote.api.GameApi;
import com.Dramizo.Series.data.remote.api.SlotGameApi;
import com.Dramizo.Series.data.remote.api.CasualGameApi;
import com.Dramizo.Series.data.remote.api.PromotionsApi;
import com.Dramizo.Series.data.remote.api.VanityApi;
import com.Dramizo.Series.data.remote.api.GameStoreApi;
import com.Dramizo.Series.data.remote.api.NotificationApi;
import com.Dramizo.Series.data.remote.api.RankingApi;
import com.Dramizo.Series.data.remote.api.RoomApi;
import com.Dramizo.Series.data.remote.api.RoomCupApi;
import com.Dramizo.Series.data.remote.api.LuckyBoxesApi;
import com.Dramizo.Series.data.remote.api.DramaApi;
import com.Dramizo.Series.data.remote.api.GameAdsApi;
import com.Dramizo.Series.data.remote.api.UploadApi;
import com.Dramizo.Series.data.remote.api.UserApi;
import com.Dramizo.Series.data.remote.api.InviteApi;
import com.Dramizo.Series.data.remote.api.VipApi;
import com.Dramizo.Series.data.remote.api.WalletApi;
import com.Dramizo.Series.data.remote.interceptor.AuthInterceptor;
import com.Dramizo.Series.data.remote.interceptor.TokenAuthenticator;
import com.Dramizo.Series.data.repository.AgencyRepositoryImpl;
import com.Dramizo.Series.data.repository.AuthRepositoryImpl;
import com.Dramizo.Series.data.repository.ChatRepositoryImpl;
import com.Dramizo.Series.data.repository.CosmeticsRepositoryImpl;
import com.Dramizo.Series.data.repository.GiftRepositoryImpl;
import com.Dramizo.Series.data.repository.GameRepositoryImpl;
import com.Dramizo.Series.data.repository.NotificationRepositoryImpl;
import com.Dramizo.Series.data.repository.RankingRepositoryImpl;
import com.Dramizo.Series.data.repository.RoomRepositoryImpl;
import com.Dramizo.Series.data.repository.UserRepositoryImpl;
import com.Dramizo.Series.data.repository.VipRepositoryImpl;
import com.Dramizo.Series.data.repository.WalletRepositoryImpl;
import com.Dramizo.Series.domain.repository.AgencyRepository;
import com.Dramizo.Series.domain.repository.AuthRepository;
import com.Dramizo.Series.domain.repository.ChatRepository;
import com.Dramizo.Series.domain.repository.CosmeticsRepository;
import com.Dramizo.Series.domain.repository.GiftRepository;
import com.Dramizo.Series.domain.repository.GameRepository;
import com.Dramizo.Series.domain.repository.NotificationRepository;
import com.Dramizo.Series.domain.repository.RankingRepository;
import com.Dramizo.Series.domain.repository.RoomRepository;
import com.Dramizo.Series.domain.repository.UserRepository;
import com.Dramizo.Series.domain.repository.VipRepository;
import com.Dramizo.Series.domain.repository.WalletRepository;
import com.Dramizo.Series.domain.usecase.agency.GetAgenciesUseCase;
import com.Dramizo.Series.domain.usecase.auth.GuestLoginUseCase;
import com.Dramizo.Series.domain.usecase.auth.LoginUseCase;
import com.Dramizo.Series.domain.usecase.auth.RefreshSessionUseCase;
import com.Dramizo.Series.domain.usecase.auth.RegisterUseCase;
import com.Dramizo.Series.domain.usecase.auth.SendOtpUseCase;
import com.Dramizo.Series.domain.usecase.auth.SocialLoginUseCase;
import com.Dramizo.Series.domain.usecase.auth.VerifyOtpUseCase;
import com.Dramizo.Series.domain.usecase.chat.GetConversationsUseCase;
import com.Dramizo.Series.domain.usecase.chat.GetMessagesUseCase;
import com.Dramizo.Series.domain.usecase.chat.SendMessageUseCase;
import com.Dramizo.Series.domain.usecase.gift.GetGiftsUseCase;
import com.Dramizo.Series.domain.usecase.gift.SendGiftUseCase;
import com.Dramizo.Series.domain.usecase.notification.GetNotificationsUseCase;
import com.Dramizo.Series.domain.usecase.ranking.GetRankingsUseCase;
import com.Dramizo.Series.domain.usecase.room.CreateRoomUseCase;
import com.Dramizo.Series.domain.usecase.room.GetRoomUseCase;
import com.Dramizo.Series.domain.usecase.room.JoinRoomUseCase;
import com.Dramizo.Series.domain.usecase.room.ListRoomsUseCase;
import com.Dramizo.Series.domain.usecase.room.RaiseHandUseCase;
import com.Dramizo.Series.domain.usecase.user.GetProfileUseCase;
import com.Dramizo.Series.domain.usecase.user.UpdateProfileUseCase;
import com.Dramizo.Series.domain.usecase.vip.GetVipPlansUseCase;
import com.Dramizo.Series.domain.usecase.vip.PurchaseVipUseCase;
import com.Dramizo.Series.domain.usecase.wallet.GetWalletUseCase;
import com.Dramizo.Series.domain.usecase.wallet.GetRechargePackagesUseCase;
import com.Dramizo.Series.domain.usecase.wallet.VerifyPurchaseUseCase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppContainer {
    private final Context appContext;
    private final SessionManager sessionManager;
    private final EncryptedFeatureCache featureCache;
    private final AuraDatabase database;
    private final ExecutorService ioExecutor;
    private final Retrofit retrofit;
    private final BillingHelper billingHelper;

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ChatRepository chatRepository;
    private final WalletRepository walletRepository;
    private final GiftRepository giftRepository;
    private final GameRepository gameRepository;
    private final VipRepository vipRepository;
    private final AgencyRepository agencyRepository;
    private final RankingRepository rankingRepository;
    private final NotificationRepository notificationRepository;
    private final CosmeticsRepository cosmeticsRepository;
    private final ConfigApi configApi;
    private final TasksApi tasksApi;
    private final SlotGameApi slotGameApi;
    private final CasualGameApi casualGameApi;
    private final VanityApi vanityApi;
    private final PromotionsApi promotionsApi;
    private final GameStoreApi gameStoreApi;
    private final ContestsApi contestsApi;
    private final EventsApi eventsApi;
    private final AgencyApi agencyApiRef;
    private final UserApi userApi;
    private final InviteApi inviteApi;
    private final RoomApi roomApi;
    private final RoomCupApi roomCupApi;
    private final ChatApi chatApi;
    private final UploadApi uploadApi;
    private final WalletApi walletApiRef;
    private final GiftApi giftApiRef;
    private final LuckyBoxesApi luckyBoxesApi;
    private final DramaApi dramaApi;
    private final GameAdsApi gameAdsApi;
    private final NotificationApi notificationApi;

    public final LoginUseCase loginUseCase;
    public final RegisterUseCase registerUseCase;
    public final SendOtpUseCase sendOtpUseCase;
    public final VerifyOtpUseCase verifyOtpUseCase;
    public final GuestLoginUseCase guestLoginUseCase;
    public final SocialLoginUseCase socialLoginUseCase;
    public final RefreshSessionUseCase refreshSessionUseCase;
    public final GetRoomUseCase getRoomUseCase;
    public final JoinRoomUseCase joinRoomUseCase;
    public final RaiseHandUseCase raiseHandUseCase;
    public final CreateRoomUseCase createRoomUseCase;
    public final ListRoomsUseCase listRoomsUseCase;
    public final GetConversationsUseCase getConversationsUseCase;
    public final GetMessagesUseCase getMessagesUseCase;
    public final SendMessageUseCase sendMessageUseCase;
    public final GetProfileUseCase getProfileUseCase;
    public final UpdateProfileUseCase updateProfileUseCase;
    public final GetWalletUseCase getWalletUseCase;
    public final GetRechargePackagesUseCase getRechargePackagesUseCase;
    public final VerifyPurchaseUseCase verifyPurchaseUseCase;
    public final GetGiftsUseCase getGiftsUseCase;
    public final SendGiftUseCase sendGiftUseCase;
    public final GetVipPlansUseCase getVipPlansUseCase;
    public final PurchaseVipUseCase purchaseVipUseCase;
    public final GetAgenciesUseCase getAgenciesUseCase;
    public final GetRankingsUseCase getRankingsUseCase;
    public final GetNotificationsUseCase getNotificationsUseCase;

    public AppContainer(Context context) {
        appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
        featureCache = new EncryptedFeatureCache(appContext);
        ioExecutor = Executors.newFixedThreadPool(4);
        database = Room.databaseBuilder(appContext, AuraDatabase.class, "auralive.db")
                .fallbackToDestructiveMigration()
                .build();

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // BODY logging freezes first-install UI under many parallel image/API calls.
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        String apiBase = ensureTrailingSlash(com.Dramizo.Series.util.ApiOrigin.apiV1());
        if (BuildConfig.DEBUG && BuildConfig.API_BASE_URL != null && !BuildConfig.API_BASE_URL.isEmpty()) {
            apiBase = ensureTrailingSlash(BuildConfig.API_BASE_URL);
        }
        okhttp3.Cache httpCache = new okhttp3.Cache(
                new java.io.File(appContext.getCacheDir(), "http_cache"),
                64L * 1024L * 1024L);
        OkHttpClient client = new OkHttpClient.Builder()
                .cache(httpCache)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    okhttp3.Request req = chain.request().newBuilder()
                            .header("User-Agent", "HamsLive/" + BuildConfig.VERSION_NAME
                                    + " (Android; okhttp)")
                            .build();
                    return chain.proceed(req);
                })
                // Render's free service can briefly sleep/restart and mobile networks
                // can drop an idle TCP connection. Retry only safe reads so a transient
                // disconnect does not blank the app or force the user to retry manually.
                .addInterceptor(chain -> {
                    okhttp3.Request request = chain.request();
                    if (!request.method().equalsIgnoreCase("GET")
                            && !request.method().equalsIgnoreCase("HEAD")) {
                        return chain.proceed(request);
                    }
                    java.io.IOException last = null;
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            return chain.proceed(request);
                        } catch (java.io.IOException error) {
                            last = error;
                            if (attempt < 2) {
                                try {
                                    Thread.sleep(attempt == 0 ? 350L : 900L);
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw error;
                                }
                            }
                        }
                    }
                    throw last;
                })
                .addInterceptor(new AuthInterceptor(sessionManager))
                .authenticator(new TokenAuthenticator(sessionManager, apiBase))
                .addInterceptor(logging)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(apiBase)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        AuthApi authApi = retrofit.create(AuthApi.class);
        UserApi userApi = retrofit.create(UserApi.class);
        RoomApi roomApi = retrofit.create(RoomApi.class);
        chatApi = retrofit.create(ChatApi.class);
        WalletApi walletApi = retrofit.create(WalletApi.class);
        GiftApi giftApi = retrofit.create(GiftApi.class);
        GameApi gameApi = retrofit.create(GameApi.class);
        VipApi vipApi = retrofit.create(VipApi.class);
        AgencyApi agencyApi = retrofit.create(AgencyApi.class);
        agencyApiRef = agencyApi;
        RankingApi rankingApi = retrofit.create(RankingApi.class);
        NotificationApi notificationApi = retrofit.create(NotificationApi.class);
        CosmeticsApi cosmeticsApi = retrofit.create(CosmeticsApi.class);
        configApi = retrofit.create(ConfigApi.class);
        tasksApi = retrofit.create(TasksApi.class);
        slotGameApi = retrofit.create(SlotGameApi.class);
        casualGameApi = retrofit.create(CasualGameApi.class);
        vanityApi = retrofit.create(VanityApi.class);
        promotionsApi = retrofit.create(PromotionsApi.class);
        gameStoreApi = retrofit.create(GameStoreApi.class);
        contestsApi = retrofit.create(ContestsApi.class);
        eventsApi = retrofit.create(EventsApi.class);
        this.userApi = userApi;
        this.inviteApi = retrofit.create(InviteApi.class);
        this.roomApi = roomApi;
        this.roomCupApi = retrofit.create(RoomCupApi.class);
        this.uploadApi = retrofit.create(UploadApi.class);
        this.walletApiRef = walletApi;
        this.giftApiRef = giftApi;
        this.luckyBoxesApi = retrofit.create(LuckyBoxesApi.class);
        this.dramaApi = retrofit.create(DramaApi.class);
        this.gameAdsApi = retrofit.create(GameAdsApi.class);
        this.notificationApi = notificationApi;

        authRepository = new AuthRepositoryImpl(authApi, sessionManager, database.userDao(), ioExecutor);
        userRepository = new UserRepositoryImpl(userApi, database.userDao(), ioExecutor, sessionManager);
        roomRepository = new RoomRepositoryImpl(roomApi, ioExecutor);
        chatRepository = new ChatRepositoryImpl(chatApi, database.chatMessageDao(), ioExecutor);
        walletRepository = new WalletRepositoryImpl(walletApi, database.walletDao(), ioExecutor);
        giftRepository = new GiftRepositoryImpl(giftApi, database.giftDao(), ioExecutor);
        gameRepository = new GameRepositoryImpl(gameApi, ioExecutor);
        vipRepository = new VipRepositoryImpl(vipApi, ioExecutor);
        agencyRepository = new AgencyRepositoryImpl(agencyApi, ioExecutor);
        rankingRepository = new RankingRepositoryImpl(rankingApi, ioExecutor);
        notificationRepository = new NotificationRepositoryImpl(notificationApi, ioExecutor, sessionManager, context);
        cosmeticsRepository = new CosmeticsRepositoryImpl(cosmeticsApi);

        billingHelper = new BillingHelper(appContext);

        loginUseCase = new LoginUseCase(authRepository);
        registerUseCase = new RegisterUseCase(authRepository);
        sendOtpUseCase = new SendOtpUseCase(authRepository);
        verifyOtpUseCase = new VerifyOtpUseCase(authRepository);
        guestLoginUseCase = new GuestLoginUseCase(authRepository);
        socialLoginUseCase = new SocialLoginUseCase(authRepository);
        refreshSessionUseCase = new RefreshSessionUseCase(authApi, sessionManager);
        getRoomUseCase = new GetRoomUseCase(roomRepository);
        joinRoomUseCase = new JoinRoomUseCase(roomRepository);
        raiseHandUseCase = new RaiseHandUseCase(roomRepository);
        createRoomUseCase = new CreateRoomUseCase(roomRepository);
        listRoomsUseCase = new ListRoomsUseCase(roomRepository);
        getConversationsUseCase = new GetConversationsUseCase(chatRepository);
        getMessagesUseCase = new GetMessagesUseCase(chatRepository);
        sendMessageUseCase = new SendMessageUseCase(chatRepository);
        getProfileUseCase = new GetProfileUseCase(userRepository);
        updateProfileUseCase = new UpdateProfileUseCase(userRepository);
        getWalletUseCase = new GetWalletUseCase(walletRepository);
        getRechargePackagesUseCase = new GetRechargePackagesUseCase(walletRepository);
        verifyPurchaseUseCase = new VerifyPurchaseUseCase(walletRepository);
        getGiftsUseCase = new GetGiftsUseCase(giftRepository);
        sendGiftUseCase = new SendGiftUseCase(giftRepository);
        getVipPlansUseCase = new GetVipPlansUseCase(vipRepository);
        purchaseVipUseCase = new PurchaseVipUseCase(vipRepository);
        getAgenciesUseCase = new GetAgenciesUseCase(agencyRepository);
        getRankingsUseCase = new GetRankingsUseCase(rankingRepository);
        getNotificationsUseCase = new GetNotificationsUseCase(notificationRepository);
    }

    private static String ensureTrailingSlash(String url) {
        if (url == null || url.isEmpty()) return com.Dramizo.Series.util.ApiOrigin.apiV1();
        return url.endsWith("/") ? url : url + "/";
    }

    public SessionManager getSessionManager() { return sessionManager; }
    public EncryptedFeatureCache getFeatureCache() { return featureCache; }
    public AuraDatabase getDatabase() { return database; }
    public ExecutorService getIoExecutor() { return ioExecutor; }
    public BillingHelper getBillingHelper() { return billingHelper; }
    public AuthRepository getAuthRepository() { return authRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public RoomRepository getRoomRepository() { return roomRepository; }
    public ChatRepository getChatRepository() { return chatRepository; }
    public WalletRepository getWalletRepository() { return walletRepository; }
    public GiftRepository getGiftRepository() { return giftRepository; }
    public GameRepository getGameRepository() { return gameRepository; }
    public NotificationRepository getNotificationRepository() { return notificationRepository; }
    public Context getAppContext() { return appContext; }
    public VipRepository getVipRepository() { return vipRepository; }
    public AgencyRepository getAgencyRepository() { return agencyRepository; }
    public RankingRepository getRankingRepository() { return rankingRepository; }
    public CosmeticsRepository getCosmeticsRepository() { return cosmeticsRepository; }
    public ConfigApi getConfigApi() { return configApi; }
    public TasksApi getTasksApi() { return tasksApi; }
    public SlotGameApi getSlotGameApi() { return slotGameApi; }
    public CasualGameApi getCasualGameApi() { return casualGameApi; }
    public VanityApi getVanityApi() { return vanityApi; }
    public PromotionsApi getPromotionsApi() { return promotionsApi; }
    public GameStoreApi getGameStoreApi() { return gameStoreApi; }
    public ContestsApi getContestsApi() { return contestsApi; }
    public EventsApi getEventsApi() { return eventsApi; }
    public AgencyApi getAgencyApi() { return agencyApiRef; }
    public UserApi getUserApi() { return userApi; }
    public InviteApi getInviteApi() { return inviteApi; }
    public RoomApi getRoomApi() { return roomApi; }
    public RoomCupApi getRoomCupApi() { return roomCupApi; }
    public ChatApi getChatApi() { return chatApi; }
    public UploadApi getUploadApi() { return uploadApi; }
    public WalletApi getWalletApi() { return walletApiRef; }
    public GiftApi getGiftApi() { return giftApiRef; }
    public LuckyBoxesApi getLuckyBoxesApi() { return luckyBoxesApi; }
    public DramaApi getDramaApi() { return dramaApi; }
    public GameAdsApi getGameAdsApi() { return gameAdsApi; }
    public NotificationApi getNotificationApi() { return notificationApi; }
}

