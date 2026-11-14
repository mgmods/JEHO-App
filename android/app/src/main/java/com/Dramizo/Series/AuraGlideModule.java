package com.Dramizo.Series;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Large disk/memory cache like Mikoo — do NOT force RGB_565 / global dontAnimate
 * (that breaks GIF/WebP/SVGA previews for frames & entries).
 * Durable art freshness is driven by {@code mediaAssetEpoch} (MediaAssetSync).
 */
@GlideModule
public final class AuraGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        long mem = Runtime.getRuntime().maxMemory() / 8;
        builder.setMemoryCache(new LruResourceCache(Math.max(mem, 24L * 1024L * 1024L)));
        builder.setDiskCache(new InternalCacheDiskCacheFactory(
                context, "glide_images", 512L * 1024L * 1024L));
        builder.setDefaultRequestOptions(
                new RequestOptions().diskCacheStrategy(DiskCacheStrategy.AUTOMATIC));
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide,
                                   @NonNull Registry registry) {
        Interceptor ua = chain -> {
            Request req = chain.request().newBuilder()
                    .header("User-Agent", "HamsLive/" + BuildConfig.VERSION_NAME
                            + " (Android; okhttp)")
                    .build();
            return chain.proceed(req);
        };
        Cache cache = new Cache(new File(context.getCacheDir(), "glide_http"), 128L * 1024L * 1024L);
        OkHttpClient client = new OkHttpClient.Builder()
                .cache(cache)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(ua)
                .addNetworkInterceptor(chain -> {
                    okhttp3.Response resp = chain.proceed(chain.request());
                    String cc = resp.header("Cache-Control");
                    if (cc != null && (cc.contains("no-store") || cc.contains("no-cache"))) {
                        return resp;
                    }
                    return resp.newBuilder()
                            .header("Cache-Control", "public, max-age=3600, must-revalidate")
                            .removeHeader("Pragma")
                            .build();
                })
                .build();
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(client));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
