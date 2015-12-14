package com.youhuan.exovideoplayer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by youhuan on 15/12/11.
 */
public class VideoActivity extends Activity {
    private static final String TAG = "VideoPlayer";
    public static final double VIDEO_ASPECT_RATIO = 1.78d;

    private String mTitle = "第2期：艾蕾嫚特殊眼型及款式搭配";
    private String mUrl = "http://v.yilos.com/2440562ea8bc0930a54045be0a67a6e7.mp4";

    private int widthPixels;
    private int heightPixels;
    private float density;

    private ViewGroup mDecorView;


    // 视频播放控件
    private FrameLayout mVideoPlayerLayout;
    private LinearLayout mLayoutVideoPlayerIcon;
    // 视频播放器
    private VideoPlayer mVideoPlayer;
    // 视频播放按钮
    private ImageView mIvVideoPlayIcon;
    // 非WIFI情况下，提示信息布局
    private RelativeLayout mLayoutVideoPlayNotWifi;
    // 非WIFI情况下，"继续播放"文字
    private TextView mTvVideoPlayNotWifi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        widthPixels = displayMetrics.widthPixels;
        heightPixels = displayMetrics.heightPixels;
        density = displayMetrics.density;
        mDecorView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        mVideoPlayer = (VideoPlayer) findViewById(R.id.video_player);
        mVideoPlayerLayout = (FrameLayout) findViewById(R.id.layout_video_player);
        mVideoPlayerLayout.getLayoutParams().height = (int) (widthPixels / VIDEO_ASPECT_RATIO);


        // 视频播放控件
        mLayoutVideoPlayerIcon = (LinearLayout) findViewById(R.id.layout_video_player_icon);
        // 根据视频宽高比例，重新计算视频播放器高度
        mLayoutVideoPlayerIcon.getLayoutParams().height = (int) (widthPixels / VIDEO_ASPECT_RATIO);
        mLayoutVideoPlayerIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 屏蔽视频播放器的点击事件
            }
        });

        mIvVideoPlayIcon = (ImageView) findViewById(R.id.iv_video_play_icon);
        // 视频播放
        mIvVideoPlayIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo();
            }
        });

        mLayoutVideoPlayNotWifi = (RelativeLayout) findViewById(R.id.layout_video_play_not_wifi);
        mTvVideoPlayNotWifi = (TextView) findViewById(R.id.tv_video_play_not_wifi);
        // 非WIFI环境下视频播放
        mTvVideoPlayNotWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playVideo();
            }
        });

        // 当前是非WIFI环境，并且允许在非WIFI环境下播放视频，需要提示用户
        // WIFI环境下显示播放按钮
        initVideoPlayerIcon();

        mLayoutVideoPlayNotWifi.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // 空实现，防止mLayoutVideoPlayNotWifi显示时点击到了底层的视频播放控件
            }
        });


        mVideoPlayer.setPageType(MediaController.PageType.SHRINK);
        mVideoPlayer.setSupportPlayOnSurfaceView();
        mVideoPlayer.setVideoPlayCallback(new VideoPlayer.VideoPlayCallbackImpl() {
            @Override
            public void onSwitchPageType() {
                if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {//竖屏播放
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    mVideoPlayer.setPageType(MediaController.PageType.SHRINK);
                } else {//横屏播放
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    mVideoPlayer.setPageType(MediaController.PageType.EXPAND);
                }
            }

            @Override
            public void onPlayFinish() {
                Log.d(TAG, "视频播放完成");
                initVideoPlayerIcon();
            }
        });
    }

    private void initVideoPlayerIcon() {
        mVideoPlayer.showOrHideProgressView(false);
        mVideoPlayer.hideController();
        if (!isNetworkConnected()) {
            Toast.makeText(this, "网络连接失败，请稍后重试", Toast.LENGTH_SHORT).show();
        }
        //WIFI环境下显示播放按钮
        if (isWifi()) {
            mLayoutVideoPlayerIcon.setVisibility(View.VISIBLE);
            mIvVideoPlayIcon.setVisibility(View.VISIBLE);
            mLayoutVideoPlayNotWifi.setVisibility(View.GONE);
        } else {//否则给用户选择提示
            mIvVideoPlayIcon.setVisibility(View.GONE);
            mLayoutVideoPlayerIcon.setVisibility(View.GONE);
            mLayoutVideoPlayNotWifi.setVisibility(View.VISIBLE);
        }
    }

    private void playVideo() {
        mLayoutVideoPlayerIcon.setVisibility(View.GONE);
        mLayoutVideoPlayNotWifi.setVisibility(View.GONE);
        mIvVideoPlayIcon.setVisibility(View.GONE);

        ArrayList<Video> videos = new ArrayList<Video>();
        Video video = new Video();
        video.setVideoName(mTitle);
        video.setVideoUri(mUrl);
        videos.add(video);

        mVideoPlayer.loadMultipleVideo(videos);
    }

    /**
     * 判断当前网络是否连通
     *
     * @return
     */
    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getApplication().getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();

        return info != null && info.isConnectedOrConnecting();
    }

    /**
     * 判断当前网络是否为wifi
     *
     * @return
     */
    private boolean isWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplication().getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }


    @Override
    protected void onPause() {
        super.onPause();
        mVideoPlayer.pausePlay(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVideoPlayer.goOnPlay();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mVideoPlayer) {
            mVideoPlayer.close();
            mVideoPlayer.releasePlayer();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (null == mVideoPlayer) {
            return;
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            mVideoPlayerLayout.removeView(mVideoPlayer);
            mDecorView.removeView(mVideoPlayer);
            mDecorView.addView(mVideoPlayer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            mVideoPlayer.goOnPlay();
        } else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().setAttributes(attrs);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            mDecorView.removeView(mVideoPlayer);
            mVideoPlayerLayout.removeView(mVideoPlayer);
            mVideoPlayerLayout.addView(mVideoPlayer, 0);
            mVideoPlayer.goOnPlay();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (-1 != mDecorView.indexOfChild(mVideoPlayer)) {
                // 如果是横屏状态，则切换为竖屏状态
                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    mVideoPlayer.setPageType(MediaController.PageType.EXPAND);
                }
                return false;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
