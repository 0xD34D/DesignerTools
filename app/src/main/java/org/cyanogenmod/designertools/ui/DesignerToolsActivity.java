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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.WindowInsetsCompat;

import org.cyanogenmod.designertools.R;
import org.cyanogenmod.designertools.utils.EdgeToEdgeUtils;
import org.cyanogenmod.designertools.utils.LaunchUtils;

public class DesignerToolsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_designer_tools);
        if (!LaunchUtils.isCyanogenMod(this)) {
            TextView tv = findViewById(R.id.qs_tiles_section);
            tv.setText(R.string.overlays_section_text);
        }
        View headerGlyph = findViewById(R.id.header_glyph);
        if (headerGlyph != null) {
            headerGlyph.setOnClickListener(mGlyphClickListener);
        }

        goEdgeToEdge();
    }

    private View.OnClickListener mGlyphClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            startActivity(new Intent(DesignerToolsActivity.this, CreditsActivity.class));
        }
    };

    private void goEdgeToEdge() {
        View decorView = getWindow().getDecorView();
        EdgeToEdgeUtils.addEdgeToEdgeFlags(decorView);

        final ScrollView container = findViewById(R.id.main_container);
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
