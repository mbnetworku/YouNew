package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Presenter;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

/**
 * One row in the settings category list (YouTube TV style): icon + name.
 * Normal row = light text on the dark page. Focused row = white pill with dark bold text.
 * The pill shape itself comes from the settings_category_bg drawable.
 */
public class SettingsCardPresenter extends Presenter {
    private static final int TEXT_NORMAL = 0xFFF1F1F1;
    private static final int TEXT_FOCUSED = 0xFF0F0F0F;

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        Context context = parent.getContext();

        @SuppressLint("InflateParams")
        View container = LayoutInflater.from(context).inflate(R.layout.settings_card, parent, false);

        TextView textView = container.findViewById(R.id.settings_title);
        ImageView imageView = container.findViewById(R.id.settings_image);
        textView.setTextColor(TEXT_NORMAL);
        imageView.setColorFilter(TEXT_NORMAL);

        ViewUtil.setTextScrollSpeed(textView, getCardTextScrollSpeed(context));

        container.setOnFocusChangeListener((v, hasFocus) -> {
            int color = hasFocus ? TEXT_FOCUSED : TEXT_NORMAL;

            textView.setTextColor(color);
            textView.setTypeface(null, hasFocus ? Typeface.BOLD : Typeface.NORMAL);
            imageView.setColorFilter(color);

            if (hasFocus) {
                ViewUtil.enableMarquee(textView);
            } else {
                ViewUtil.disableMarquee(textView);
            }
        });

        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        SettingsItem settingsItem = (SettingsItem) item;

        TextView textView = viewHolder.view.findViewById(R.id.settings_title);

        textView.setText(settingsItem.title);

    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }

    protected boolean isCardTextAutoScrollEnabled(Context context) {
        return MainUIData.instance(context).isCardTextAutoScrollEnabled();
    }

    protected float getCardTextScrollSpeed(Context context) {
        return MainUIData.instance(context).getCardTextScrollSpeed();
    }
}
