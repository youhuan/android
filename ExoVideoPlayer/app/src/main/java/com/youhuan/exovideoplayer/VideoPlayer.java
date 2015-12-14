package com.youhuan.exovideoplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.net.Uri;
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

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by youhuan on 15/12/10.
 * 基于ExoPlayer的视频播放控件
 */
public class VideoPlayer extends FrameLayout
        implements View.OnClickListener, SurfaceHolder.Callback, ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener, MediaCodecAudioTrackRenderer.EventListener {
    private final static String TAG = "VideoPlayer";
    private static final int BUFFER_SEGMENT_SIZE = 256 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    private final int MSG_HIDE_CONTROLLER = 10;
    private final int MSG_UPDATE_PLAY_TIME = 11;
    private MediaController.PageType mCurrPageType = MediaController.PageType.SHRINK;//当前是横屏还是竖屏

    private Context mContext;
    private ExoPlayer mExoPlayer;
    private SurfaceView mSurfaceView;
    private MediaController mMediaController;
    private FrameLayout mLayoutProgressbar;
    private Timer mUpdateTimer;
    private VideoPlayCallbackImpl mVideoPlayCallback;

    // 所有播放视频列表
    private ArrayList<Video> mAllVideo;
    // 当前播放的视频
    private Video mNowPlayVideo;

    //是否自动隐藏控制栏
    private boolean mAutoHideController = true;

    private MediaCodecVideoTrackRenderer mVideoRenderer;
    private MediaCodecAudioTrackRenderer mAudioRenderer;
    private long mPosition = 0;//记录播放位置
    private Boolean mIsPlayWhenReady = true;//是否在缓冲好时自动播放
    private Boolean mHasRenderToSurface = false;//是否已经渲染到surface
//    private PlayerControl mPlayerControl;

    private Handler mainHandler;


//    private static final CookieManager defaultCookieManager;
//
//    static {
//        defaultCookieManager = new CookieManager();
//        defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
//    }


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

//    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//    public VideoPlayer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
//        super(context, attrs, defStyleAttr, defStyleRes);
//        initView(context);
//    }


    private void initView(Context context) {
        mContext = context;
        View.inflate(context, R.layout.video_player_layout, this);
        mSurfaceView = (SurfaceView) findViewById(R.id.video_view);
        mMediaController = (MediaController) findViewById(R.id.controller);
        mLayoutProgressbar = (FrameLayout) findViewById(R.id.progressbar);

        mMediaController.setMediaControl(mMediaControl);
        mSurfaceView.setOnTouchListener(mOnTouchVideoListener);
        mSurfaceView.getHolder().addCallback(this);


        showProgressView(true);

        mLayoutProgressbar.setOnClickListener(mOnClickListener);
        mAllVideo = new ArrayList<>();

        mainHandler = new Handler();

//        CookieHandler currentHandler = CookieHandler.getDefault();
//        if (currentHandler != defaultCookieManager) {
//            CookieHandler.setDefault(defaultCookieManager);
//        }

        // 创建ExoPlayer
        mExoPlayer = ExoPlayer.Factory.newInstance(2, 1000, 5000);
        mExoPlayer.addListener(this);
        mMediaController.setEnabled(true);
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
        public void onPlayTurn() {
            if (mExoPlayer.getPlayWhenReady()) {
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
                long time = progress * mExoPlayer.getDuration() / 100;
                mExoPlayer.seekTo(time);
                updatePlayTime();
            }
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

    /**
     * 设置视频播放器横竖屏播放状态
     *
     * @param pageType
     */
    public void setPageType(MediaController.PageType pageType) {
        mMediaController.setPageType(pageType);
        mCurrPageType = pageType;
    }

    /**
     * 强制横屏模式
     */
    @SuppressWarnings("unused")
    public void forceLandscapeMode() {
        mMediaController.forceLandscapeMode();
    }

    /**
     * 播放本地视频 只支持横屏播放
     *
     * @param video
     */
    @SuppressWarnings("unused")
    public void loadLocalVideo(Video video) {
        mNowPlayVideo = video;
        //初始化控制条的精简模式
        mMediaController.initTrimmedMode();
        loadAndPlay(video.getVideoUri(), 0);
    }

    /**
     * 播放多个视频,默认播放第一个视频
     *
     * @param allVideo 所有视频
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo) {
        loadMultipleVideo(allVideo, 0);
    }

    /**
     * 播放多个视频
     *
     * @param allVideo    所有的视频
     * @param selectVideo 指定的视频
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo, int selectVideo) {
        loadMultipleVideo(allVideo, selectVideo, 0);
    }

    /***
     * @param allVideo    所有的视频
     * @param selectVideo 指定的视频
     * @param seekTime    开始进度
     */
    public void loadMultipleVideo(ArrayList<Video> allVideo, int selectVideo, int seekTime) {
        if (null == allVideo || allVideo.isEmpty()) {
            Log.e(TAG, "视频列表为空");
            return;
        }
        mAllVideo.clear();
        mAllVideo.addAll(allVideo);
        mNowPlayVideo = mAllVideo.get(selectVideo);
        loadAndPlay(mNowPlayVideo.getVideoUri(), seekTime);
    }

    /**
     * 暂停播放
     *
     * @param isShowController 是否显示控制条
     */
    public void pausePlay(boolean isShowController) {
        if (null == mExoPlayer) {
            return;
        }
        mExoPlayer.setPlayWhenReady(false);
        mPosition = mExoPlayer.getCurrentPosition();
        mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        stopHideTimer(isShowController);
    }

    /**
     * 继续播放
     */
    public void goOnPlay() {
        if (null == mExoPlayer) {
            return;
        }
        mExoPlayer.setPlayWhenReady(true);
        mExoPlayer.seekTo((int) mPosition);
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
        resetHideTimer();
        resetUpdateTimer();
    }

    /**
     * 获取播放视频列表
     *
     * @return
     */
    public ArrayList<Video> getAllVideos() {
        return mAllVideo;
    }

    public boolean isAutoHideController() {
        return mAutoHideController;
    }

    /**
     * 设置时候自动隐藏视频播放控制栏
     *
     * @param autoHideController
     */
    public void setAutoHideController(boolean autoHideController) {
        mAutoHideController = autoHideController;
    }

    /**
     * 创建videoRender和audioRender
     *
     * @param url
     */
    private void buildRenders(String url) {
        String userAgent = Util.getUserAgent(mContext, TAG);

        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(mainHandler,
                null);
        DataSource dataSource = new DefaultUriDataSource(mContext, bandwidthMeter, userAgent);
        ExtractorSampleSource sampleSource = new ExtractorSampleSource(Uri.parse(url), dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE);
        mVideoRenderer = new MediaCodecVideoTrackRenderer(mContext,
                sampleSource, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, mainHandler,
                this, 50);
        mAudioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                null, true, mainHandler, this, AudioCapabilities.getCapabilities(mContext));

        mExoPlayer.prepare(mVideoRenderer, mAudioRenderer);
    }


    /**
     * 尝试播放视频
     *
     * @param isPlayWhenReady
     */
    private void play(Boolean isPlayWhenReady) {
        Surface surface = mSurfaceView.getHolder().getSurface();
        if (surface == null || !surface.isValid()) {
            Log.e(TAG, "surface not ready");
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
        mPosition = 0;
    }

    /**
     * 关闭视频
     */
    public void close() {
        mMediaController.setPlayState(MediaController.PlayState.PAUSE);
        stopHideTimer(true);
        stopUpdateTimer();
        if (null != mExoPlayer) {
            mExoPlayer.setPlayWhenReady(false);
        }
        mSurfaceView.setVisibility(GONE);
    }

    /**
     * 释放player
     */
    public void releasePlayer() {
        mHasRenderToSurface = false;
        if (mExoPlayer != null) {
            mPosition = mExoPlayer.getCurrentPosition();
            mExoPlayer.release();
            mExoPlayer = null;
        }
        if (mUpdateTimer != null) {
            mUpdateTimer.cancel();
            mUpdateTimer = null;
        }
        mVideoRenderer = null;
        mAudioRenderer = null;
    }


    /**
     * 加载并开始播放视频
     *
     * @param uri
     * @param seekTime
     */
    private void loadAndPlay(String uri, int seekTime) {
        showProgressView(true);
        if (TextUtils.isEmpty(uri)) {
            Log.e(TAG, "视频uri为空");
            return;
        }
        buildRenders(uri);
        mSurfaceView.setVisibility(VISIBLE);
        mLayoutProgressbar.setVisibility(View.GONE);
        startPlayVideo(seekTime);
    }

    /**
     * 播放视频
     */
    private void startPlayVideo(int seekTime) {
        if (null == mUpdateTimer) {
            resetUpdateTimer();
        }
        resetHideTimer();
        play(true);
//        mExoPlayer.setPlayWhenReady(true);
        if (seekTime > 0) {
            mExoPlayer.seekTo(seekTime);
        }
        mMediaController.setPlayState(MediaController.PlayState.PLAY);
    }

    /**
     * 更新播放的进度时间
     */
    private void updatePlayTime() {
        if (null == mExoPlayer) {
            return;
        }
        long allTime = mExoPlayer.getDuration();
        long playTime = mExoPlayer.getCurrentPosition();
        mMediaController.setPlayProgressTxt(playTime, allTime);
    }

    /**
     * 更新播放进度条
     */
    private void updatePlayProgress() {
        if (null == mExoPlayer) {
            return;
        }
        long allTime = mExoPlayer.getDuration();
        long playTime = mExoPlayer.getCurrentPosition();
        if (allTime < 1) {
            return;
        }
        int loadProgress = mExoPlayer.getBufferedPercentage();
        long progress = playTime * 100 / allTime;
        mMediaController.setProgressBar((int) progress, loadProgress);
    }

    /**
     * 显示loading圈
     *
     * @param isTransparentBg isTransparentBg
     */
    private void showProgressView(Boolean isTransparentBg) {
        mLayoutProgressbar.setVisibility(VISIBLE);
        if (!isTransparentBg) {
            mLayoutProgressbar.setBackgroundResource(android.R.color.black);
        } else {
            mLayoutProgressbar.setBackgroundResource(android.R.color.transparent);
        }
    }

    public void showOrHideProgressView(boolean showFlag) {
        mLayoutProgressbar.setVisibility(showFlag ? VISIBLE : GONE);
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

    /**
     * 显示或隐藏视频播放控制栏
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
        }
    }

    // ExoPlayer.Listener implementation
    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d(TAG, "onPlayerStateChanged, playWhenReady:" + playWhenReady + ", playbackState:" + playbackState);
        mIsPlayWhenReady = playWhenReady;
        switch (playbackState) {
            case ExoPlayer.STATE_ENDED://视频播放完
                mExoPlayer.seekTo(0);
                showReplay();
                if (null == mVideoPlayCallback) {
                    mVideoPlayCallback.onPlayFinish();
                }
                stopUpdateTimer();
                break;
            case ExoPlayer.STATE_READY://视频准备播放
                if (mIsPlayWhenReady) {//&& mHasRenderToSurface
                    mLayoutProgressbar.setVisibility(View.GONE);
                }
                break;
            default:
                mLayoutProgressbar.setVisibility(View.VISIBLE);
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

    // MediaCodecVideoTrackRenderer.EventListener
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

    //MediaCodecAudioTrackRenderer.EventListener
    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
        Log.d(TAG, "onAudioTrackInitializationError");
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {
        Log.d(TAG, "onAudioTrackWriteError");
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
        void onSwitchPageType();

        void onPlayFinish();
    }
}