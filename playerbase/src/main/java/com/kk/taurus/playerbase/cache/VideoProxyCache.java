package com.kk.taurus.playerbase.cache;

import android.content.Context;
import android.net.Uri;

import com.danikula.videocache.CacheListener;
import com.danikula.videocache.HttpProxyCacheServer;
import com.danikula.videocache.file.FileNameGenerator;
import com.kk.taurus.playerbase.log.PLog;

import java.io.File;

/**
 * @author Tao.Yj
 * created: 2019-06-14
 * desc:
 */
public class VideoProxyCache implements CacheListener {
    private static final String TAG = VideoProxyCache.class.getName();
    private static VideoProxyCache ourInstance;

    public static VideoProxyCache getInstance(Context context) {
        if (ourInstance == null) {
            synchronized (VideoProxyCache.class) {
                if (ourInstance == null) {
                    ourInstance = new VideoProxyCache(context.getApplicationContext());
                }
            }
        }
        return ourInstance;
    }

    private VideoProxyCache(Context context) {
        proxy = new HttpProxyCacheServer.Builder(context)
                // 1 Gb for cache
                .maxCacheSize(1024 * 1024 * 1024)
                .build();

    }

    private HttpProxyCacheServer proxy;

    public String registerProxyUrl(String url) {
        String proxyUrl = proxy.getProxyUrl(url);
        proxy.registerCacheListener(this, url);
        return proxyUrl;
    }

    public void unRegisterproxyUrl(String url) {
        proxy.unregisterCacheListener(this, url);
    }

    @Override
    public void onCacheAvailable(File cacheFile, String url, int percentsAvailable) {
        PLog.d(TAG, String.format("onCacheAvailable. percents: %d, file: %s, url: %s", percentsAvailable, cacheFile, url));
    }
}
