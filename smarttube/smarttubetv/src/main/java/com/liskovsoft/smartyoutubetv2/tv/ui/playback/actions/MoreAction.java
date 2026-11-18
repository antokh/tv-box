package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.Action;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * TV Box player: the "•••" button. Every player button that isn't one of the few main ones lives
 * behind it, in a list (see VideoPlayerGlue#showMoreOptions).
 */
public class MoreAction extends Action {
    public MoreAction(Context context) {
        super(R.id.action_more);
        setIcon(ContextCompat.getDrawable(context, R.drawable.tvbox_more));
        setLabel1(context.getString(R.string.tvbox_more_options));
    }
}
