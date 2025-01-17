/*
 * Copyright 2017 jiajunhui<junhui_jia@163.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.kk.taurus.exoplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.danikula.videocache.StorageUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.AssetDataSource;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheSpan;
import com.google.android.exoplayer2.upstream.cache.CacheUtil;
import com.google.android.exoplayer2.upstream.cache.ContentMetadata;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.kk.taurus.exoplayer.source.GSYExoHttpDataSource;
import com.kk.taurus.exoplayer.source.GSYExoHttpDataSourceFactory;
import com.kk.taurus.playerbase.config.AppContextAttach;
import com.kk.taurus.playerbase.config.PlayerConfig;
import com.kk.taurus.playerbase.config.PlayerLibrary;
import com.kk.taurus.playerbase.entity.DataSource;
import com.kk.taurus.playerbase.entity.DecoderPlan;
import com.kk.taurus.playerbase.entity.TimedTextSource;
import com.kk.taurus.playerbase.event.BundlePool;
import com.kk.taurus.playerbase.event.EventKey;
import com.kk.taurus.playerbase.event.OnErrorEventListener;
import com.kk.taurus.playerbase.event.OnPlayerEventListener;
import com.kk.taurus.playerbase.log.PLog;
import com.kk.taurus.playerbase.player.BaseInternalPlayer;
import com.kk.taurus.playerbase.player.IPlayer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;


public class ExoMediaPlayer extends BaseInternalPlayer {

    private final String TAG = "ExoMediaPlayer";

    public static final int PLAN_ID = 200;

    private final Context mAppContext;
    private SimpleExoPlayer mInternalPlayer;


    private int mVideoWidth, mVideoHeight;

    private int mStartPos = -1;

    private boolean isPreparing = true;
    private boolean isBuffering = false;
    private boolean isPendingSeek = false;

    private final DefaultBandwidthMeter mBandwidthMeter;

    private static final long DEFAULT_MAX_SIZE = 512 * 1024 * 1024;

    private static Cache mCache;
    private String mSourceUrl;
    private boolean isCached = false;
    private HashMap<String, String> heads;

    /**
     * 忽律Https证书校验
     */
    private static boolean mSkipSSLChain = true;


    public static void init(Context context) {
        PlayerConfig.addDecoderPlan(new DecoderPlan(
                PLAN_ID,
                ExoMediaPlayer.class.getName(),
                "exoplayer"));
        PlayerConfig.setDefaultPlanId(PLAN_ID);
        PlayerLibrary.init(context);
    }

    @TargetApi(Build.VERSION_CODES.N)
    public ExoMediaPlayer() {
        mAppContext = AppContextAttach.getApplicationContext();
        RenderersFactory renderersFactory = new DefaultRenderersFactory(mAppContext,
                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        DefaultTrackSelector trackSelector =
                new DefaultTrackSelector();
        mInternalPlayer = ExoPlayerFactory.newSimpleInstance(mAppContext, renderersFactory, trackSelector);

        // Measures bandwidth during playback. Can be null if not required.
        mBandwidthMeter = new DefaultBandwidthMeter();

        mBandwidthMeter.addEventListener(new Handler(), new BandwidthMeter.EventListener() {
            @Override
            public void onBandwidthSample(int elapsedMs, long bytesTransferred, long bitrateEstimate) {
                PLog.d(TAG, "onBandwidthEstimate: 带宽估计 总加载时间" + elapsedMs +
                        "   已加载的:" + bytesTransferred / 1024 + "kb" +
                        "\t应该是网速" + String.format("%.2f", bitrateEstimate / 1024.00 / 1024.00 * 1000.00 / elapsedMs)+ "Mb/s");
            }
        });

        mInternalPlayer.addListener(mEventListener);


    }

    @Override
    public void setDataSource(DataSource dataSource) {
        updateStatus(STATE_INITIALIZED);
        mInternalPlayer.addVideoListener(mVideoListener);
        String data = dataSource.getData();
        mSourceUrl = data;
        Uri uri = dataSource.getUri();
        String assetsPath = dataSource.getAssetsPath();
        int rawId = dataSource.getRawId();

        Uri videoUri = null;

        if (!TextUtils.isEmpty(data)) {
            videoUri = Uri.parse(data);
        } else if (uri != null) {
            videoUri = uri;
        } else if (!TextUtils.isEmpty(assetsPath)) {
            try {
                DataSpec dataSpec = new DataSpec(DataSource.buildAssetsUri(assetsPath));
                AssetDataSource assetDataSource = new AssetDataSource(mAppContext);
                assetDataSource.open(dataSpec);
                videoUri = assetDataSource.getUri();
            } catch (AssetDataSource.AssetDataSourceException e) {
                e.printStackTrace();
            }
        } else if (rawId > 0) {
            try {
                DataSpec dataSpec = new DataSpec(RawResourceDataSource.buildRawResourceUri(dataSource.getRawId()));
                RawResourceDataSource rawResourceDataSource = new RawResourceDataSource(mAppContext);
                rawResourceDataSource.open(dataSpec);
                videoUri = rawResourceDataSource.getUri();
            } catch (RawResourceDataSource.RawResourceDataSourceException e) {
                e.printStackTrace();
            }
        }

        if (videoUri == null) {
            Bundle bundle = BundlePool.obtain();
            bundle.putString(EventKey.STRING_DATA, "Incorrect setting of playback data!");
            submitErrorEvent(OnErrorEventListener.ERROR_EVENT_IO, bundle);
            return;
        }

        //create DefaultDataSourceFactory
//        com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory =
//                new DefaultDataSourceFactory(mAppContext,
//                        Util.getUserAgent(mAppContext, mAppContext.getPackageName()), mBandwidthMeter);

        //if scheme is http or https and DataSource contain extra data, use DefaultHttpDataSourceFactory.
//        String scheme = videoUri.getScheme();
        heads = dataSource.getExtra();

//        if(heads!=null && heads.size()>0 &&
//                ("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme))){
//            dataSourceFactory = new DefaultHttpDataSourceFactory(
//                    Util.getUserAgent(mAppContext, mAppContext.getPackageName()));
//            ((DefaultHttpDataSourceFactory)dataSourceFactory).getDefaultRequestProperties().set(heads);
//        }

        // Prepare the player with the source.
        isPreparing = true;

        //create MediaSource
        MediaSource mediaSource = getMediaSource(videoUri,
                false,
                PlayerConfig.isUseCache() || dataSource.isCache(),
                false,
                StorageUtils.getIndividualCacheDirectory(mAppContext));


        //handle timed text source
        TimedTextSource timedTextSource = dataSource.getTimedTextSource();
        if (timedTextSource != null) {
            Format format = Format.createTextSampleFormat(null, timedTextSource.getMimeType(), timedTextSource.getFlag(), null);
            MediaSource timedTextMediaSource = new SingleSampleMediaSource.Factory(new DefaultDataSourceFactory(mAppContext,
                    Util.getUserAgent(mAppContext, mAppContext.getPackageName())))
                    .createMediaSource(Uri.parse(timedTextSource.getPath()), format, C.TIME_UNSET);
            //merge MediaSource and timedTextMediaSource.
            mediaSource = new MergingMediaSource(mediaSource, timedTextMediaSource);
        }

        mInternalPlayer.prepare(mediaSource);
        mInternalPlayer.setPlayWhenReady(false);

        Bundle sourceBundle = BundlePool.obtain();
        sourceBundle.putSerializable(EventKey.SERIALIZABLE_DATA, dataSource);
        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_DATA_SOURCE_SET, sourceBundle);

    }

//    private MediaSource getMediaSource(Uri uri, com.google.android.exoplayer2.upstream.DataSource.Factory dataSourceFactory) {
//        int contentType = Util.inferContentType(uri);
//        switch (contentType) {
//            case C.TYPE_DASH:
//                return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
//            case C.TYPE_SS:
//                return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
//            case C.TYPE_HLS:
//                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
//            case C.TYPE_OTHER:
//            default:
//                // This is the MediaSource representing the media to be played.
//                return new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
//        }
//    }


    /**
     * @param dataSource  链接
     * @param preview     是否带上header，默认有header自动设置为true??   测量播放带宽，如果不需要可以传null
     * @param cacheEnable 是否需要缓存
     * @param isLooping   是否循环
     * @param cacheDir    自定义缓存目录
     */
    public MediaSource getMediaSource(Uri dataSource, boolean preview, boolean cacheEnable, boolean isLooping, File cacheDir) {
        MediaSource mediaSource;
        int contentType = Util.inferContentType(dataSource);
        switch (contentType) {
            case C.TYPE_SS:
                mediaSource = new SsMediaSource.Factory(
                        new DefaultSsChunkSource.Factory(getDataSourceFactoryCache(mAppContext, cacheEnable, preview, cacheDir)),
                        new DefaultDataSourceFactory(mAppContext, null,
                                getHttpDataSourceFactory(mAppContext, preview))).createMediaSource(dataSource);
                break;
            case C.TYPE_DASH:
                mediaSource = new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(getDataSourceFactoryCache(mAppContext, cacheEnable, preview, cacheDir)),
                        new DefaultDataSourceFactory(mAppContext, null,
                                getHttpDataSourceFactory(mAppContext, preview))).createMediaSource(dataSource);
                break;
            case C.TYPE_HLS:
                mediaSource = new HlsMediaSource.Factory(getDataSourceFactoryCache(mAppContext, cacheEnable, preview, cacheDir)).createMediaSource(dataSource);
                break;
            case C.TYPE_OTHER:
            default:
                mediaSource = new ExtractorMediaSource.Factory(getDataSourceFactoryCache(mAppContext, cacheEnable, preview, cacheDir))
                        .setExtractorsFactory(new DefaultExtractorsFactory())
                        .createMediaSource(dataSource);
                break;
        }
        if (isLooping) {
            return new LoopingMediaSource(mediaSource);
        }
        return mediaSource;
    }

    /**
     * 本地缓存目录
     */
    public static synchronized Cache getCacheSingleInstance(Context context, File cacheDir) {
        String dirs = context.getCacheDir().getAbsolutePath();
        if (cacheDir != null) {
            dirs = cacheDir.getAbsolutePath();
        }
        if (mCache == null) {
            String path = dirs + File.separator + "exo";
            boolean isLocked = SimpleCache.isCacheFolderLocked(new File(path));
            if (!isLocked) {
                mCache = new SimpleCache(new File(path), new LeastRecentlyUsedCacheEvictor(DEFAULT_MAX_SIZE));
            }
        }
        return mCache;
    }

    /**
     * 根据缓存块判断是否缓存成功
     *
     * @param cache
     */
    private static boolean resolveCacheState(Cache cache, String url) {
        boolean isCache = true;
        if (!TextUtils.isEmpty(url)) {
            String key = CacheUtil.generateKey(Uri.parse(url));
            if (!TextUtils.isEmpty(key)) {
                NavigableSet<CacheSpan> cachedSpans = cache.getCachedSpans(key);
                if (cachedSpans.size() == 0) {
                    isCache = false;
                } else {
                    long contentLength = cache.getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, C.LENGTH_UNSET);
                    long currentLength = 0;
                    for (CacheSpan cachedSpan : cachedSpans) {
                        currentLength += cache.getCachedLength(key, cachedSpan.position, cachedSpan.length);
                    }
                    isCache = currentLength >= contentLength;
                }
            } else {
                isCache = false;
            }
        }
        return isCache;
    }

    /**
     * 获取SourceFactory，是否带Cache
     */
    private com.google.android.exoplayer2.upstream.DataSource.Factory getDataSourceFactoryCache(Context context, boolean cacheEnable, boolean preview, File cacheDir) {
        if (cacheEnable) {
            Cache cache = getCacheSingleInstance(context, cacheDir);
            if (cache != null) {
                isCached = resolveCacheState(cache, mSourceUrl);
                return new CacheDataSourceFactory(cache, getDataSourceFactory(context, preview), CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
            }
        }
        return getDataSourceFactory(context, preview);
    }

    /**
     * 获取SourceFactory
     */
    private com.google.android.exoplayer2.upstream.DataSource.Factory getDataSourceFactory(Context context, boolean preview) {

        return new DefaultDataSourceFactory(context, preview ? null : new DefaultBandwidthMeter(),
                getHttpDataSourceFactory(context, preview));
    }

    private com.google.android.exoplayer2.upstream.DataSource.Factory getHttpDataSourceFactory(Context context, boolean preview) {
        boolean allowCrossProtocolRedirects = false;
        if (heads != null && heads.size() > 0) {
            allowCrossProtocolRedirects = "true".equals(heads.get("allowCrossProtocolRedirects"));
        }
        if (mSkipSSLChain) {
            GSYExoHttpDataSourceFactory dataSourceFactory = new GSYExoHttpDataSourceFactory(Util.getUserAgent(context,
                    TAG), preview ? null : mBandwidthMeter, GSYExoHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    GSYExoHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects);
            if (heads != null && heads.size() > 0) {
                for (Map.Entry<String, String> header : heads.entrySet()) {
                    dataSourceFactory.getDefaultRequestProperties().set(header.getKey(), header.getValue());
                }
            }
            return dataSourceFactory;
        }
        DefaultHttpDataSourceFactory dataSourceFactory = new DefaultHttpDataSourceFactory(Util.getUserAgent(context,
                TAG), preview ? null : mBandwidthMeter, GSYExoHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                GSYExoHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects);
        if (heads != null && heads.size() > 0) {
            for (Map.Entry<String, String> header : heads.entrySet()) {
                dataSourceFactory.getDefaultRequestProperties().set(header.getKey(), header.getValue());
            }
        }
        return dataSourceFactory;
    }

    public void release() {
        isCached = false;
        if (mCache != null) {
            try {
                mCache.release();
                mCache = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Cache需要release之后才能clear
     *
     * @param context
     * @param cacheDir
     * @param url
     */
    public static void clearCache(Context context, File cacheDir, String url) {
        try {
            Cache cache = getCacheSingleInstance(context, cacheDir);
            if (!TextUtils.isEmpty(url)) {
                if (cache != null) {
                    CacheUtil.remove(cache, CacheUtil.generateKey(Uri.parse(url)));
                }
            } else {
                if (cache != null) {
                    for (String key : cache.getKeys()) {
                        CacheUtil.remove(cache, key);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setDisplay(SurfaceHolder surfaceHolder) {
        mInternalPlayer.setVideoSurfaceHolder(surfaceHolder);
        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_SURFACE_HOLDER_UPDATE, null);
    }

    @Override
    public void setSurface(Surface surface) {
        mInternalPlayer.setVideoSurface(surface);
        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_SURFACE_UPDATE, null);
    }

    @Override
    public void setVolume(float left, float right) {
        mInternalPlayer.setVolume(left);
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters parameters = new PlaybackParameters(speed, 1f);
        mInternalPlayer.setPlaybackParameters(parameters);
    }

    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null) {
            return false;
        }
        int state = mInternalPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mInternalPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public int getCurrentPosition() {
        return (int) mInternalPlayer.getCurrentPosition();
    }

    @Override
    public int getDuration() {
        return (int) mInternalPlayer.getDuration();
    }

    @Override
    public int getAudioSessionId() {
        return mInternalPlayer.getAudioSessionId();
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight;
    }

    @Override
    public void start() {
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void start(int msc) {
        mStartPos = msc;
        start();
    }

    @Override
    public void pause() {
        int state = getState();
        if (isInPlaybackState()
                && state != STATE_END
                && state != STATE_ERROR
                && state != STATE_IDLE
                && state != STATE_INITIALIZED
                && state != STATE_PAUSED
                && state != STATE_STOPPED) {
            mInternalPlayer.setPlayWhenReady(false);
        }
    }

    @Override
    public void resume() {
        if (isInPlaybackState() && getState() == STATE_PAUSED) {
            mInternalPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    public void seekTo(int msc) {
        if (isInPlaybackState()) {
            isPendingSeek = true;
        }
        mInternalPlayer.seekTo(msc);
        Bundle bundle = BundlePool.obtain();
        bundle.putInt(EventKey.INT_DATA, msc);
        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_SEEK_TO, bundle);
    }

    @Override
    public void stop() {
        isPreparing = true;
        isBuffering = false;
        updateStatus(IPlayer.STATE_STOPPED);
        mInternalPlayer.stop();
    }

    @Override
    public void reset() {
        stop();
    }

    @Override
    public void destroy() {
        isPreparing = true;
        isBuffering = false;
        updateStatus(IPlayer.STATE_END);
        mInternalPlayer.removeListener(mEventListener);
        mInternalPlayer.removeVideoListener(mVideoListener);
        mInternalPlayer.release();
    }

    private boolean isInPlaybackState() {
        int state = getState();
        return state != STATE_END
                && state != STATE_ERROR
                && state != STATE_INITIALIZED
                && state != STATE_STOPPED;
    }

    private VideoListener mVideoListener = new VideoListener() {
        @Override
        public void onVideoSizeChanged(int width, int height,
                                       int unappliedRotationDegrees, float pixelWidthHeightRatio) {
            mVideoWidth = width;
            mVideoHeight = height;
            Bundle bundle = BundlePool.obtain();
            bundle.putInt(EventKey.INT_ARG1, mVideoWidth);
            bundle.putInt(EventKey.INT_ARG2, mVideoHeight);
            bundle.putInt(EventKey.INT_ARG3, 0);
            bundle.putInt(EventKey.INT_ARG4, 0);
            submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_VIDEO_SIZE_CHANGE, bundle);
        }

        @Override
        public void onRenderedFirstFrame() {
            PLog.d(TAG, "onRenderedFirstFrame");
            updateStatus(IPlayer.STATE_STARTED);
            submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_VIDEO_RENDER_START, null);
        }
    };

    private Player.EventListener mEventListener = new Player.EventListener() {

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            int bufferPercentage = mInternalPlayer.getBufferedPercentage();
            if (!isLoading) {
                submitBufferingUpdate(bufferPercentage, null);
            }
            PLog.d(TAG, "onLoadingChanged : " + isLoading + ", bufferPercentage = " + bufferPercentage);
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            PLog.d(TAG, "onPlayerStateChanged : playWhenReady = " + playWhenReady
                    + ", playbackState = " + playbackState);

            if (!isPreparing) {
                if (playWhenReady) {
                    updateStatus(IPlayer.STATE_STARTED);
                    submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_RESUME, null);
                } else {
                    updateStatus(IPlayer.STATE_PAUSED);
                    submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_PAUSE, null);
                }
            }

            if (isPreparing) {
                switch (playbackState) {
                    case Player.STATE_READY:
                        isPreparing = false;
                        Format format = mInternalPlayer.getVideoFormat();
                        Bundle bundle = BundlePool.obtain();
                        if (format != null) {
                            bundle.putInt(EventKey.INT_ARG1, format.width);
                            bundle.putInt(EventKey.INT_ARG2, format.height);
                        }
                        updateStatus(IPlayer.STATE_PREPARED);
                        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_PREPARED, bundle);

                        if (playWhenReady) {
                            updateStatus(STATE_STARTED);
                            submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_START, null);
                        }

                        if (mStartPos > 0) {
                            mInternalPlayer.seekTo(mStartPos);
                            mStartPos = -1;
                        }
                        break;
                }
            }

            if (isBuffering) {
                switch (playbackState) {
                    case Player.STATE_READY:
                    case Player.STATE_ENDED:
                        long bitrateEstimate = mBandwidthMeter.getBitrateEstimate();
                        PLog.d(TAG, "buffer_end, BandWidth : " + bitrateEstimate);
                        isBuffering = false;
                        Bundle bundle = BundlePool.obtain();
                        bundle.putLong(EventKey.LONG_DATA, bitrateEstimate);
                        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_BUFFERING_END, bundle);
                        break;
                }
            }

            if (isPendingSeek) {
                switch (playbackState) {
                    case Player.STATE_READY:
                        isPendingSeek = false;
                        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_SEEK_COMPLETE, null);
                        break;
                }
            }

            if (!isPreparing) {
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        long bitrateEstimate = mBandwidthMeter.getBitrateEstimate();
                        PLog.d(TAG, "buffer_start, BandWidth : " + bitrateEstimate);
                        isBuffering = true;
                        Bundle bundle = BundlePool.obtain();
                        bundle.putLong(EventKey.LONG_DATA, bitrateEstimate);
                        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_BUFFERING_START, bundle);
                        break;
                    case Player.STATE_ENDED:
                        updateStatus(IPlayer.STATE_PLAYBACK_COMPLETE);
                        submitPlayerEvent(OnPlayerEventListener.PLAYER_EVENT_ON_PLAY_COMPLETE, null);
                        break;
                }
            }

        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            if (error == null) {
                submitErrorEvent(OnErrorEventListener.ERROR_EVENT_UNKNOWN, null);
                return;
            }
            PLog.e(TAG, error.getMessage() == null ? "" : error.getMessage());
            int type = error.type;
            switch (type) {
                case ExoPlaybackException.TYPE_SOURCE:
                    submitErrorEvent(OnErrorEventListener.ERROR_EVENT_IO, null);
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    submitErrorEvent(OnErrorEventListener.ERROR_EVENT_COMMON, null);
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    submitErrorEvent(OnErrorEventListener.ERROR_EVENT_UNKNOWN, null);
                    break;
            }
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            PLog.d(TAG, "onPlaybackParametersChanged : " + playbackParameters.toString());
        }
    };

}
