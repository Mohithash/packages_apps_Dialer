/*
 * Copyright (C) 2026 BestROM
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

package com.android.dialer.callnote;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.android.dialer.R;
import com.android.dialer.common.LogUtil;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

/**
 * Writes the note attached to one call.
 *
 * <p>The note is saved by this fragment; a host that shows the note itself can implement {@link
 * CallNoteSavedListener} to be told when to re-read it.
 */
public final class CallNoteDialogFragment extends DialogFragment {

  private static final String ARG_CALL_NOTE_ID = "call_note_id";
  private static final String ARG_NUMBER = "number";
  private static final String ARG_NOTE = "note";

  /** Implemented by a host that displays the note and needs to refresh after an edit. */
  public interface CallNoteSavedListener {
    void onCallNoteSaved(String callNoteId, String note);
  }

  private EditText editText;

  /**
   * @param callNoteId the call's date in millis, as a string
   * @param number the number that was called, kept for debugging
   * @param note the note as it stands, or null when there is none yet
   */
  public static CallNoteDialogFragment newInstance(
      String callNoteId, @Nullable String number, @Nullable String note) {
    Bundle args = new Bundle();
    args.putString(ARG_CALL_NOTE_ID, callNoteId);
    args.putString(ARG_NUMBER, number);
    args.putString(ARG_NOTE, note);
    CallNoteDialogFragment fragment = new CallNoteDialogFragment();
    fragment.setArguments(args);
    return fragment;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    // In-call UI is a dark activity with light text. AlertDialog's panel is
    // light; an EditText built from the activity theme keeps white text and
    // becomes invisible. Theme the whole dialog (and the field) for DayNight
    // dialog colors instead.
    Context dialogContext =
        new ContextThemeWrapper(
            requireContext(),
            androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog_Alert);

    Bundle args = requireArguments();

    editText = new EditText(dialogContext);
    editText.setHint(R.string.call_details_note_hint);
    editText.setInputType(
        InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    editText.setMinLines(3);
    editText.setMaxLines(8);
    editText.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
    editText.setTextColor(attrColor(dialogContext, android.R.attr.textColorPrimary, 0xFF212121));
    editText.setHintTextColor(attrColor(dialogContext, android.R.attr.textColorHint, 0xFF757575));
    editText.setBackgroundColor(
        attrColor(dialogContext, android.R.attr.colorBackgroundFloating, 0xFFFFFFFF));
    if (savedInstanceState == null) {
      String note = args.getString(ARG_NOTE);
      if (note != null) {
        editText.setText(note);
        editText.setSelection(editText.getText().length());
      } else {
        // Opened without the note in hand - the in-call screen does this, because it has no reason
        // to have read the store. Fill it in when it arrives, unless the user has started typing.
        prefillFromStore(dialogContext, args.getString(ARG_CALL_NOTE_ID));
      }
    }

    int padding =
        (int)
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20, dialogContext.getResources().getDisplayMetrics());
    FrameLayout container = new FrameLayout(dialogContext);
    container.setPaddingRelative(padding, padding / 2, padding, 0);
    container.addView(editText);

    AlertDialog dialog =
        new AlertDialog.Builder(dialogContext)
            .setTitle(R.string.call_details_note_title)
            .setView(container)
            .setPositiveButton(R.string.call_details_note_save, (d, which) -> save())
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    dialog
        .getWindow()
        .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    return dialog;
  }

  private static int attrColor(Context context, int attr, int fallback) {
    TypedValue value = new TypedValue();
    if (!context.getTheme().resolveAttribute(attr, value, true)) {
      return fallback;
    }
    if (value.resourceId != 0) {
      return ContextCompat.getColor(context, value.resourceId);
    }
    return value.data;
  }

  private void prefillFromStore(Context context, String callNoteId) {
    Futures.addCallback(
        CallNoteUtil.loadNote(context.getApplicationContext(), callNoteId),
        new FutureCallback<String>() {
          @Override
          public void onSuccess(String existing) {
            if (editText != null && editText.getText().length() == 0 && existing != null) {
              editText.setText(existing);
              editText.setSelection(editText.getText().length());
            }
          }

          @Override
          public void onFailure(@NonNull Throwable throwable) {
            LogUtil.e("CallNoteDialogFragment.prefillFromStore", "failed", throwable);
          }
        },
        ContextCompat.getMainExecutor(context));
  }

  private void save() {
    Bundle args = requireArguments();
    String callNoteId = args.getString(ARG_CALL_NOTE_ID);
    String number = args.getString(ARG_NUMBER);
    String note = editText.getText().toString();

    // Saving is deliberately tied to the application context: the note must land even if the
    // screen that opened this dialog goes away first, which is the normal case in a call.
    CallNoteUtil.saveNote(requireContext().getApplicationContext(), callNoteId, number, note);

    if (getActivity() instanceof CallNoteSavedListener) {
      ((CallNoteSavedListener) getActivity()).onCallNoteSaved(callNoteId, note.trim());
    }
  }
}
