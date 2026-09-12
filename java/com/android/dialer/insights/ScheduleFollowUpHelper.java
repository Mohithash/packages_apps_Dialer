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

package com.android.dialer.insights;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.android.dialer.R;
import com.bestrom.agent.health.BestromHealthClient;
import com.bestrom.agent.health.HealthProxyContract;

import java.util.Calendar;
import java.util.concurrent.Executors;

/** Confirms then inserts a calendar follow-up via BestromAgent HealthProxy. */
public final class ScheduleFollowUpHelper {

  private ScheduleFollowUpHelper() {}

  public static void confirmAndSchedule(Context context, String displayName) {
    String title =
        context.getString(
            R.string.schedule_followup_title,
            displayName == null || displayName.isEmpty() ? "contact" : displayName);
    new AlertDialog.Builder(context)
        .setTitle(R.string.schedule_followup)
        .setMessage(R.string.schedule_followup_confirm)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(
            android.R.string.ok,
            (d, w) ->
                Executors.newSingleThreadExecutor()
                    .execute(() -> doSchedule(context.getApplicationContext(), title)))
        .show();
  }

  private static void doSchedule(Context context, String title) {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DAY_OF_YEAR, 1);
    cal.set(Calendar.HOUR_OF_DAY, 10);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    long start = cal.getTimeInMillis();
    BestromHealthClient client = new BestromHealthClient(context);
    String err = "unavailable";
    boolean ok = false;
    try {
      if (client.bind()) {
        Bundle r = client.scheduleEvent(title, start, 30, 10);
        if (r != null && r.getBoolean(HealthProxyContract.KEY_OK, false)) {
          ok = true;
        } else if (r != null) {
          err = r.getString(HealthProxyContract.KEY_ERROR, err);
        }
      }
    } catch (Exception e) {
      err = e.getMessage() != null ? e.getMessage() : "error";
    } finally {
      client.unbind();
    }
    boolean success = ok;
    String error = err;
    new Handler(Looper.getMainLooper())
        .post(
            () -> {
              if (success) {
                Toast.makeText(context, R.string.schedule_followup_ok, Toast.LENGTH_SHORT).show();
              } else {
                Toast.makeText(
                        context,
                        context.getString(R.string.schedule_followup_fail, error),
                        Toast.LENGTH_LONG)
                    .show();
              }
            });
  }
}
