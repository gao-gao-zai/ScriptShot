package com.scriptshot.ui.editor;

import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.widget.EditText;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.scriptshot.R;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight JavaScript syntax highlighter for the script editor EditText.
 * It is intentionally simple: keywords, strings, and comments get distinct colors.
 */
public final class JsSyntaxHighlighter implements TextWatcher {

    private static final int MAX_LENGTH_FOR_HIGHLIGHT = 20000;
    private static final Pattern KEYWORD_PATTERN = Pattern.compile(
        "\\b(?:break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|function|if|import|in|instanceof|let|new|return|super|switch|this|throw|try|typeof|var|void|while|with|yield|true|false|null|undefined)\\b"
    );
    private static final Pattern STRING_PATTERN = Pattern.compile(
        "\"(?:\\\\.|[^\\\"])*\"|'(?:\\\\.|[^\\'])*'",
        Pattern.DOTALL
    );
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
        "//.*|/\\*(?:.|\\R)*?\\*/",
        Pattern.MULTILINE
    );

    public static void attach(@NonNull EditText editText) {
        new JsSyntaxHighlighter(editText);
    }

    private final EditText editText;
    private final int keywordColor;
    private final int stringColor;
    private final int commentColor;
    private boolean modifying;

    private JsSyntaxHighlighter(EditText editText) {
        this.editText = editText;
        keywordColor = ContextCompat.getColor(editText.getContext(), R.color.script_highlight_keyword);
        stringColor = ContextCompat.getColor(editText.getContext(), R.color.script_highlight_string);
        commentColor = ContextCompat.getColor(editText.getContext(), R.color.script_highlight_comment);
        editText.addTextChangedListener(this);
        highlight(editText.getText());
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // No-op
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // No-op
    }

    @Override
    public void afterTextChanged(Editable s) {
        highlight(s);
    }

    private void highlight(Editable editable) {
        if (modifying) {
            return;
        }
        modifying = true;
        try {
            clearExisting(editable);
            if (editable == null || editable.length() == 0) {
                return;
            }
            if (editable.length() > MAX_LENGTH_FOR_HIGHLIGHT) {
                return;
            }
            applyPattern(editable, COMMENT_PATTERN, commentColor);
            applyPattern(editable, STRING_PATTERN, stringColor);
            applyPattern(editable, KEYWORD_PATTERN, keywordColor);
        } finally {
            modifying = false;
        }
    }

    private void clearExisting(Editable editable) {
        SyntaxSpan[] spans = editable.getSpans(0, editable.length(), SyntaxSpan.class);
        for (SyntaxSpan span : spans) {
            editable.removeSpan(span);
        }
    }

    private void applyPattern(Editable editable, Pattern pattern, @ColorInt int color) {
        Matcher matcher = pattern.matcher(editable);
        while (matcher.find()) {
            editable.setSpan(new SyntaxSpan(color), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static final class SyntaxSpan extends ForegroundColorSpan {
        SyntaxSpan(@ColorInt int color) {
            super(color);
        }
    }
}
