package com.kk.taurus.avplayer.cover;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.TextView;

import com.kk.taurus.avplayer.R;
import com.kk.taurus.playerbase.event.EventKey;
import com.kk.taurus.playerbase.event.OnPlayerEventListener;
import com.kk.taurus.playerbase.player.IPlayer;
import com.kk.taurus.playerbase.receiver.BaseCover;
import com.kk.taurus.playerbase.receiver.PlayerStateGetter;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Taurus on 2018/4/15.
 */

public class LoadingCover extends BaseCover {
    TextView loadingText;

    private Handler handler = new Handler() {


        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:

                    //得到网速
                    long netSpeed = getNetSpeed(getContext());
                    setLoadingBitrate(netSpeed);
                    removeMessages(0);
                    handler.sendEmptyMessageDelayed(0, 2000);

                    break;
            }
        }
    };
    private long lastTotalRxBytes;
    private long lastTimeStamp;

    public LoadingCover(Context context) {
        super(context);
        loadingText = findViewById(R.id.cover_player_loading_text);
        lastTimeStamp = System.currentTimeMillis();
        handler.sendEmptyMessage(0);
    }

    @Override
    protected void onCoverAttachedToWindow() {
        super.onCoverAttachedToWindow();
        PlayerStateGetter playerStateGetter = getPlayerStateGetter();
        if (playerStateGetter != null && isInPlaybackState(playerStateGetter)) {
            setLoadingState(playerStateGetter.isBuffering());
        }
    }

    private boolean isInPlaybackState(PlayerStateGetter playerStateGetter) {
        int state = playerStateGetter.getState();
        return state != IPlayer.STATE_END
                && state != IPlayer.STATE_ERROR
                && state != IPlayer.STATE_IDLE
                && state != IPlayer.STATE_INITIALIZED
                && state != IPlayer.STATE_STOPPED;
    }

    @Override
    public void onPlayerEvent(int eventCode, Bundle bundle) {
        switch (eventCode) {
            case OnPlayerEventListener.PLAYER_EVENT_ON_BUFFERING_START:
            case OnPlayerEventListener.PLAYER_EVENT_ON_DATA_SOURCE_SET:
            case OnPlayerEventListener.PLAYER_EVENT_ON_PROVIDER_DATA_START:
            case OnPlayerEventListener.PLAYER_EVENT_ON_SEEK_TO:
                setLoadingState(true);
//                if (bundle != null) {
//                    long bitrateEstimate = bundle.getLong(EventKey.LONG_DATA);
//                    setLoadingBitrate(bitrateEstimate);
//                }
                break;

            case OnPlayerEventListener.PLAYER_EVENT_ON_VIDEO_RENDER_START:
            case OnPlayerEventListener.PLAYER_EVENT_ON_BUFFERING_END:
            case OnPlayerEventListener.PLAYER_EVENT_ON_STOP:
            case OnPlayerEventListener.PLAYER_EVENT_ON_PROVIDER_DATA_ERROR:
            case OnPlayerEventListener.PLAYER_EVENT_ON_SEEK_COMPLETE:
                setLoadingState(false);
                break;
        }
    }

    private void setLoadingBitrate(long bitrateEstimate) {
//        loadingText.setText("Loading("+String.format("%.2f", bitrateEstimate / 1024.00 / 1024.00) + "Mb/s)" );
        loadingText.setText("Loading(" + bitrateEstimate + "Kb/s)");
    }

    @Override
    public void onErrorEvent(int eventCode, Bundle bundle) {
        setLoadingState(false);
    }

    @Override
    public void onReceiverEvent(int eventCode, Bundle bundle) {

    }

    private void setLoadingState(boolean show) {
        setCoverVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public View onCreateCoverView(Context context) {
        return View.inflate(context, R.layout.layout_loading_cover, null);
    }

    @Override
    public int getCoverLevel() {
        return levelMedium(1);
    }


    /**
     * 得到网络速度
     * 每隔两秒调用一次
     *
     * @param context
     * @return
     */
    public long getNetSpeed(Context context) {
        long netSpeed;
        long nowTotalRxBytes = TrafficStats.getUidRxBytes(context.getApplicationInfo().uid) == TrafficStats.UNSUPPORTED ? 0
                : (TrafficStats.getTotalRxBytes() / 1024);//转为KB;
        long nowTimeStamp = System.currentTimeMillis();
        long speed = ((nowTotalRxBytes - lastTotalRxBytes) * 1000 / (nowTimeStamp - lastTimeStamp));//毫秒转换

        lastTimeStamp = nowTimeStamp;
        lastTotalRxBytes = nowTotalRxBytes;
        netSpeed = speed;
        return netSpeed;
    }

    @Override
    public void onReceiverBind() {
        super.onReceiverBind();
    }

    @Override
    public void onReceiverUnBind() {
        //需要在activity调用 removeReceiver方法生效
        handler.removeCallbacksAndMessages(null);
        super.onReceiverUnBind();
    }
}
