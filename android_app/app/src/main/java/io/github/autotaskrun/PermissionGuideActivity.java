package io.github.autotaskrun;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Continuous, user-operated settings guide. Xiaomi screenshots are bundled offline. */
public final class PermissionGuideActivity extends Activity {
    static final String EXTRA_INITIAL = "initial_permissions";
    private static final int BG = 0xFFF4F6F5, INK = 0xFF193630, MUTED = 0xFF75867F;
    private static final int ACCENT = 0xFF21846B, SOFT = 0xFFE9F4EF, WHITE = Color.WHITE;
    private static final int[][] IMAGES = {
            {R.drawable.permission_step1_1, R.drawable.permission_step1_2, R.drawable.permission_step1_3},
            {R.drawable.permission_step2_1, R.drawable.permission_step2_2, R.drawable.permission_step2_3}
    };
    private static final String[][] TITLES = {
            {"打开应用的权限管理", "展开右上角菜单", "允许应用获取权限"},
            {"进入已下载的应用", "找到屏幕操作服务", "开启无障碍服务"}
    };
    private static final String[][] COPY = {
            {
                "点击本节的“打开应用信息”，进入每日学习助手的应用信息页面，再点击“权限管理”。也可以长按桌面上的助手图标，进入应用信息。",
                "进入权限管理后，点击右上角的三个点，展开更多设置。",
                "点击“允许应用获取权限”，按照系统提示确认。部分系统将此项称为“允许受限设置”。完成后返回本页，按下方图示开启无障碍。若没有此菜单，可直接查看无障碍说明。"
            },
            {
                "点击本节的“打开辅助功能设置”。在“通用”页面向下滚动，找到并进入“已下载的应用”。其他品牌可能显示为“已安装的服务”或“下载的服务”。",
                "在列表中找到“每日学习助手 · 屏幕操作”，点击进入。请核对完整名称，避免选到其他应用。",
                "打开“使用每日学习助手 · 屏幕操作”开关，按系统提示确认，再返回助手。只需开启使用开关，快捷方式开关可保持关闭。"
            }
    };
    private ScrollView scroll;
    private LinearLayout content;
    private final LinearLayout[] sections = new LinearLayout[2];
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        build();
        int savedScroll = state == null ? -1 : state.getInt("guide_scroll_y", -1);
        scroll.post(() -> {
            if (savedScroll >= 0) scroll.scrollTo(0, savedScroll);
            else if (!getIntent().getBooleanExtra(EXTRA_INITIAL, false))
                scroll.scrollTo(0, sections[1].getTop());
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("guide_scroll_y", scroll.getScrollY());
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void space(LinearLayout host, int height) { host.addView(new android.view.View(this), lp(1, height)); }
    private TextView text(String copy, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(copy); v.setTextSize(size); v.setTextColor(color);
        v.setLineSpacing(dp(3), 1);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
    private Button button(String copy, int color, int foreground) {
        Button v = new Button(this); v.setText(copy); v.setTextSize(14); v.setAllCaps(false);
        v.setTextColor(foreground); v.setMinHeight(0); v.setMinimumHeight(0); v.setPadding(dp(8), 0, dp(8), 0);
        v.setBackground(shape(color, 16)); return v;
    }

    private void build() {
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom); return insets;
        });
        setContentView(root); root.requestApplyInsets();
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) bars.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        LinearLayout header = row(); header.setPadding(dp(16), dp(12), dp(20), dp(10)); root.addView(header, lp(-1, -2));
        Button back = button("‹", WHITE, INK); back.setTextSize(26); back.setContentDescription("返回助手");
        header.addView(back, lp(42, 44)); back.setOnClickListener(v -> finish());
        TextView title = text("权限设置指南", 21, INK, true); title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout anchors = row(); anchors.setPadding(dp(20), 0, dp(20), dp(10)); root.addView(anchors, lp(-1, 56));
        for (int section = 0; section < 2; section++) {
            final int target = section;
            Button anchor = button(section == 0 ? "首次授权  ↓" : "开启无障碍  ↓", SOFT, ACCENT);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1); p.setMargins(dp(3), 0, dp(3), 0);
            anchors.addView(anchor, p);
            anchor.setOnClickListener(v -> scroll.smoothScrollTo(0, sections[target].getTop()));
        }

        scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false);
        scroll.setPadding(dp(20), dp(2), dp(20), dp(18)); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column(); scroll.addView(content);
        TextView note = text("示例为小米型号手机\n向下滑动即可查看全部图示。系统版本和其他品牌的菜单名称可能不同，请按相近入口操作。", 12, MUTED, false);
        note.setPadding(dp(14), dp(12), dp(14), dp(12)); note.setBackground(shape(SOFT, 16));
        content.addView(note, lp(-1, -2)); space(content, 20);
        addSection(0); space(content, 24); addSection(1);

        LinearLayout dock = row(); dock.setPadding(dp(20), dp(12), dp(20), dp(12)); dock.setBackgroundColor(WHITE);
        root.addView(dock, lp(-1, -2));
        status = text("", 13, ACCENT, true); dock.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        Button done = button("返回助手", ACCENT, WHITE); dock.addView(done, lp(112, 48)); done.setOnClickListener(v -> finish());
        refreshStatus();
    }

    private void addSection(int section) {
        LinearLayout group = column(); sections[section] = group; content.addView(group, lp(-1, -2));
        group.addView(text(section == 0 ? "首次授权" : "开启无障碍", 21, INK, true)); space(group, 6);
        group.addView(text(section == 0 ? "示例为小米型号手机。首次设置时，先允许应用获取权限。" : "示例为小米型号手机。无障碍开关关闭后，可按以下图示重新开启。", 13, MUTED, false));
        space(group, 12);
        Button settings = button(section == 0 ? "打开应用信息  ›" : "打开辅助功能设置  ›", ACCENT, WHITE);
        group.addView(settings, lp(-1, 48)); settings.setOnClickListener(v -> openSystemSettings(section)); space(group, 16);
        for (int step = 0; step < 3; step++) {
            LinearLayout card = column(); card.setPadding(dp(16), dp(18), dp(16), dp(16)); card.setBackground(shape(WHITE, 22));
            group.addView(card, lp(-1, -2));
            card.addView(text((step + 1) + ". " + TITLES[section][step], 18, INK, true)); space(card, 8);
            card.addView(text(COPY[section][step], 14, MUTED, false)); space(card, 14);
            LinearLayout frame = column(); frame.setPadding(dp(4), dp(4), dp(4), dp(4));
            GradientDrawable border = shape(BG, 16); border.setStroke(dp(1), 0xFFDDE7E1); frame.setBackground(border);
            LinearLayout.LayoutParams imageMargins = lp(-1, -2); imageMargins.setMargins(dp(10), 0, dp(10), 0);
            card.addView(frame, imageMargins);
            ImageView image = new ImageView(this); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImageResource(IMAGES[section][step]); image.setContentDescription("小米手机示例：" + TITLES[section][step]);
            image.setBackground(shape(WHITE, 12)); image.setClipToOutline(true); frame.addView(image, lp(-1, -2));
            if (step < 2) space(group, 14);
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean enabled = AccessibilityGrant.enabled(this);
        status.setText(enabled ? "无障碍已开启" : "无障碍未开启");
    }

    private void openSystemSettings(int section) {
        Intent intent = section == 0
                ? new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))
                : new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        try { startActivity(intent); }
        catch (ActivityNotFoundException error) { Toast.makeText(this, "无法打开此入口，请按图示手动进入系统设置", Toast.LENGTH_LONG).show(); }
    }
}
