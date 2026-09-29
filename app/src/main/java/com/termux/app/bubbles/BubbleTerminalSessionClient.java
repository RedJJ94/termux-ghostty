package com.termux.app.bubbles;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.BubbleSessionActivity;
import com.termux.shared.interact.ShareUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.shared.termux.terminal.TermuxTerminalStyling;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public final class BubbleTerminalSessionClient extends TermuxTerminalSessionClientBase {

    private final BubbleSessionActivity mActivity;

    private static final String LOG_TAG = "BubbleTerminalSessionClient";

    public BubbleTerminalSessionClient(@NonNull BubbleSessionActivity activity) {
        mActivity = activity;
    }

    public void onCreate() {
        applyTerminalStyling();
    }

    public void onResume() {
        updateBackgroundColor();
        mActivity.setTerminalCursorBlinkerState(true, true);
    }

    public void onStop() {
        mActivity.setTerminalCursorBlinkerState(false, true);
    }

    @Override
    public void onFrameAvailable(@NonNull TerminalSession changedSession) {
        if (!mActivity.isVisible()) return;
        if (changedSession != mActivity.getCurrentSession()) return;
        mActivity.getTerminalView().onFrameAvailable();
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession updatedSession) {
        if (updatedSession != mActivity.getCurrentSession()) return;
        mActivity.updateSessionTitle();
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
        if (finishedSession != mActivity.getCurrentSession()) return;
        mActivity.onSessionFinished();
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
        if (!mActivity.isVisible()) return;
        ShareUtils.copyTextToClipboard(mActivity, text);
    }

    @Override
    public void onPasteTextFromClipboard(@Nullable TerminalSession session) {
        if (!mActivity.isVisible()) return;

        String text = ShareUtils.getTextStringFromClipboardIfSet(mActivity, true);
        TerminalSession currentSession = mActivity.getCurrentSession();
        if (text == null || currentSession == null) return;
        currentSession.paste(text);
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession changedSession) {
        if (changedSession != mActivity.getCurrentSession()) return;
        updateBackgroundColor();
    }

    @Override
    public void onTerminalCursorStateChange(boolean enabled) {
        if (enabled && !mActivity.isVisible()) return;
        mActivity.setTerminalCursorBlinkerState(enabled, false);
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return mActivity.getProperties().getTerminalCursorStyle();
    }

    public boolean isSessionFocused(@Nullable TerminalSession session) {
        if (session == null) return false;
        if (!mActivity.isVisible()) return false;
        if (!mActivity.hasWindowFocus()) return false;
        return session == mActivity.getCurrentSession();
    }

    public void applyTerminalStyling() {
        TermuxTerminalStyling.applyTerminalStyling(
            mActivity, mActivity.getProperties(), mActivity.getTerminalView(), mActivity.getCurrentSession());
        updateBackgroundColor();
    }

    public void updateBackgroundColor() {
        if (!mActivity.isVisible()) return;

        TermuxTerminalStyling.applyBackgroundColor(
            mActivity.getCurrentSession(), mActivity.getWindow().getDecorView(), mActivity.getExtraKeysView());
    }
}
