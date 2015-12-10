package com.youhuan.exovideoplayer;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.util.PlayerControl;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by youhuan on 15/12/10.
 */
public class VideoPlayer extends FrameLayout
        implements
        View.OnClickListener,
        SurfaceHolder.Callback,
        ExoPlayer.Listener,
        MediaCodecVideoTrackRenderer.EventListener {
    private final static String TAG = "VideoPlayer";
    private final int MSG_HIDE_CONTROLLER = 10;
    private final int MSG_UPDATE_PLAY_TIME = 11;
    private final int MSG_PLAY_ON_TV_RESULT = 12;
    private final int MSG_EXIT_FORM_TV_RESULT = 13;
    private MediaController.PageType mCurrPageType = MediaController.PageType.SHRINK;//当前是横屏还是竖屏

    private Context mContext;
    private ExoPlayer mExoPlayer;
    private SurfaceView mSurfaceView;
    private MediaController mMediaController;
    private FrameLayout mLayoutProgressbar;
    private Timer mUpdateTimer;
    private VideoPlayCallbackImpl mVideoPlayCallback;

    private View mProgressBarView;

    private ArrayList<Video> mAllVideo;
    private Video mNowPlayVideo;

    //是否自动隐藏控制栏
    private boolean mAutoHideController = true;


    private MediaCodecVideoTrackRenderer mVideoRenderer;
    private MediaCodecAudioTrackRenderer mAudioRenderer;
    private long mPosition = 0;//记录播放位置
    private Boolean mIsPlayWhenReady = true;//是否在缓冲好时自动播放
    private Boolean mHasRenderToSurface = false;//是否已经渲染到surface
    private PlayerControl mPlayerControl;

    // http://v.yilos.com/973f5643fe7fe566d00c8b447bc75e65.mp4
    // http://v.yilos.com/826fd77a9baefd7907d4e04f4d20ab36.mp4
    private String mUrl = "http://v.yilos.com/826fd77a9baefd7907d4e04f4d20ab36.mp4";

    private static final CookieManager defaultCookieManager;

    static {
        defaultCookieManager = new CookieManager();
        defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    }


    public VideoPlayer(Context context) {
        super(context);
        initView(context);
    }

    public VideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(context);
    }


    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_PLAY_TIME) {
                updatePlayTime();
                updatePlayProgress();
            } else if (msg.what == MSG_HIDE_CONTROLLER) {
                showOrHideController();
            }
            return false;
        }
    });


    private OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {

        }
    };

    private OnTouchListener mOnTouchVideoListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                showOrHideController();
            }
            return mCurrPageType == MediaController.PageType.EXPAND;
        }
    };

    private MediaController.MediaControlImpl mMediaControl = new MediaController.MediaControlImpl() {
        @Override
        public void alwaysShowController() {
            VideoPlayer.this.alwaysShowController();
        }

        @Override
        public void onPlayTurn() {
            if (mPlayerControl.isPlaying()) {
                pausePlay(true);
            } else {
                goOnPlay();
            }
        }

        @Override
        public void onPageTurn() {
            if (null == mVideoPlayCallback) {
                return;
            }
            mVideoPlayCallback.onSwitchPageType();
        }

        @Override
        public void onProgressTurn(MediaController.ProgressState state, int progress) {
            if (state.equals(MediaController.ProgressState.START)) {
                mHandler.removeMessages(MSG_HIDE_CONTROLLER);
            } else if (state.equals(MediaController.ProgressState.STOP)) {
                resetHideTimer();
            } else {
                int time = progress * mPlayerControl.getDuration() / 100;
                mPlayerControl.seekTo(time);
                updatePlayTime();
            }
        }
    };

    private MediaPlayer.OnPreparedListener mOnPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mediaPlayer) {
            // 两秒钟之后如果loading还显示的话，则将其隐藏
            // 解决神奇的三星 note2不回调onInfoListener
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mProgressBarView.getVisibility() == View.VISIBLE) {
                        mProgressBarView.setVisibility(View.GONE);
                    }
                }
            }, 2000);
            mediaPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mp, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START || what == 861) {
                        mProgressBarView.setVisibility(View.GONE);
                        return true;
                    }
                    return false;
                }
            });
        }
    };

    private MediaPlayer.OnCompletionListener mOnCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mediaPlayer) {
            stopUpdateTimer();
            stopHideTimer(true);
            mMediaController.playFinish(mPlayerControl.getDuration());
            if (null == mVideoPlayCallback) {
                return;
            }
            mVideoPlayCallback.onPlayFinish();
//            Toast.makeText(mContext, "视频播放完成", Toast.LENGTH_SHORT).show();
        }
    };

    public void setVideoPlayCallback(VideoPlayCallbackImpl videoPlayCallback) {
        mVideoPlayCallback = videoPlayCallback;
    }

    /**
     * 如果在地图页播放视频，请先调用该接口
     */
    @SuppressWarnings("unused")
    public void setSupportPlayOnSurfaceView() {
        mSurfaceView.setZOrderMediaOverlay(true);
    }

    public void setPageType(MediaController.PageType pageType) {
        mMediaController.setPageType(pageType);
        mCurrPageType = pageType;
    }

    /***
     * 强制横屏模式
     */
    @SuppressWarnings("unused")
    public void forceLandscapeMode() {
        mMediaController.forceLandscapeMode();
    }

    /***
     * 播放本地视频 只支持横屏播放
     *
     * @param fileUrl fileUrl
     */
    @SuppressWarnings("unused")
    public void loadLocalVideo(String fileUrl) {
        VideoUrl videoUrl = new VideoUrl();
        videoUrl.setIsOnlineVideo(false);
        videoUrl.setFormatUrl(fileUrl);
        videoUrl.setFormatName("本地视频");
        Video video = new Video();
        ArrayList<VideoUrl> videoUrls = new ArrayList<>();
        videoUrls.add(videoUrl);
        video.setVideoUrl(videoUrls);
        video.setPlayUrl(0);

        mNowPlayVideo = video;

        /***
         * 初始化控制条的精简模式
         */
        mMediaController.initTrimmedMode();
        loadAndPlay(mNowPlayVideo.getPlayUrl(), 0);
    }

    /**
     * 播放多个视频,默认播放第一个视频，第一个格式
     *
     * @param allVideo 所有视频
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo) {
        loadMultipleVideo(allVideo, 0, 0);
    }

    /**
     * 播放多个视频
     *
     * @param allVideo     所有的视频
     * @param selectVideo  指定的视频
     * @param selectFormat 指定的格式
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo, int selectVideo, int selectFormat) {
        loadMultipleVideo(allVideo, selectVideo, selectFormat, 0);
    }

    /***
     * @param allVideo     所有的视频
     * @param selectVideo  指定的视频
     * @param selectFormat 指定的格式
     * @param seekTime     开始进度
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo, int selectVideo, int selectFormat, int seekTime) {
        if (null == allVideo || allVideo.size() == 0) {
//            Toast.makeText(mContext, "视频列表为空", Toast.LENGTH_SHORT).show();
            return;
        }
        mAllVideo.clear();
        mAllVideo.addAll(allVideo);
        mNowPlayVideo = mAllVideo.get(selectVideo);
        mNowPlayVideo.setPlayUrl(selectFormat);
        mMediaController.initVideoList(mAllVideo);
        mMediaController.initPlayVideo(mNowPlayVideo);
        loadAndPlay(mNowPlayVideo.getPlayUrl(), seekTime);
    }

    /**
     * 暂停播放
     *
     * @param isShowController 是否显示控制条
     */
    public void pausePlay(boolean isShowController) {
        mPlayerControl.pause();
        mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        stopHideTimer(isShowController);
    }

    /***
     * 继续播放
     */
    public void goOnPlay() {
        mPlayerControl.start();
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
        resetHideTimer();
        resetUpdateTimer();
    }

    /**
     * 关闭视频
     */
    public void close() {
        mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        stopHideTimer(true);
        stopUpdateTimer();
        mPlayerControl.pause();
        mSurfaceView.setVisibility(GONE);
    }


    public ArrayList<Video> getAllVideos() {
        return mAllVideo;
    }

    public boolean isAutoHideController() {
        return mAutoHideController;
    }

    public void setAutoHideController(boolean autoHideController) {
        mAutoHideController = autoHideController;
    }


    private void initView(Context context) {
        mContext = context;
        View.inflate(context, R.layout.video_player_layout, this);
        mSurfaceView = (SurfaceView) findViewById(R.id.video_view);
        mMediaController = (MediaController) findViewById(R.id.controller);
        mProgressBarView = findViewById(R.id.progressbar);

        mMediaController.setMediaControl(mMediaControl);
        mSurfaceView.setOnTouchListener(mOnTouchVideoListener);

        showProgressView(false);

        mProgressBarView.setOnClickListener(mOnClickListener);
        mAllVideo = new ArrayList<>();

        CookieHandler currentHandler = CookieHandler.getDefault();
        if (currentHandler != defaultCookieManager) {
            CookieHandler.setDefault(defaultCookieManager);
        }


    }


    /**
     * 创建ExoPlayer
     */
    private void preparePlayer() {
        if (mUrl == null || mUrl.isEmpty()) {
            Log.e(TAG, "视频url地址为空");
            return;
        }

        if (mExoPlayer == null) {
            mExoPlayer = ExoPlayer.Factory.newInstance(2, 1000, 5000);
            mExoPlayer.addListener(this);
            mExoPlayer.seekTo(mPosition);
            mPlayerControl = new PlayerControl(mExoPlayer);
//            mMediaController.setMediaPlayer(mPlayerControl);
            mMediaController.setEnabled(true);
        }
        buildRenders();
    }

    /**
     * 创建videoRender和audioRender
     */
    private void buildRenders() {
        FrameworkSampleSource sampleSource = new FrameworkSampleSource(mContext, Uri.parse(mUrl), null);
        mVideoRenderer = new MediaCodecVideoTrackRenderer(mContext, sampleSource,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        mAudioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

        mExoPlayer.prepare(mVideoRenderer, mAudioRenderer);
        // 调用了该方法后，视频播放是没有声音
//        mExoPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, 0f);
    }


    /**
     * 尝试播放视频
     *
     * @param isPlayWhenReady
     */
    private void play(Boolean isPlayWhenReady) {
        Surface surface = mSurfaceView.getHolder().getSurface();
        if (surface == null || !surface.isValid()) {
            Log.d(TAG, "surface not ready");
            return;
        }
        mHasRenderToSurface = false;
        mExoPlayer.sendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        mExoPlayer.setPlayWhenReady(isPlayWhenReady);
    }

    /**
     * 重新播放
     */
    private void showReplay() {
        releasePlayer();
        mPosition = 0;
    }

    /**
     * 释放player
     */
    private void releasePlayer() {
        mHasRenderToSurface = false;
        if (mExoPlayer != null) {
            mPosition = mExoPlayer.getCurrentPosition();
            mExoPlayer.release();
            mExoPlayer = null;
            mPlayerControl = null;
        }
        mVideoRenderer = null;
    }


    /**
     * 更换清晰度地址时，续播
     */
    private void playVideoAtLastPos() {
        int playTime = mPlayerControl.getCurrentPosition();
//        mSurfaceView.stopPlayback();
        loadAndPlay(mNowPlayVideo.getPlayUrl(), playTime);
    }

    public void playLastVideoAtPos(int playTime) {
        if (null == mNowPlayVideo) {
            return;
        }
//        mSurfaceView.stopPlayback();
        loadAndPlay(mNowPlayVideo.getPlayUrl(), playTime);
    }


    /**
     * 加载并开始播放视频
     *
     * @param videoUrl videoUrl
     */
    private void loadAndPlay(VideoUrl videoUrl, int seekTime) {
        showProgressView(seekTime > 0);
        if (TextUtils.isEmpty(videoUrl.getFormatUrl())) {
            Log.e("TAG", "videoUrl should not be null");
            return;
        }
        mSurfaceView.setOnPreparedListener(mOnPreparedListener);
        if (videoUrl.isOnlineVideo()) {
            mSurfaceView.setVideoPath(videoUrl.getFormatUrl());
        } else {
            Uri uri = Uri.parse(videoUrl.getFormatUrl());
            mSurfaceView.setVideoURI(uri);
        }
        mSurfaceView.setVisibility(VISIBLE);
        startPlayVideo(seekTime);
    }

    /**
     * 播放视频
     * should called after setVideoPath()
     */
    private void startPlayVideo(int seekTime) {
        if (null == mUpdateTimer) resetUpdateTimer();
        resetHideTimer();
        mSurfaceView.setOnCompletionListener(mOnCompletionListener);
        mPlayerControl.start();
        if (seekTime > 0) {
            mPlayerControl.seekTo(seekTime);
        }
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
    }

    /**
     * 更新播放的进度时间
     */
    private void updatePlayTime() {
        int allTime = mPlayerControl.getDuration();
        int playTime = mPlayerControl.getCurrentPosition();
        mMediaController.setPlayProgressTxt(playTime, allTime);
    }

    /**
     * 更新播放进度条
     */
    private void updatePlayProgress() {
        int allTime = mPlayerControl.getDuration();
        int playTime = mPlayerControl.getCurrentPosition();
        int loadProgress = mPlayerControl.getBufferPercentage();
        int progress = playTime * 100 / allTime;
        mMediaController.setProgressBar(progress, loadProgress);
    }

    /**
     * 显示loading圈
     *
     * @param isTransparentBg isTransparentBg
     */
    private void showProgressView(Boolean isTransparentBg) {
        mProgressBarView.setVisibility(VISIBLE);
        if (!isTransparentBg) {
            mProgressBarView.setBackgroundResource(android.R.color.black);
        } else {
            mProgressBarView.setBackgroundResource(android.R.color.transparent);
        }
    }


    public void showOrHideProgressView(boolean showFlag) {
        mProgressBarView.setVisibility(showFlag ? VISIBLE : GONE);
    }

    public void hideController() {
        Animation animation = AnimationUtils.loadAnimation(mContext,
                R.anim.anim_exit_from_bottom);
        animation.setAnimationListener(new AnimationImp() {
            @Override
            public void onAnimationEnd(Animation animation) {
                super.onAnimationEnd(animation);
                mMediaController.setVisibility(View.GONE);
            }
        });
        mMediaController.startAnimation(animation);
    }

    /***
     *
     */
    private void showOrHideController() {
        if (mMediaController.getVisibility() == View.VISIBLE) {
            Animation animation = AnimationUtils.loadAnimation(mContext,
                    R.anim.anim_exit_from_bottom);
            animation.setAnimationListener(new AnimationImp() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    super.onAnimationEnd(animation);
                    mMediaController.setVisibility(View.GONE);
                }
            });
            mMediaController.startAnimation(animation);
        } else {
            mMediaController.setVisibility(View.VISIBLE);
            mMediaController.clearAnimation();
            Animation animation = AnimationUtils.loadAnimation(mContext,
                    R.anim.anim_enter_from_bottom);
            mMediaController.startAnimation(animation);
            resetHideTimer();
        }
    }

    private void alwaysShowController() {
        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        mMediaController.setVisibility(View.VISIBLE);
    }

    private void resetHideTimer() {
        if (!isAutoHideController()) return;
        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        int TIME_SHOW_CONTROLLER = 4000;
        mHandler.sendEmptyMessageDelayed(MSG_HIDE_CONTROLLER, TIME_SHOW_CONTROLLER);
    }

    private void stopHideTimer(boolean isShowController) {
        mHandler.removeMessages(MSG_HIDE_CONTROLLER);
        mMediaController.clearAnimation();
        mMediaController.setVisibility(isShowController ? View.VISIBLE : View.GONE);
    }

    private void resetUpdateTimer() {
        mUpdateTimer = new Timer();
        int TIME_UPDATE_PLAY_TIME = 1000;
        mUpdateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage(MSG_UPDATE_PLAY_TIME);
            }
        }, 0, TIME_UPDATE_PLAY_TIME);
    }

    private void stopUpdateTimer() {
        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
            mUpdateTimer = null;
        }
    }


    // ExoPlayer.Listener implementation

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        // Do nothing.
        Log.d(TAG, "onPlayerStateChanged, " + playWhenReady + ", " + playbackState);

        mIsPlayWhenReady = playWhenReady;
        switch (playbackState) {
            case ExoPlayer.STATE_ENDED:
                mExoPlayer.seekTo(0);
                showReplay();
                break;
            case ExoPlayer.STATE_READY:
                if (!mIsPlayWhenReady) {
                    progressbar.setVisibility(View.INVISIBLE);
                    mMediaController.show();
                }
                if (mIsPlayWhenReady && mHasRenderToSurface) {
                    progressbar.setVisibility(View.INVISIBLE);
                }
                break;
            default:
                mLoadingIcon.setVisibility(View.VISIBLE);
                break;
        }
    }

    @Override
    public void onPlayWhenReadyCommitted() {
        Log.d(TAG, "onPlayWhenReadyCommitted");
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
        Log.d(TAG, "onPlayerError" + e.getMessage());
        showReplay();
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        Log.d(TAG, "onDroppedFrames");
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.d(TAG, "onVideoSizeChanged");
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        Log.d(TAG, "onDrawnToSurface");
        mHasRenderToSurface = true;
        mExoPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME, 1f);
    }


    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
        Log.d(TAG, "onDecoderInitializationError " + e.getMessage());
        showReplay();
    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {
        Log.d(TAG, "onCryptoError" + e.getMessage());
        showReplay();
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
        Log.d(TAG, "onDecoderInitialized");

    }

    // SurfaceHolder.Callback implementation

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
        mHasRenderToSurface = true;
        play(mIsPlayWhenReady);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed");
        mHasRenderToSurface = false;
    }

    @Override
    public void onClick(View v) {
        if (v == mSurfaceView) {
            showOrHideController();
        }
    }


    private class AnimationImp implements Animation.AnimationListener {

        @Override
        public void onAnimationEnd(Animation animation) {

        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    public interface VideoPlayCallbackImpl {
        void onCloseVideo();

        void onSwitchPageType();

        void onPlayFinish();
    }
}
