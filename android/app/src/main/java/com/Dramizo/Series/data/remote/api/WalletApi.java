package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import java.util.Map;

public interface WalletApi {
    @GET("wallet")
    Call<ApiResponse<WalletDtos.WalletDto>> getWallet();

    @GET("wallet/economy-config")
    Call<ApiResponse<WalletDtos.EconomyConfig>> economyConfig();

    @GET("wallet/packages")
    Call<ApiResponse<WalletDtos.PackagesResult>> packages();

    @GET("wallet/withdraw-packages")
    Call<ApiResponse<WalletDtos.WithdrawPackagesResult>> withdrawPackages();

    @POST("payments/google-play/verify")
    Call<ApiResponse<WalletDtos.WalletDto>> verifyPurchase(@Body WalletDtos.VerifyPurchaseRequest body);

    @POST("payments/binance-wallet/deposit-address")
    Call<ApiResponse<WalletDtos.BinanceWalletDepositAddressResult>> binanceWalletDepositAddress(
            @Body WalletDtos.BinanceWalletDepositRequest body);

    @POST("payments/binance-wallet/orders")
    Call<ApiResponse<WalletDtos.BinanceWalletOrderResult>> createBinanceWalletOrder(
            @Body WalletDtos.BinanceWalletOrderRequest body);

    @GET("payments/binance-wallet/orders/{id}")
    Call<ApiResponse<WalletDtos.BinanceWalletStatusResult>> binanceWalletOrderStatus(
            @retrofit2.http.Path("id") String id);

    @POST("payments/binance-pay/orders")
    Call<ApiResponse<WalletDtos.BinanceWalletOrderResult>> createBinancePayOrder(
            @Body WalletDtos.BinancePayCreateRequest body);

    @POST("payments/fourthwall/checkout")
    Call<ApiResponse<WalletDtos.FourthwallCheckoutResult>> createFourthwallCheckout(
            @Body WalletDtos.FourthwallCheckoutRequest body);

    @GET("payments/fourthwall/orders/{id}")
    Call<ApiResponse<WalletDtos.FourthwallOrderStatus>> fourthwallOrderStatus(
            @retrofit2.http.Path("id") String id);

    @POST("payments/sham-cash/orders")
    Call<ApiResponse<WalletDtos.ShamCashOrderResult>> createShamCashOrder(
            @Body java.util.Map<String, Object> body);

    @GET("payments/sham-cash/orders/{id}")
    Call<ApiResponse<WalletDtos.ShamCashOrderInfo>> shamCashOrderStatus(
            @retrofit2.http.Path("id") String id);

    @GET("payments/binance-pay/orders/{id}")
    Call<ApiResponse<WalletDtos.BinanceWalletStatusResult>> binancePayOrderStatus(
            @retrofit2.http.Path("id") String id);

    @POST("wallet/exchange")
    Call<ApiResponse<WalletDtos.WalletDto>> exchange(@Body WalletDtos.ExchangeRequest body);

    @POST("wallet/host-trade")
    Call<ApiResponse<Map<String, Object>>> hostTrade(@Body WalletDtos.HostTradeRequest body);

    @POST("wallet/withdraw")
    Call<ApiResponse<Object>> withdraw(@Body WalletDtos.WithdrawRequest body);

    @GET("wallet/withdraws")
    Call<ApiResponse<WalletDtos.WithdrawList>> withdraws();

    @GET("wallet/transactions")
    Call<ApiResponse<MiscDtos.ListResult<WalletDtos.TransactionDto>>> transactions(@retrofit2.http.Query("page") int page);

    @POST("recharge-agents/apply")
    Call<ApiResponse<Map<String, Object>>> applyRechargeAgent(@Body Map<String, Object> body);

    @GET("recharge-agents/me")
    Call<ApiResponse<Map<String, Object>>> rechargeAgentMe();

    @GET("recharge-agents/config")
    Call<ApiResponse<Map<String, Object>>> rechargeAgentConfig();

    @GET("recharge-agents/quote")
    Call<ApiResponse<Map<String, Object>>> rechargeAgentQuote(@Query("coins") long coins);

    @GET("recharge-agents/payment-details")
    Call<ApiResponse<Map<String, Object>>> rechargeAgentPaymentDetails(
            @Query("network") String network);

    @POST("recharge-agents/deposit-order")
    Call<ApiResponse<Map<String, Object>>> createAgentDepositOrder(@Body Map<String, Object> body);

    @GET("recharge-agents/resolve-user")
    Call<ApiResponse<Map<String, Object>>> resolveRechargeRecipient(
            @Query("query") String query);

    @GET("recharge-agents/user-overview")
    Call<ApiResponse<Map<String, Object>>> agentUserOverview(@Query("query") String query);

    @GET("recharge-agents/withdraw-agents")
    Call<ApiResponse<WalletDtos.AgentDirectoryResult>> withdrawAgents();

    @GET("recharge-agents/withdraws")
    Call<ApiResponse<Map<String, Object>>> agentWithdraws();

    @POST("recharge-agents/withdraws/{id}/complete")
    Call<ApiResponse<Map<String, Object>>> agentCompleteWithdraw(
            @retrofit2.http.Path("id") String id, @Body Map<String, Object> body);

    @POST("recharge-agents/withdraws/{id}/reject")
    Call<ApiResponse<Map<String, Object>>> agentRejectWithdraw(
            @retrofit2.http.Path("id") String id, @Body Map<String, Object> body);

    @POST("recharge-agents/sell")
    Call<ApiResponse<Map<String, Object>>> sellAgentRecharge(@Body Map<String, Object> body);

    @GET("recharge-agents/recharges")
    Call<ApiResponse<Map<String, Object>>> agentRecharges();

    @GET("recharge-agents/directory")
    Call<ApiResponse<WalletDtos.AgentDirectoryResult>> rechargeAgentDirectory(
            @Query("country") String country);
}
