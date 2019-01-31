/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cyanogenmod.designertools.overlays;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.cyanogenmod.designertools.DesignerToolsApplication;
import org.cyanogenmod.designertools.qs.ColorPickerQuickSettingsTile;
import org.cyanogenmod.designertools.qs.OnOffTileState;
import org.cyanogenmod.designertools.R;
import org.cyanogenmod.designertools.widget.MagnifierNodeView;
import org.cyanogenmod.designertools.widget.MagnifierView;

import java.nio.ByteBuffer;

public class ColorPickerOverlay extends Service {
    private static final int NOTIFICATION_ID = ColorPickerOverlay.class.hashCode();

    private static final String ACTION_HIDE_PICKER = "hide_picker";
    private static final String ACTION_SHOW_PICKER = "show_picker";

    private static final float DAMPENING_FACTOR_DP = 25.0f;

    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mNodeParams;
    private WindowManager.LayoutParams mMagnifierParams;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;

    private MagnifierView mMagnifierView;
    private MagnifierNodeView mMagnifierNodeView;
    private Rect mPreviewArea;
    private int mPreviewSampleWidth;
    private int mPreviewSampleHeight;

    private float mNodeToMagnifierDistance;
    private float mAngle = (float) Math.PI * 1.5f;

    private PointF mLastPosition;
    private PointF mStartPosition;
    private float mDampeningFactor;

    private int mCurrentOrientation;

    private final Object mScreenCaptureLock = new Object();

    private boolean mAnimating = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setup();
        ((DesignerToolsApplication) getApplicationContext()).setColorPickerOn(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        teardownMediaProjection();
        mMediaProjection = null;
        mVirtualDisplay = null;

        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mMagnifierView != null) {
            animateColorPickerOut(new Runnable() {
                @Override
                public void run() {
                    removeViewIfAttached(mMagnifierView);
                    mMagnifierView = null;
                    if (mMagnifierNodeView != null) {
                        removeViewIfAttached(mMagnifierNodeView);
                        mMagnifierNodeView = null;
                    }
                }
            });
        }
        ((DesignerToolsApplication) getApplicationContext()).setColorPickerOn(false);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // recreate the media projection on orientation changes
        if (mCurrentOrientation != newConfig.orientation) {
            recreateMediaPrjection();
            mCurrentOrientation = newConfig.orientation;
        }
    }

    private void setup() {
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        setupMediaProjection();

        final Resources res = getResources();
        mCurrentOrientation = res.getConfiguration().orientation;

        int magnifierWidth = res.getDimensionPixelSize(R.dimen.picker_magnifying_ring_width);
        int magnifierHeight = res.getDimensionPixelSize(R.dimen.picker_magnifying_ring_height);

        int nodeViewSize = res.getDimensionPixelSize(R.dimen.picker_node_size);
        DisplayMetrics dm = res.getDisplayMetrics();

        mNodeParams = new WindowManager.LayoutParams(
                nodeViewSize, nodeViewSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        mNodeParams.gravity = Gravity.TOP | Gravity.LEFT;
        mMagnifierParams = new WindowManager.LayoutParams(
                magnifierWidth, magnifierHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        mMagnifierParams.gravity = Gravity.TOP | Gravity.LEFT;

        final int x = dm.widthPixels / 2;
        final int y = dm.heightPixels / 2;
        mNodeParams.x = x - nodeViewSize / 2;
        mNodeParams.y = y - nodeViewSize / 2;

        mMagnifierParams.x = x - magnifierWidth / 2;
        mMagnifierParams.y = mNodeParams.y - (magnifierHeight + nodeViewSize / 2);

        mMagnifierView = (MagnifierView) View.inflate(this, R.layout.color_picker_magnifier, null);
        mMagnifierView.setOnTouchListener(mDampenedOnTouchListener);
        mMagnifierNodeView = new MagnifierNodeView(this);
        mMagnifierNodeView.setOnTouchListener(mOnTouchListener);
        addOverlayViewsIfDetached();
        mMagnifierView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                animateColorPickerIn();
                mMagnifierView.getViewTreeObserver().removeOnPreDrawListener(this);
                return false;
            }
        });

        mPreviewSampleWidth = res.getInteger(R.integer.color_picker_sample_width);
        mPreviewSampleHeight = res.getInteger(R.integer.color_picker_sample_height);
        mPreviewArea = new Rect(x - mPreviewSampleWidth / 2, y - mPreviewSampleHeight / 2,
                x + mPreviewSampleWidth / 2 + 1, y + mPreviewSampleHeight / 2 + 1);

        mNodeToMagnifierDistance = (Math.min(magnifierWidth, magnifierHeight) + nodeViewSize * 2) / 2f;
        mLastPosition = new PointF();
        mStartPosition = new PointF();
        mDampeningFactor = DAMPENING_FACTOR_DP * dm.density;

        IntentFilter filter = new IntentFilter(ColorPickerQuickSettingsTile.ACTION_TOGGLE_STATE);
        filter.addAction(ColorPickerQuickSettingsTile.ACTION_UNPUBLISH);
        filter.addAction(ACTION_HIDE_PICKER);
        filter.addAction(ACTION_SHOW_PICKER);
        registerReceiver(mReceiver, filter);
        startForeground(NOTIFICATION_ID, getPersistentNotification(true));
    }

    private void removeViewIfAttached(View v) {
        if (v.isAttachedToWindow()) {
            mWindowManager.removeView(v);
        }
    }

    private void removeOverlayViewsIfAttached() {
        removeViewIfAttached(mMagnifierView);
        removeViewIfAttached(mMagnifierNodeView);
    }

    private void addOverlayViewsIfDetached () {
        if (mMagnifierNodeView != null && !mMagnifierNodeView.isAttachedToWindow()) {
            mWindowManager.addView(mMagnifierNodeView, mNodeParams);
        }
        if (mMagnifierView != null && !mMagnifierView.isAttachedToWindow()) {
            mWindowManager.addView(mMagnifierView, mMagnifierParams);
        }
    }

    private void animateColorPickerIn() {
        mMagnifierView.setScaleX(0);
        mMagnifierView.setScaleY(0);
        mMagnifierNodeView.setVisibility(View.GONE);

        final int startX = mMagnifierParams.x + (mMagnifierParams.width - mNodeParams.width) / 2;
        final int startY = mMagnifierParams.y + (mMagnifierParams.height - mNodeParams.height) / 2;
        final int endX = mNodeParams.x;
        final int endY = mNodeParams.y;
        mNodeParams.x = startX;
        mNodeParams.y = startY;
        mWindowManager.updateViewLayout(mMagnifierNodeView, mNodeParams);
        final ValueAnimator animator = ObjectAnimator.ofFloat(null, "", 0f, 1f);
        animator.setDuration(200);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();
                mNodeParams.x = startX + (int) (fraction * (endX - startX));
                mNodeParams.y = startY + (int) (fraction * (endY - startY));
                mWindowManager.updateViewLayout(mMagnifierNodeView, mNodeParams);
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mAnimating = true;
                mMagnifierNodeView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mAnimating = false;
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });

        mMagnifierView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(new FastOutSlowInInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        animator.start();
                    }
                });

    }

    private void animateColorPickerOut(final Runnable endAction) {
        final int endX = mMagnifierParams.x + (mMagnifierParams.width - mNodeParams.width) / 2;
        final int endY = mMagnifierParams.y + (mMagnifierParams.height - mNodeParams.height) / 2;
        final int startX = mNodeParams.x;
        final int startY = mNodeParams.y;
        mNodeParams.x = startX;
        mNodeParams.y = startY;
        mWindowManager.updateViewLayout(mMagnifierNodeView, mNodeParams);
        final ValueAnimator animator = ObjectAnimator.ofFloat(null, "", 0f, 1f);
        animator.setDuration(200);
        animator.setInterpolator(new FastOutSlowInInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();
                mNodeParams.x = startX + (int) (fraction * (endX - startX));
                mNodeParams.y = startY + (int) (fraction * (endY - startY));
                mWindowManager.updateViewLayout(mMagnifierNodeView, mNodeParams);
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mAnimating = true;
                mMagnifierNodeView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mAnimating = false;
                mMagnifierNodeView.setVisibility(View.GONE);
                mNodeParams.x = startX;
                mNodeParams.y = startY;
                mMagnifierView.animate()
                        .scaleX(0)
                        .scaleY(0)
                        .setInterpolator(new FastOutSlowInInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (endAction != null) {
                                    endAction.run();
                                }
                            }
                        });

            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animator.start();
    }

    public Bitmap getScreenBitmapRegion(Image image, Rect region) {
        if (image == null) {
            return null;
        }
        final int maxX = image.getWidth() - 1;
        final int maxY = image.getHeight() - 1;
        final int width = region.width();
        final int height = region.height();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int rowStride = planes[0].getRowStride();
        int pixelStride = planes[0].getPixelStride();
        int color, pixelX, pixelY;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixelX = region.left + x;
                pixelY = region.top + y;
                if (pixelX >= 0 && pixelX <= maxX && pixelY >= 0 && pixelY <= maxY) {
                    int index = (pixelY * rowStride + pixelX * pixelStride);
                    buffer.position(index);
                    color = Color.argb(255, buffer.get() & 0xff, buffer.get() & 0xff,
                            buffer.get() & 0xff);
                } else {
                    color = 0;
                }
                bmp.setPixel(x, y, color);
            }
        }
        return bmp;
    }

    private void setupMediaProjection() {
        final DesignerToolsApplication app = (DesignerToolsApplication) getApplication();
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        final Point size = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(size);
        mImageReader = ImageReader.newInstance(size.x, size.y,
                PixelFormat.RGBA_8888, 2);
        mImageReader.setOnImageAvailableListener(mImageAvailableListener, new Handler());
        mMediaProjectionManager = getSystemService(MediaProjectionManager.class);
        mMediaProjection = mMediaProjectionManager.getMediaProjection(app.getScreenRecordResultCode(),
                app.getScreenRecordResultData());
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                ColorPickerOverlay.class.getSimpleName(),
                size.x, size.y, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
    }

    private void teardownMediaProjection() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mMediaProjection != null) {
            mMediaProjection.stop();
        }
    }

    private void recreateMediaPrjection() {
        teardownMediaProjection();
        setupMediaProjection();
    }

    private void updateNotification(boolean actionIsHide) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, getPersistentNotification(actionIsHide));
    }

    private Notification getPersistentNotification(boolean actionIsHide) {
        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                new Intent(actionIsHide ? ACTION_HIDE_PICKER : ACTION_SHOW_PICKER), 0);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setPriority(Notification.PRIORITY_MIN)
                .setSmallIcon(actionIsHide ? R.drawable.ic_qs_colorpicker_on
                        : R.drawable.ic_qs_colorpicker_off)
                .setContentTitle(getString(R.string.color_picker_qs_tile_label))
                .setContentText(getString(actionIsHide ? R.string.notif_content_hide_picker
                        : R.string.notif_content_show_picker))
                .setStyle(new Notification.BigTextStyle().bigText(
                        getString(actionIsHide ? R.string.notif_content_hide_picker
                        : R.string.notif_content_show_picker)))
                .setContentIntent(pi);
        return builder.build();
    }

    private void updateMagnifierViewPosition(int x, int y, float angle) {
        mPreviewArea.left = x - mPreviewSampleWidth / 2;
        mPreviewArea.top = y - mPreviewSampleHeight / 2;
        mPreviewArea.right = x + mPreviewSampleWidth / 2 + 1;
        mPreviewArea.bottom = y + mPreviewSampleHeight / 2 + 1;

        mNodeParams.x = x - mMagnifierNodeView.getWidth() / 2;
        mNodeParams.y = y - mMagnifierNodeView.getHeight() / 2;
        mWindowManager.updateViewLayout(mMagnifierNodeView, mNodeParams);

        mMagnifierParams.x = (int) (mNodeToMagnifierDistance * (float) Math.cos(angle) + x)
                - mMagnifierView.getWidth() / 2;
        mMagnifierParams.y = (int) (mNodeToMagnifierDistance * (float) Math.sin(angle) + y)
                - mMagnifierView.getHeight() / 2;
        mWindowManager.updateViewLayout(mMagnifierView, mMagnifierParams);
    }

    private ImageReader.OnImageAvailableListener mImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            synchronized (mScreenCaptureLock) {
                Image newImage = reader.acquireNextImage();
                if (newImage != null) {
                    if (!mAnimating && mMagnifierView != null) {
                        mMagnifierView.setPixels(getScreenBitmapRegion(newImage, mPreviewArea));
                    }
                    newImage.close();
                }
            }
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ColorPickerQuickSettingsTile.ACTION_UNPUBLISH.equals(action)) {
                stopSelf();
            } else if (ColorPickerQuickSettingsTile.ACTION_TOGGLE_STATE.equals(action)) {
                int state =
                        intent.getIntExtra(OnOffTileState.EXTRA_STATE, OnOffTileState.STATE_OFF);
                if (state == OnOffTileState.STATE_ON) {
                    stopSelf();
                }
            } else if (ACTION_HIDE_PICKER.equals(action)) {
                animateColorPickerOut(new Runnable() {
                    @Override
                    public void run() {
                        removeOverlayViewsIfAttached();
                        teardownMediaProjection();
                        updateNotification(false);
                    }
                });
            } else if (ACTION_SHOW_PICKER.equals(action)) {
                addOverlayViewsIfDetached();
                setupMediaProjection();
                updateNotification(true);
                animateColorPickerIn();
            }
        }
    };

    private View.OnTouchListener mOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mMagnifierNodeView.setVisibility(View.INVISIBLE);
                    break;
                case MotionEvent.ACTION_MOVE:
                    final float rawX = event.getRawX();
                    final float rawY = event.getRawY();
                    final float dx = (mMagnifierParams.x + mMagnifierView.getWidth() / 2) - rawX;
                    final float dy = (mMagnifierParams.y + mMagnifierView.getHeight() / 2) - rawY;
                    mAngle = (float) Math.atan2(dy, dx);
                    updateMagnifierViewPosition((int) rawX, (int) rawY, mAngle);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mMagnifierNodeView.setVisibility(View.VISIBLE);
                    break;
            }
            return true;
        }
    };

    private View.OnTouchListener mDampenedOnTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mLastPosition.set(event.getRawX(), event.getRawY());
                    mStartPosition.set(mNodeParams.x, mNodeParams.y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    final float rawX = event.getRawX();
                    final float rawY = event.getRawY();
                    final float dx = (rawX - mLastPosition.x) / mDampeningFactor;
                    final float dy = (rawY - mLastPosition.y) / mDampeningFactor;
                    final float x = (mStartPosition.x + mMagnifierNodeView.getWidth() / 2) + dx;
                    final float y = (mStartPosition.y + mMagnifierNodeView.getHeight() / 2) + dy;
                    updateMagnifierViewPosition((int) x, (int) y, mAngle);
                    break;
            }
            return true;
        }
    };
}
