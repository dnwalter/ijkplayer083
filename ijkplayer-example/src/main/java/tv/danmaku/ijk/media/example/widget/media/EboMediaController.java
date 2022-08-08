package tv.danmaku.ijk.media.example.widget.media;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Locale;

import tv.danmaku.ijk.media.example.R;

/**
 * Create by ousiyuan on 2022/8/8.
 */
public class EboMediaController extends RelativeLayout implements IMediaController {

    private MediaController.MediaPlayerControl mPlayer;

    private SeekBar mSeekBar;
    private TextView mEndTime, mCurrentTime;
    private ImageView mPlayIv;

    private StringBuilder mFormatBuilder;
    private Formatter mFormatter;

    private boolean mShowing;
    private boolean mDragging;

    public EboMediaController(Context context, AttributeSet attrs) {
        super(context, attrs);
        View root = View.inflate(context, R.layout.media_controller_ebo, this);
        initView(root);
    }

    private void initView(View root) {
        mSeekBar = (SeekBar) root.findViewById(R.id.video_seekbar);
        mEndTime = (TextView) root.findViewById(R.id.video_duration);
        mCurrentTime = (TextView) root.findViewById(R.id.video_current);
        mPlayIv = (ImageView) root.findViewById(R.id.iv_control);

        mPlayIv.requestFocus();
        mPlayIv.setOnClickListener(mPauseListener);

        mSeekBar.setOnSeekBarChangeListener(mSeekListener);
        mSeekBar.setMax(1000);

        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    }

    private final OnClickListener mPauseListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            doPauseResume();
            show();
        }
    };

    /**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaPlayerControlExt
     */
    private void disableUnsupportedButtons() {
        try {
            if (mPlayIv != null && !mPlayer.canPause()) {
                mPlayIv.setEnabled(false);
            }
            if (mSeekBar != null && !mPlayer.canSeekBackward() && !mPlayer.canSeekForward()) {
                mSeekBar.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
        }
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private int setProgress() {
        if (mPlayer == null || mDragging) {
            return 0;
        }
        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        if (mSeekBar != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mSeekBar.setProgress( (int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            mSeekBar.setSecondaryProgress(percent * 10);
        }

        if (mEndTime != null)
            mEndTime.setText(stringForTime(duration));
        if (mCurrentTime != null)
            mCurrentTime.setText(stringForTime(position));

        return position;
    }

    private void updatePausePlay() {
        if (mPlayer.isPlaying()) {
            mPlayIv.setImageResource(R.mipmap.ic_video_stop_small);
        } else {
            mPlayIv.setImageResource(R.mipmap.ic_video_play_small);
        }
    }

    private void doPauseResume() {
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }

    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onStartTrackingTouch(SeekBar bar) {
            show();

            mDragging = true;

            // By removing these pending progress messages we make sure
            // that a) we won't update the progress while the user adjusts
            // the seekbar and b) once the user is done dragging the thumb
            // we will post one of these messages to the queue again and
            // this ensures that there will be exactly one message queued up.
            removeCallbacks(mShowProgress);
        }

        @Override
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            mPlayer.seekTo( (int) newposition);
            if (mCurrentTime != null)
                mCurrentTime.setText(stringForTime( (int) newposition));
        }

        @Override
        public void onStopTrackingTouch(SeekBar bar) {
            mDragging = false;
            setProgress();
            updatePausePlay();
            show();

            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            post(mShowProgress);
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
        if (mPlayIv != null) {
            mPlayIv.setEnabled(enabled);
        }
        if (mSeekBar != null) {
            mSeekBar.setEnabled(enabled);
        }
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    @Override
    public void hide() {
        if (mShowing) {
            try {
                removeCallbacks(mShowProgress);
            } catch (IllegalArgumentException ex) {
            }
            setVisibility(View.INVISIBLE);
            mShowing = false;
        }
    }

    @Override
    public boolean isShowing() {
        return mShowing;
    }

    @Override
    public void setAnchorView(View view) {

    }

    @Override
    public void setMediaPlayer(MediaController.MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
    }

    @Override
    public void show(int timeout) {
        if (!mShowing) {
            setVisibility(View.VISIBLE);
            setProgress();
            if (mPlayIv != null) {
                mPlayIv.requestFocus();
            }
            disableUnsupportedButtons();
            mShowing = true;
        }
        updatePausePlay();

        post(mShowProgress);

        if (timeout > 0) {
            removeCallbacks(mFadeOut);
            postDelayed(mFadeOut, timeout);
        }
    }

    private final Runnable mFadeOut = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private final Runnable mShowProgress = new Runnable() {
        @Override
        public void run() {
            int pos = setProgress();
            if (!mDragging && mShowing && mPlayer.isPlaying()) {
                postDelayed(mShowProgress, 1000 - (pos % 1000));
            }
        }
    };

    @Override
    public void show() {
        show(0);
    }

    @Override
    public void showOnce(View view) {

    }
}
