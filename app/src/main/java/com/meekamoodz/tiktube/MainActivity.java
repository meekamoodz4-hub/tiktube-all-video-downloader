package com.meekamoodz.tiktube;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText linkInput;
    private TextView status;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void addSpace(LinearLayout box, int height) {
        View spacer = new View(this);
        box.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13, 18, 35));
        getWindow().setNavigationBarColor(Color.rgb(13, 18, 35));

        Intent incoming = getIntent();
        String shared = incoming.getStringExtra(Intent.EXTRA_TEXT);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 18, 35));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(30), dp(22), dp(28));
        scroll.addView(page);

        TextView brand = label("TIKTUBE", 30, Color.WHITE, true);
        page.addView(brand);
        TextView subtitle = label("ALL VIDEO DOWNLOADER", 12, Color.rgb(91, 213, 190), true);
        page.addView(subtitle);
        addSpace(page, 28);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(rounded(Color.rgb(26, 34, 57), 20));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        card.addView(label("Save videos you have permission to download", 20, Color.WHITE, true));
        addSpace(card, 10);
        card.addView(label("Paste a video link below, or use Share from another app to send a link here.", 14, Color.rgb(190, 199, 219), false));
        addSpace(card, 18);

        linkInput = new EditText(this);
        linkInput.setSingleLine(false);
        linkInput.setMinLines(2);
        linkInput.setTextSize(15);
        linkInput.setHint("Paste video URL here");
        linkInput.setHintTextColor(Color.rgb(130, 143, 167));
        linkInput.setTextColor(Color.WHITE);
        linkInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        linkInput.setBackground(rounded(Color.rgb(15, 22, 41), 12));
        card.addView(linkInput, new LinearLayout.LayoutParams(-1, -2));
        if (shared != null) linkInput.setText(shared);

        addSpace(card, 14);
        Button check = new Button(this);
        check.setText("CHECK VIDEO LINK");
        check.setTextColor(Color.rgb(13, 18, 35));
        check.setAllCaps(false);
        check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        check.setBackground(rounded(Color.rgb(91, 213, 190), 14));
        card.addView(check, new LinearLayout.LayoutParams(-1, dp(52)));

        addSpace(page, 22);
        page.addView(label("SUPPORTED PLATFORMS", 13, Color.rgb(160, 174, 201), true));
        addSpace(page, 10);
        page.addView(label("TikTok  •  Instagram  •  Facebook  •  YouTube", 15, Color.WHITE, true));
        addSpace(page, 20);

        status = label("Next: connect the video extraction engine and download queue.", 13, Color.rgb(190, 199, 219), false);
        status.setPadding(dp(14), dp(14), dp(14), dp(14));
        status.setBackground(rounded(Color.rgb(26, 34, 57), 12));
        page.addView(status, new LinearLayout.LayoutParams(-1, -2));

        addSpace(page, 18);
        TextView note = label("Only save content you own or are allowed to download. Some platforms restrict downloading, and link support can change.", 12, Color.rgb(145, 157, 180), false);
        page.addView(note);

        check.setOnClickListener(v -> {
            String url = linkInput.getText().toString().trim();
            if (url.isEmpty() || !(url.startsWith("https://") || url.startsWith("http://"))) {
                Toast.makeText(this, "Please paste a valid video link.", Toast.LENGTH_SHORT).show();
                return;
            }
            status.setText("Link received. The downloader engine is not connected yet; no file has been downloaded.");
        });

        setContentView(scroll);
    }
}
