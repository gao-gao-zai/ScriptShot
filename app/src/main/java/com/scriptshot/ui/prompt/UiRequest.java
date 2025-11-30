package com.scriptshot.ui.prompt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class UiRequest {
    enum Type {
        MENU,
        DATE_PICKER
    }

    final int requestId;
    final Type type;
    final String title;
    final String message;
    final String[] options;
    final boolean allowMultiple;
    final long initialDateMillis;

    UiRequest(int requestId, Type type, @Nullable String title, @Nullable String message,
              @Nullable String[] options, boolean allowMultiple, long initialDateMillis) {
        this.requestId = requestId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.options = options;
        this.allowMultiple = allowMultiple;
        this.initialDateMillis = initialDateMillis;
    }

    public static UiRequest menu(int requestId, @NonNull String title, @NonNull String[] options, boolean allowMultiple) {
        return new UiRequest(requestId, Type.MENU, title, null, options, allowMultiple, 0L);
    }

    public static UiRequest datePicker(int requestId, @NonNull String title, long initialMillis) {
        return new UiRequest(requestId, Type.DATE_PICKER, title, null, null, false, initialMillis);
    }
}
