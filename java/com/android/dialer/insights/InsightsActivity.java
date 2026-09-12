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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.dialer.R;
import com.bestrom.agent.brain.BestromBrainClient;
import com.bestrom.agent.brain.BrainProxyContract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Period summary, relationship scores, days-since, optional brain narrative. */
public class InsightsActivity extends Activity {

  private TextView summaryView;
  private TextView narrativeView;
  private ListView listView;
  private Spinner periodSpinner;
  private Button brainButton;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  @Nullable private CallInsightEngine.PeriodSummary lastSummary;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_call_insights);
    setTitle(R.string.insights_title);
    summaryView = findViewById(R.id.insights_summary);
    narrativeView = findViewById(R.id.insights_narrative);
    listView = findViewById(R.id.insights_list);
    periodSpinner = findViewById(R.id.insights_period);
    brainButton = findViewById(R.id.insights_brain);
    ArrayAdapter<CharSequence> adapter =
        ArrayAdapter.createFromResource(
            this, R.array.insights_periods, android.R.layout.simple_spinner_dropdown_item);
    periodSpinner.setAdapter(adapter);
    periodSpinner.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(
              android.widget.AdapterView<?> parent, View view, int position, long id) {
            reload();
          }

          @Override
          public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    brainButton.setOnClickListener(v -> runBrain());
    findViewById(R.id.insights_privacy).setVisibility(View.VISIBLE);
    reload();
  }

  @Override
  protected void onDestroy() {
    io.shutdownNow();
    super.onDestroy();
  }

  private long windowMs() {
    int pos = periodSpinner.getSelectedItemPosition();
    if (pos == 0) return TimeUnit.DAYS.toMillis(1);
    if (pos == 1) return TimeUnit.DAYS.toMillis(7);
    return TimeUnit.DAYS.toMillis(30);
  }

  private void reload() {
    summaryView.setText(R.string.insights_loading);
    io.execute(
        () -> {
          CallInsightEngine.PeriodSummary s = CallInsightEngine.compute(this, windowMs());
          main.post(() -> bind(s));
        });
  }

  private void bind(CallInsightEngine.PeriodSummary s) {
    lastSummary = s;
    long mins = s.talkMs / 60000L;
    summaryView.setText(
        getString(
            R.string.insights_summary_fmt, s.totalCalls, mins, s.missed));
    List<String> rows = new ArrayList<>();
    for (CallInsightEngine.ContactInsight c : s.top) {
      rows.add(
          getString(
              R.string.insights_row_fmt,
              c.label,
              c.bucket.name(),
              c.score,
              c.daysSinceLast,
              c.callCount));
    }
    listView.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
  }

  private void runBrain() {
    if (lastSummary == null) return;
    brainButton.setEnabled(false);
    narrativeView.setText(R.string.insights_brain_working);
    final String json = CallInsightEngine.toAggregateJson(lastSummary);
    io.execute(
        () -> {
          String text;
          BestromBrainClient client = new BestromBrainClient(getApplicationContext());
          try {
            if (!client.bind() || !client.isReady()) {
              text = getString(R.string.insights_brain_not_ready);
            } else {
              Bundle r =
                  client.complete(
                      "You help the user understand call habits. Be brief (2-4 sentences)."
                          + " Data is anonymised aggregates from this phone only."
                          + " Mention who they may have neglected (high score, many days_ago)"
                          + " and who they talk to most. Not medical or legal advice.",
                      json);
              if (r != null && r.getBoolean(BrainProxyContract.KEY_OK, false)) {
                text = r.getString(BrainProxyContract.KEY_TEXT);
              } else {
                text =
                    r != null
                        ? r.getString(BrainProxyContract.KEY_ERROR)
                        : getString(R.string.insights_brain_failed);
              }
            }
          } catch (Exception e) {
            text = getString(R.string.insights_brain_failed);
          } finally {
            client.unbind();
          }
          final String out = text;
          main.post(
              () -> {
                narrativeView.setText(out);
                brainButton.setEnabled(true);
              });
        });
  }
}
