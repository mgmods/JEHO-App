package com.Dramizo.Series.data.remote.dto;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public final class WalletDtos {
    private WalletDtos() {}

    public static class WalletDto {
        @SerializedName("id") public String id;
        @SerializedName("userId") public String userId;
        @SerializedName("coins") public long coins;
        /** Personal-room gift pool — platform withdraw */
        @SerializedName("diamonds") public long diamonds;
        /** Agency-room host share + owner commission pool */
        @SerializedName("agencyDiamonds") public long agencyDiamonds;
        @SerializedName("personalUsd") public double personalUsd;
        @SerializedName("agencyUsd") public double agencyUsd;
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
        /** Host↔host trade collection (التاجر). */
        @SerializedName("traderDiamonds") public long traderDiamonds;
        @SerializedName("silverCoins") public long silverCoins;
        @SerializedName("gamePoints") public long gamePoints;
        @SerializedName("totalRecharged") public long totalRecharged;
        @SerializedName("currency") public String currency;
        @SerializedName("canWithdraw") public boolean canWithdraw;
        @SerializedName("canWithdrawAgency") public boolean canWithdrawAgency;
    }

    public static class EconomyConfig {
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
        @SerializedName("diamondCoinRate") public double diamondCoinRate;
        @SerializedName("minWithdrawDiamonds") public long minWithdrawDiamonds;
        @SerializedName("withdrawTargetDiamonds") public long withdrawTargetDiamonds;
        @SerializedName("coinToSilverRate") public long coinToSilverRate;
        @SerializedName("silverToCoinRate") public double silverToCoinRate;
        @SerializedName("minSilverExchange") public long minSilverExchange;
        @SerializedName("currency") public String currency;
        @SerializedName("managedByAdmin") public boolean managedByAdmin;
    }

    public static class RechargePackageDto {
        @SerializedName("id") public String id;
        @SerializedName("sku") public String sku;
        @SerializedName("coins") public int coins;
        @SerializedName("bonusCoins") public int bonusCoins;
        @SerializedName("priceUsd") public double priceUsd;
        @SerializedName("label") public String label;
        @SerializedName("popular") public boolean popular;
        @SerializedName("iconUrl") public String iconUrl;
        @SerializedName("imageUrl") public String imageUrl;
        /** Filled from Google Play Billing (Play Console), not from the API. */
        public transient String playPriceLabel;
        /** Local currency amount from Play (`priceAmountMicros / 1e6`). */
        public transient double playPriceAmount;
        public transient boolean playPriceLoaded;

        /** Prefer Play Console formatted price; fall back to server USD. */
        public String displayPrice() {
            if (playPriceLabel != null && !playPriceLabel.isEmpty()) return playPriceLabel;
            return usdPriceLabel();
        }

        /** Always USD from server — use for card / crypto / agent (not Play local currency). */
        public String usdPriceLabel() {
            return String.format(java.util.Locale.US, "$%.2f", priceUsd);
        }

        public double amountForVerify() {
            return playPriceLoaded ? playPriceAmount : priceUsd;
        }
    }

    public static class VerifyPurchaseRequest {
        @SerializedName("productId") public String productId;
        @SerializedName("purchaseToken") public String purchaseToken;
        @SerializedName("orderId") public String orderId;
        @SerializedName("coins") public int coins;
        @SerializedName("amountFiat") public double amountFiat;
        public VerifyPurchaseRequest(String productId, String purchaseToken, String orderId,
                                     int coins, double amountFiat) {
            this.productId = productId;
            this.purchaseToken = purchaseToken;
            this.orderId = orderId;
            this.coins = coins;
            this.amountFiat = amountFiat;
        }
    }

    public static class PackagesResult {
        @SerializedName("items") public List<RechargePackageDto> items;
    }

    public static class WithdrawPackageDto {
        @SerializedName("id") public String id;
        @SerializedName("diamonds") public long diamonds;
        @SerializedName("usd") public double usd;
        @SerializedName("label") public String label;
        @SerializedName("currency") public String currency;
        public transient boolean selected;
    }

    public static class WithdrawPackagesResult {
        @SerializedName("items") public List<WithdrawPackageDto> items;
        @SerializedName("diamondUsdRate") public double diamondUsdRate;
        @SerializedName("minWithdrawDiamonds") public long minWithdrawDiamonds;
        @SerializedName("currency") public String currency;
    }

    public static class BinancePayCreateRequest {
        @SerializedName("sku") public String sku;
        public BinancePayCreateRequest(String sku) { this.sku = sku; }
    }

    public static class BinanceWalletDepositRequest {
        @SerializedName("network") public String network;
        public BinanceWalletDepositRequest(String network) { this.network = network; }
    }

    public static class BinanceWalletOrderRequest {
        @SerializedName("sku") public String sku;
        @SerializedName("network") public String network;
        public BinanceWalletOrderRequest(String sku, String network) {
            this.sku = sku;
            this.network = network;
        }
    }

    public static class BinanceWalletOrderDto {
        @SerializedName("id") public String id;
        @SerializedName("status") public String status;
        @SerializedName("sku") public String sku;
        @SerializedName("coins") public int coins;
        @SerializedName("bonusCoins") public int bonusCoins;
        @SerializedName("amountFiat") public double amountFiat;
        @SerializedName("currency") public String currency;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("providerPaymentId") public String providerPaymentId;
        @SerializedName("providerPayload") public java.util.Map<String, Object> providerPayload;
    }

    public static class BinanceWalletDepositAddressResult {
        @SerializedName("coin") public String coin;
        @SerializedName("network") public String network;
        @SerializedName("address") public String address;
        @SerializedName("tag") public String tag;
    }

    public static class BinanceWalletOrderResult {
        @SerializedName("order") public BinanceWalletOrderDto order;
        @SerializedName("coin") public String coin;
        @SerializedName("network") public String network;
        @SerializedName("address") public String address;
        @SerializedName("tag") public String tag;
        @SerializedName("amount") public double amount;
        @SerializedName("expiresAt") public String expiresAt;
        @SerializedName("message") public String message;
    }

    public static class BinanceWalletStatusResult {
        @SerializedName("order") public BinanceWalletOrderDto order;
        @SerializedName("coin") public String coin;
        @SerializedName("network") public String network;
        @SerializedName("address") public String address;
        @SerializedName("tag") public String tag;
        @SerializedName("amount") public Double amount;
        @SerializedName("expiresAt") public String expiresAt;
    }

    public static class FourthwallCheckoutRequest {
        @SerializedName("sku") public String sku;
        public FourthwallCheckoutRequest(String sku) { this.sku = sku; }
    }

    public static class FourthwallCheckoutResult {
        @SerializedName("order") public BinanceWalletOrderDto order;
        @SerializedName("checkoutUrl") public String checkoutUrl;
        @SerializedName("cartId") public String cartId;
        @SerializedName("claimCode") public String claimCode;
        @SerializedName("instruction") public String instruction;
    }

    public static class FourthwallOrderStatus {
        @SerializedName("id") public String id;
        @SerializedName("status") public String status;
        @SerializedName("sku") public String sku;
        @SerializedName("coins") public int coins;
        @SerializedName("bonusCoins") public int bonusCoins;
        @SerializedName("claimCode") public String claimCode;
        @SerializedName("checkoutUrl") public String checkoutUrl;
        @SerializedName("completedAt") public String completedAt;
    }

    public static class ExchangeRequest {
        @SerializedName("diamonds") public int diamonds;
        public ExchangeRequest(int diamonds) { this.diamonds = diamonds; }
    }

    public static class HostTradeRequest {
        @SerializedName("toUserId") public String toUserId;
        @SerializedName("diamonds") public int diamonds;
        public HostTradeRequest(String toUserId, int diamonds) {
            this.toUserId = toUserId;
            this.diamonds = diamonds;
        }
    }

    public static class SilverExchangeRequest {
        @SerializedName("silver") public int silver;
        public SilverExchangeRequest(int silver) { this.silver = silver; }
    }

    public static class BuySilverRequest {
        @SerializedName("coins") public int coins;
        public BuySilverRequest(int coins) { this.coins = coins; }
    }

    public static class WithdrawRequest {
        @SerializedName("diamonds") public int diamonds;
        @SerializedName("method") public String method;
        @SerializedName("agentId") public String agentId;
        @SerializedName("payoutDetails") public java.util.Map<String, Object> payoutDetails;

        public WithdrawRequest(int diamonds, String method, String account) {
            this.diamonds = diamonds;
            this.method = method;
            this.payoutDetails = new java.util.HashMap<>();
            this.payoutDetails.put("account", account != null ? account : "");
        }

        public static WithdrawRequest viaAgent(int diamonds, String agentId, String note) {
            WithdrawRequest r = new WithdrawRequest(diamonds, "agent", note != null ? note : "");
            r.agentId = agentId;
            r.payoutDetails.put("channel", "agent");
            return r;
        }
    }

    public static class WithdrawDto {
        @SerializedName("id") public String id;
        @SerializedName("diamonds") public long diamonds;
        @SerializedName("amountFiat") public double amountFiat;
        @SerializedName("method") public String method;
        @SerializedName("status") public String status;
        @SerializedName("adminNote") public String adminNote;
        @SerializedName("createdAt") public String createdAt;
    }

    public static class WithdrawList {
        @SerializedName("items") public List<WithdrawDto> items;
    }

    public static class TransactionDto {
        @SerializedName("id") public String id;
        @SerializedName("type") public String type;
        @SerializedName("currency") public String currency;
        @SerializedName("amount") public long amount;
        @SerializedName("balanceAfter") public long balanceAfter;
        @SerializedName("description") public String description;
        @SerializedName("createdAt") public String createdAt;
    }

    public static class TransactionList {
        @SerializedName("items") public List<TransactionDto> items;
    }

    public static class AgentDirectoryResult {
        @SerializedName("items") public List<AgentDirectoryEntry> items;
        @SerializedName("countries") public List<String> countries;
        @SerializedName("total") public int total;
    }

    public static class AgentDirectoryEntry {
        @SerializedName("id") public String id;
        @SerializedName("source") public String source;
        @SerializedName("displayName") public String displayName;
        @SerializedName("country") public String country;
        @SerializedName("whatsapp") public String whatsapp;
        @SerializedName("telegram") public String telegram;
        @SerializedName("notes") public String notes;
        @SerializedName("avatarUrl") public String avatarUrl;
        @SerializedName("coverUrl") public String coverUrl;
    }

    public static class ShamCashOrderResult {
        @SerializedName("order") public ShamCashOrderInfo order;
        @SerializedName("payment") public ShamCashPaymentInfo payment;
    }

    public static class ShamCashOrderInfo {
        @SerializedName("id") public String id;
        @SerializedName("status") public String status;
        @SerializedName("sku") public String sku;
        @SerializedName("coins") public int coins;
        @SerializedName("bonusCoins") public int bonusCoins;
        @SerializedName("amountFiat") public double amountFiat;
        @SerializedName("claimCode") public String claimCode;
    }

    public static class ShamCashPaymentInfo {
        @SerializedName("accountId") public String accountId;
        @SerializedName("accountName") public String accountName;
        @SerializedName("displayName") public String displayName;
        @SerializedName("whatsapp") public String whatsapp;
        @SerializedName("qrPayload") public String qrPayload;
        @SerializedName("instructions") public String instructions;
    }
}
