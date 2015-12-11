package com.youhuan.exovideoplayer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;

/**
 * Created by youhuan on 15/12/11.
 */
public class VideoActivity extends Activity {
    public static final double VIDEO_ASPECT_RATIO = 1.78d;

    private String mTitle = "第2期：艾蕾嫚特殊眼型及款式搭配";
    private String mUrl = "http://v.yilos.com/2440562ea8bc0930a54045be0a67a6e7.mp4";

    private int widthPixels;
    private int heightPixels;
    private float density;

    private ViewGroup mDecorView;
    private FrameLayout mVideoPlayerLayout;
    private VideoPlayer mVideoPlayer;

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


        mVideoPlayer.setPageType(MediaController.PageType.SHRINK);
        mVideoPlayer.setSupportPlayOnSurfaceView();
        mVideoPlayer.setVideoPlayCallback(new VideoPlayer.VideoPlayCallbackImpl() {
            @Override
            public void onCloseVideo() {
                mVideoPlayer.close();
                //initVideoPlayerIcon();
                if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    mVideoPlayer.setPageType(MediaController.PageType.SHRINK);
                }
            }

            @Override
            public void onSwitchPageType() {
                if (getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    mVideoPlayer.setPageType(MediaController.PageType.SHRINK);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    mVideoPlayer.setPageType(MediaController.PageType.EXPAND);
                }
            }

            @Override
            public void onPlayFinish() {
//                mLayoutVideoPlayerIcon.setBackgroundDrawable(new BitmapDrawable(mVideoThumbnail));
//                initVideoPlayerIcon();
            }
        });

        ArrayList<Video> videos = new ArrayList<Video>();
        Video video = new Video();
        video.setVideoName(mTitle);
        video.setVideoUri(mUrl);
        videos.add(video);

        mVideoPlayer.loadMultipleVideo(videos);
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
            mVideoPlayerLayout.addView(mVideoPlayer);
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
