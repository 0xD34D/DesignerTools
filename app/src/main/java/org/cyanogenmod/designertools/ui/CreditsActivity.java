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
package org.cyanogenmod.designertools.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.os.Bundle;

import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.cyanogenmod.designertools.R;
import org.cyanogenmod.designertools.utils.EdgeToEdgeUtils;

public class CreditsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_credits);
        goEdgeToEdge();
    }

    private void circularRevealActivity(View v) {

        int cx = v.getWidth() / 2;
        int cy = v.getHeight() / 2;

        float finalRadius = Math.max(v.getWidth(), v.getHeight());

        // create the animator for this view (the start radius is zero)
        Animator circularReveal = ViewAnimationUtils
                .createCircularReveal(v, cx, cy, 0, finalRadius);
        circularReveal.setDuration(getResources().getInteger(
                R.integer.credits_circular_reveal_duration));

        // make the view visible and start the animation
        v.setVisibility(View.VISIBLE);
        circularReveal.setInterpolator(new AccelerateDecelerateInterpolator());
        circularReveal.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                animateContent();
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        circularReveal.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        final View rootLayout = findViewById(R.id.activity_credits);
        rootLayout.setVisibility(View.INVISIBLE);

        ViewTreeObserver viewTreeObserver = rootLayout.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    circularRevealActivity(rootLayout);
                    rootLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            });
        }
    }

    private void animateContent() {
        View avatar1 = findViewById(R.id.avatar1);
        avatar1.setScaleX(0);
        avatar1.setScaleY(0);
        avatar1.setVisibility(View.VISIBLE);
        View text1 = findViewById(R.id.text1);
        text1.setAlpha(0);
        text1.setVisibility(View.VISIBLE);
        View avatar2 = findViewById(R.id.avatar2);
        avatar2.setScaleX(0);
        avatar2.setScaleY(0);
        avatar2.setVisibility(View.VISIBLE);
        View text2 = findViewById(R.id.text2);
        text2.setAlpha(0);
        text2.setVisibility(View.VISIBLE);
        View avatar3 = findViewById(R.id.avatar3);
        avatar3.setScaleX(0);
        avatar3.setScaleY(0);
        avatar3.setVisibility(View.VISIBLE);
        View text3 = findViewById(R.id.text3);
        text3.setAlpha(0);
        text3.setVisibility(View.VISIBLE);
        View avatar4 = findViewById(R.id.avatar4);
        avatar4.setScaleX(0);
        avatar4.setScaleY(0);
        avatar4.setVisibility(View.VISIBLE);
        View text4 = findViewById(R.id.text4);
        text4.setAlpha(0);
        text4.setVisibility(View.VISIBLE);

        Interpolator interpolator = new FastOutSlowInInterpolator();
        long duration = 375L;
        long delay = duration / 3;

        AnimatorSet anim1 = new AnimatorSet();
        anim1.play(ObjectAnimator.ofFloat(avatar1, "scaleX", 1f))
                .with(ObjectAnimator.ofFloat(avatar1, "scaleY", 1f))
                .with(ObjectAnimator.ofFloat(text1, "alpha", 1f));
        anim1.setDuration(duration);
        anim1.setInterpolator(interpolator);
        AnimatorSet anim2 = new AnimatorSet();
        anim2.play(ObjectAnimator.ofFloat(avatar2, "scaleX", 1f))
                .with(ObjectAnimator.ofFloat(avatar2, "scaleY", 1f))
                .with(ObjectAnimator.ofFloat(text2, "alpha", 1f));
        anim2.setDuration(duration);
        anim2.setInterpolator(interpolator);
        anim2.setStartDelay(delay);
        AnimatorSet anim3 = new AnimatorSet();
        anim3.play(ObjectAnimator.ofFloat(avatar3, "scaleX", 1f))
                .with(ObjectAnimator.ofFloat(avatar3, "scaleY", 1f))
                .with(ObjectAnimator.ofFloat(text3, "alpha", 1f));
        anim3.setDuration(duration);
        anim3.setInterpolator(interpolator);
        anim3.setStartDelay(delay * 2);
        AnimatorSet anim4 = new AnimatorSet();
        anim4.play(ObjectAnimator.ofFloat(avatar4, "scaleX", 1f))
                .with(ObjectAnimator.ofFloat(avatar4, "scaleY", 1f))
                .with(ObjectAnimator.ofFloat(text4, "alpha", 1f));
        anim4.setDuration(duration);
        anim4.setInterpolator(interpolator);
        anim4.setStartDelay(delay * 3);
        AnimatorSet set = new AnimatorSet();
        set.play(anim1).with(anim2);
        set.play(anim2).with(anim3);
        set.play(anim3).with(anim4);
        set.start();
    }

    private void goEdgeToEdge() {
        View decorView = getWindow().getDecorView();
        EdgeToEdgeUtils.addEdgeToEdgeFlags(decorView);

        final LinearLayout container = findViewById(R.id.activity_credits);
        container.setClipToPadding(false);

        final View headerContainer = findViewById(R.id.header_container);
        final View headerTitleContainer = findViewById(R.id.header_title_container);

        final ViewGroup.LayoutParams params = headerContainer.getLayoutParams();
        final int headerHeight = headerContainer.getLayoutParams().height;

        EdgeToEdgeUtils.addEdgeToEdgeFlags(container);

        EdgeToEdgeUtils.requestAndApplyInsets(container, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View ignored, WindowInsetsCompat insets) {
                container.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom());

                params.height = headerHeight + insets.getSystemWindowInsetTop();
                headerContainer.setLayoutParams(params);

                headerTitleContainer.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);

                return insets;
            }
        });
    }
}
