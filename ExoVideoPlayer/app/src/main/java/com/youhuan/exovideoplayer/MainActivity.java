package com.youhuan.exovideoplayer;

import android.app.Activity;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
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


public class MainActivity extends Activity
        implements View.OnClickListener,
        SurfaceHolder.Callback,
        ExoPlayer.Listener,
        MediaCodecVideoTrackRenderer.EventListener {

    private final static String TAG = "MainActivity";

    private ViewGroup mDecorView;
    private ExoPlayer mExoPlayer;
    private SurfaceView mSurfaceView;
    private FrameLayout mLayoutPlayIcon;
    private ImageView mPlayImage;
    private ProgressBar mLoadingIcon;

    private MediaController mMediaController;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDecorView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        Log.d(TAG, "onCreate");
        // 视屏显示控件
        mSurfaceView = (SurfaceView) findViewById(R.id.surface_video);
        // loading控件
        mLoadingIcon = (ProgressBar) findViewById(R.id.loading_icon);
        //初始化surface
        mSurfaceView.getHolder().addCallback(this);
        //绑定点击事件, 控制显示隐藏controller
        mSurfaceView.setOnClickListener(this);
        //创建controller
        mMediaController = new MediaController(this);
        mMediaController.setAnchorView(mSurfaceView);
        mMediaController.hide();
//        mLayoutPlayIcon = (FrameLayout) findViewById(R.id.layout_play_icon);
        // 不需要对onClick做任何处理，防止点击到底层的视频播放控件
//        mLayoutPlayIcon.setOnClickListener(this);
        // 播放按钮
        mPlayImage = (ImageView) findViewById(R.id.play_icon);
        mPlayImage.setOnClickListener(this);

        CookieHandler currentHandler = CookieHandler.getDefault();
        if (currentHandler != defaultCookieManager) {
            CookieHandler.setDefault(defaultCookieManager);
        }

        preparePlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mPlayImage.setVisibility(View.VISIBLE);

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        releasePlayer();
        mIsPlayWhenReady = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    /**
     * 创建ExoPlayer
     */
    private void preparePlayer() {
        mLoadingIcon.setVisibility(View.GONE);
        mMediaController.hide();
        if (mUrl == null || mUrl.isEmpty()) {
            Toast.makeText(this, "视频url地址为空", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mExoPlayer == null) {
            mExoPlayer = ExoPlayer.Factory.newInstance(2, 1000, 5000);
            mExoPlayer.addListener(this);
            mExoPlayer.seekTo(mPosition);
            Log.d(TAG, "seekTo:" + mPosition);
            mPlayerControl = new PlayerControl(mExoPlayer);
            mMediaController.setMediaPlayer(mPlayerControl);
            mMediaController.setEnabled(true);
        }
        buildRenders();
    }

    /**
     * 释放player
     */
    private void releasePlayer() {
        mHasRenderToSurface = false;
        mMediaController.hide();
        if (mExoPlayer != null) {
            mPosition = mExoPlayer.getCurrentPosition();
            mExoPlayer.release();
            mExoPlayer = null;
            mPlayerControl = null;
        }
        mVideoRenderer = null;
    }

    /**
     * 创建videoRender和audioRender
     */
    private void buildRenders() {
        FrameworkSampleSource sampleSource = new FrameworkSampleSource(this, Uri.parse(mUrl), null);
        mVideoRenderer = new MediaCodecVideoTrackRenderer(this, sampleSource,
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
        mLoadingIcon.setVisibility(View.INVISIBLE);
        mMediaController.hide();
        mPlayImage.setVisibility(View.VISIBLE);
        releasePlayer();
        mPosition = 0;
    }

    public void onClick(View v) {
        Log.d(TAG, "onClick");
        switch (v.getId()) {
            case R.id.surface_video:
                if (mMediaController.isShowing()) {
                    mMediaController.hide();
                } else {
                    mMediaController.show();
                }
                break;
            case R.id.play_icon:
//                mLayoutPlayIcon.setVisibility(View.GONE);
                mPlayImage.setVisibility(View.GONE);
                preparePlayer();
                mIsPlayWhenReady = true;
                play(mIsPlayWhenReady);
                break;
            default:
                break;
        }
    }

    // ExoPlayer.Listener implementation

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        // Do nothing.
        Log.d(TAG, "onPlayerStateChanged, " + playWhenReady + ", " + playbackState);

        mIsPlayWhenReady = playWhenReady;
        //渲染到surface loading状态
        if (mIsPlayWhenReady && !mHasRenderToSurface) {
            mLoadingIcon.setVisibility(View.VISIBLE);
        }
        switch (playbackState) {
            case ExoPlayer.STATE_ENDED:
                mLoadingIcon.setVisibility(View.INVISIBLE);
                mExoPlayer.seekTo(0);
                showReplay();
                break;
            case ExoPlayer.STATE_READY:
                mLoadingIcon.setVisibility(View.INVISIBLE);
                if (!mIsPlayWhenReady) {
                    mLoadingIcon.setVisibility(View.INVISIBLE);
                    mMediaController.show();
                }
                if (mIsPlayWhenReady && mHasRenderToSurface) {
                    mLoadingIcon.setVisibility(View.INVISIBLE);
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
        Toast.makeText(this, "发生错误, 请检验网络或者文件是否可用. error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        mLoadingIcon.setVisibility(View.INVISIBLE);
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
}