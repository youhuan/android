package com.youhuan.exovideoplayer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.ArrayList;

/**
 * Created by youhuan on 15/12/11.
 */
public class VideoActivity extends Activity {
    private String mTitle = "第2期：艾蕾嫚特殊眼型及款式搭配";
    private String mUrl = "http://v.yilos.com/2440562ea8bc0930a54045be0a67a6e7.mp4";

    private ViewGroup mDecorView;
    private FrameLayout mVideoPlayerLayout;
    private VideoPlayer mVideoPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        mDecorView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        mVideoPlayer = (VideoPlayer) findViewById(R.id.video_player);
        mVideoPlayerLayout = (FrameLayout) findViewById(R.id.layout_video_player);
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
            final WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().setAttributes(attrs);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            mDecorView.removeView(mVideoPlayer);
            mVideoPlayerLayout.removeView(mVideoPlayer);
            mVideoPlayerLayout.addView(mVideoPlayer);
            mVideoPlayer.goOnPlay();
        }
    }
}
