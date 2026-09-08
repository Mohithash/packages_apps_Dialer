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

package com.android.dialer.calldetails;

import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.android.dialer.R;

/** Shows the note attached to a call, and opens the editor when tapped. */
final class CallNoteViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

  /** Callback for editing the note of the call being shown. */
  interface CallNoteListener {
    /** Opens the editor for this call's note. */
    void editCallNote(String callNoteId, String currentNote);
  }

  private final CallNoteListener callNoteListener;
  private final TextView action;

  private String callNoteId;
  private String note;

  CallNoteViewHolder(View view, CallNoteListener callNoteListener) {
    super(view);
    this.callNoteListener = callNoteListener;
    action = view.findViewById(R.id.call_note_action);
    action.setOnClickListener(this);
  }

  /** Shows {@code note}, or the "add note" prompt when it is empty. */
  void setNote(String callNoteId, String note) {
    this.callNoteId = callNoteId;
    this.note = note;
    if (TextUtils.isEmpty(note)) {
      action.setText(R.string.call_details_add_note);
    } else {
      action.setText(note);
    }
  }

  @Override
  public void onClick(View view) {
    callNoteListener.editCallNote(callNoteId, note);
  }
}
