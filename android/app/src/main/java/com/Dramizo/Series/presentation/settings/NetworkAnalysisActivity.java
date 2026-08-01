package com.Dramizo.Series.presentation.settings;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;

import com.Dramizo.Series.databinding.ActivityNetworkAnalysisBinding;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.MikooLoadingAnim;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mikoo NetworkAnalysisActivity — diagnosis text, never exposes API host/IP.
 */
public class NetworkAnalysisActivity extends ThemedActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ActivityNetworkAnalysisBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNetworkAnalysisBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());
        MikooLoadingAnim.bind(binding.svLoading);
        runDiagnosis();
    }

    private void runDiagnosis() {
        io.execute(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Network Diagnosis\n");
            sb.append("─────────────────\n");

            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            boolean online = false;
            String transport = "Unknown";
            if (cm != null) {
                Network n = cm.getActiveNetwork();
                NetworkCapabilities caps = n != null ? cm.getNetworkCapabilities(n) : null;
                if (caps != null) {
                    online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "Wi‑Fi";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "Cellular";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "Ethernet";
                }
            }
            sb.append("Connectivity: ").append(online ? "OK" : "Offline").append('\n');
            sb.append("Transport: ").append(transport).append('\n');

            Proxy systemProxy = Proxy.NO_PROXY;
            try {
                java.net.ProxySelector selector = java.net.ProxySelector.getDefault();
                if (selector != null) {
                    java.util.List<Proxy> list = selector.select(new URL("https://www.google.com").toURI());
                    if (list != null && !list.isEmpty()) systemProxy = list.get(0);
                }
            } catch (Exception ignored) {
            }
            if (systemProxy == null || systemProxy.type() == Proxy.Type.DIRECT) {
                sb.append("Proxy: Direct (no system proxy)\n");
            } else {
                InetSocketAddress addr = (InetSocketAddress) systemProxy.address();
                sb.append("Proxy: ").append(systemProxy.type());
                if (addr != null) sb.append(" · ").append(addr.getHostName()).append(':').append(addr.getPort());
                sb.append('\n');
            }

            long dnsMs = pingHost("dns.google", 443);
            sb.append("DNS latency: ").append(dnsMs >= 0 ? dnsMs + " ms" : "fail").append('\n');
            long httpsMs = httpHead("https://www.google.com/generate_204");
            sb.append("HTTPS check: ").append(httpsMs >= 0 ? "OK · " + httpsMs + " ms" : "fail").append('\n');
            sb.append("\nTip: If chat/rooms fail while HTTPS is OK, retry after switching Wi‑Fi / mobile data.");

            String report = sb.toString();
            runOnUiThread(() -> {
                if (binding == null) return;
                binding.tvDiagnosis.setText(report);
                MikooLoadingAnim.stop(binding.svLoading);
                binding.svLoading.setVisibility(View.GONE);
            });
        });
    }

    private static long pingHost(String host, int port) {
        long t0 = System.currentTimeMillis();
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new InetSocketAddress(host, port), 4000);
            return System.currentTimeMillis() - t0;
        } catch (Exception e) {
            return -1;
        }
    }

    private static long httpHead(String url) {
        long t0 = System.currentTimeMillis();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestMethod("GET");
            c.connect();
            int code = c.getResponseCode();
            if (code >= 200 && code < 400) return System.currentTimeMillis() - t0;
            return -1;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        binding = null;
        super.onDestroy();
    }
}
