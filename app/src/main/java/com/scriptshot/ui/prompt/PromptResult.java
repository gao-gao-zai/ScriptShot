package com.scriptshot.ui.prompt;

import androidx.annotation.Nullable;

public final class PromptResult {
    public final boolean cancelled;
    @Nullable public final int[] indexes;
    public final long dateMillis;

    public PromptResult(boolean cancelled, @Nullable int[] indexes, long dateMillis) {
        this.cancelled = cancelled;
        this.indexes = indexes;
        this.dateMillis = dateMillis;
    }

    public static PromptResult cancelled() {
        return new PromptResult(true, null, 0L);
    }

    public static PromptResult menuResult(int[] indexes) {
        return new PromptResult(false, indexes, 0L);
    }

    public static PromptResult dateResult(long millis) {
        return new PromptResult(false, null, millis);
    }
}
