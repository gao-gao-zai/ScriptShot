package com.scriptshot.ui.prompt;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.DatePicker;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.scriptshot.R;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Lightweight dialog-hosting activity for script-driven prompts.
 */
public final class PromptActivity extends AppCompatActivity {

    public static final String EXTRA_REQUEST_ID = "extra_request_id";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_OPTIONS = "extra_options";
    public static final String EXTRA_MULTI_SELECT = "extra_multi";
    public static final String EXTRA_INITIAL_DATE = "extra_initial_date";

    public static Intent createIntent(Context context, UiRequest request) {
        Intent intent = new Intent(context, PromptActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, request.requestId);
        intent.putExtra(EXTRA_TYPE, request.type.name());
        intent.putExtra(EXTRA_TITLE, request.title);
        intent.putExtra(EXTRA_MULTI_SELECT, request.allowMultiple);
        intent.putExtra(EXTRA_INITIAL_DATE, request.initialDateMillis);
        intent.putExtra(EXTRA_OPTIONS, request.options);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(false);
        String type = getIntent().getStringExtra(EXTRA_TYPE);
        if (type == null) {
            deliverCancellation();
            return;
        }
        UiRequest.Type requestType = UiRequest.Type.valueOf(type);
        switch (requestType) {
            case MENU:
                showMenuDialog();
                break;
            case DATE_PICKER:
                showDatePicker();
                break;
        }
    }

    private void showMenuDialog() {
        String[] options = getIntent().getStringArrayExtra(EXTRA_OPTIONS);
        if (options == null || options.length == 0) {
            deliverCancellation();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(getIntent().getStringExtra(EXTRA_TITLE))
            .setNegativeButton(R.string.prompt_cancel, (dialog, which) -> deliverCancellation());
        boolean multi = getIntent().getBooleanExtra(EXTRA_MULTI_SELECT, false);
        if (multi) {
            boolean[] checked = new boolean[options.length];
            builder.setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.prompt_confirm, (dialog, which) -> deliverMenuResult(collectCheckedIndexes(checked)));
        } else {
            builder.setItems(options, (dialog, which) -> deliverMenuResult(new int[]{which}));
        }
        builder.setOnCancelListener(dialog -> deliverCancellation());
        builder.show();
    }

    private int[] collectCheckedIndexes(boolean[] checked) {
        int count = 0;
        for (boolean value : checked) {
            if (value) {
                count++;
            }
        }
        int[] indexes = new int[count];
        int pointer = 0;
        for (int i = 0; i < checked.length; i++) {
            if (checked[i]) {
                indexes[pointer++] = i;
            }
        }
        return indexes;
    }

    private void showDatePicker() {
        long millis = getIntent().getLongExtra(EXTRA_INITIAL_DATE, System.currentTimeMillis());
        Calendar calendar = GregorianCalendar.getInstance();
        calendar.setTimeInMillis(millis);
        DatePickerDialog dialog = new DatePickerDialog(this, (DatePicker view, int year, int month, int dayOfMonth) -> {
            Calendar picked = GregorianCalendar.getInstance();
            picked.set(year, month, dayOfMonth, 0, 0, 0);
            deliverDateResult(picked.getTimeInMillis());
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        dialog.setOnCancelListener(d -> deliverCancellation());
        dialog.show();
    }

    private int requestId() {
        return getIntent().getIntExtra(EXTRA_REQUEST_ID, -1);
    }

    private void deliverMenuResult(@Nullable int[] indexes) {
        int id = requestId();
        if (id != -1) {
            PromptResultRegistry.deliverMenuResult(id, indexes == null ? new int[0] : indexes);
        }
        finish();
    }

    private void deliverDateResult(long millis) {
        int id = requestId();
        if (id != -1) {
            PromptResultRegistry.deliverDateResult(id, millis);
        }
        finish();
    }

    private void deliverCancellation() {
        int id = requestId();
        if (id != -1) {
            PromptResultRegistry.deliverCancellation(id);
        }
        finish();
    }
}
