/*
 * Copyright (C) 2014 The CyanogenMod Project
 * Copyright (C) 2023 The LineageOS Project
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
 * limitations under the License
 */

package com.android.dialer.lookup;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.android.dialer.R;

public class LookupSettingsFragment extends PreferenceFragmentCompat
    implements Preference.OnPreferenceChangeListener {

  private static final String KEY_ENABLE_FORWARD_LOOKUP = "enable_forward_lookup";
  private static final String KEY_ENABLE_REVERSE_LOOKUP = "enable_reverse_lookup";
  private static final String KEY_FORWARD_LOOKUP_PROVIDER = "forward_lookup_provider";
  private static final String KEY_REVERSE_LOOKUP_PROVIDER = "reverse_lookup_provider";
  private static final String KEY_CUSTOM_LOOKUP_URL = "custom_lookup_url";
  private static final String KEY_CUSTOM_LOOKUP_NAME_PATH = "custom_lookup_name_path";
  private static final String KEY_CUSTOM_LOOKUP_HEADER_NAME = "custom_lookup_header_name";
  private static final String KEY_CUSTOM_LOOKUP_HEADER_VALUE = "custom_lookup_header_value";

  private SwitchPreferenceCompat enableForwardLookup;
  private SwitchPreferenceCompat enableReverseLookup;
  private ListPreference forwardLookupProvider;
  private ListPreference reverseLookupProvider;
  private EditTextPreference customLookupUrl;
  private EditTextPreference customLookupNamePath;
  private EditTextPreference customLookupHeaderName;
  private EditTextPreference customLookupHeaderValue;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getPreferenceManager().setStorageDeviceProtected();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
    addPreferencesFromResource(R.xml.lookup_settings);

    enableForwardLookup = (SwitchPreferenceCompat) findPreference(KEY_ENABLE_FORWARD_LOOKUP);
    enableReverseLookup = (SwitchPreferenceCompat) findPreference(KEY_ENABLE_REVERSE_LOOKUP);

    enableForwardLookup.setOnPreferenceChangeListener(this);
    enableReverseLookup.setOnPreferenceChangeListener(this);

    forwardLookupProvider = (ListPreference) findPreference(KEY_FORWARD_LOOKUP_PROVIDER);
    reverseLookupProvider = (ListPreference) findPreference(KEY_REVERSE_LOOKUP_PROVIDER);

    forwardLookupProvider.setOnPreferenceChangeListener(this);
    reverseLookupProvider.setOnPreferenceChangeListener(this);

    customLookupUrl = (EditTextPreference) findPreference(KEY_CUSTOM_LOOKUP_URL);
    customLookupNamePath = (EditTextPreference) findPreference(KEY_CUSTOM_LOOKUP_NAME_PATH);
    customLookupHeaderName = (EditTextPreference) findPreference(KEY_CUSTOM_LOOKUP_HEADER_NAME);
    customLookupHeaderValue = (EditTextPreference) findPreference(KEY_CUSTOM_LOOKUP_HEADER_VALUE);
    customLookupUrl.setOnPreferenceChangeListener(this);
    customLookupNamePath.setOnPreferenceChangeListener(this);
    customLookupHeaderName.setOnPreferenceChangeListener(this);
    customLookupHeaderValue.setOnPreferenceChangeListener(this);
  }

  @Override
  public void onResume() {
    super.onResume();

    restoreLookupProviderSwitches();
    restoreLookupProviders();
    restoreCustomLookupSettings();
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    Context context = getContext();

    if (preference == enableForwardLookup) {
      LookupSettings.setForwardLookupEnabled(context, (Boolean) newValue);
    } else if (preference == enableReverseLookup) {
      LookupSettings.setReverseLookupEnabled(context, (Boolean) newValue);
    } else if (preference == forwardLookupProvider) {
      LookupSettings.setForwardLookupProvider(context, (String) newValue);
    } else if (preference == reverseLookupProvider) {
      LookupSettings.setReverseLookupProvider(context, (String) newValue);
      showCustomLookupSettings(LookupSettings.RLP_CUSTOM.equals(newValue));
    } else if (preference == customLookupUrl) {
      LookupSettings.setCustomLookupUrl(context, (String) newValue);
    } else if (preference == customLookupNamePath) {
      LookupSettings.setCustomLookupNamePath(context, (String) newValue);
    } else if (preference == customLookupHeaderName) {
      LookupSettings.setCustomLookupHeaderName(context, (String) newValue);
    } else if (preference == customLookupHeaderValue) {
      LookupSettings.setCustomLookupHeaderValue(context, (String) newValue);
    }

    return true;
  }

  private void restoreCustomLookupSettings() {
    Context context = getContext();
    customLookupUrl.setText(LookupSettings.getCustomLookupUrl(context));
    customLookupNamePath.setText(LookupSettings.getCustomLookupNamePath(context));
    customLookupHeaderName.setText(LookupSettings.getCustomLookupHeaderName(context));
    customLookupHeaderValue.setText(LookupSettings.getCustomLookupHeaderValue(context));
    showCustomLookupSettings(
        LookupSettings.RLP_CUSTOM.equals(LookupSettings.getReverseLookupProvider(context)));
  }

  /** The endpoint settings mean nothing unless the custom provider is the one selected. */
  private void showCustomLookupSettings(boolean visible) {
    customLookupUrl.setVisible(visible);
    customLookupNamePath.setVisible(visible);
    customLookupHeaderName.setVisible(visible);
    customLookupHeaderValue.setVisible(visible);
  }

  private void restoreLookupProviderSwitches() {
    Context context = getContext();

    enableForwardLookup.setChecked(LookupSettings.isForwardLookupEnabled(context));
    enableReverseLookup.setChecked(LookupSettings.isReverseLookupEnabled(context));
  }

  private void restoreLookupProviders() {
    Context context = getContext();

    restoreLookupProvider(forwardLookupProvider, LookupSettings.getForwardLookupProvider(context));
    restoreLookupProvider(reverseLookupProvider, LookupSettings.getReverseLookupProvider(context));
  }

  private void restoreLookupProvider(ListPreference pref, String provider) {
    Context context = getContext();

    if (pref.getEntries().length < 1) {
      pref.setEnabled(false);
      return;
    }

    if (provider == null) {
      setDefaultProvider(pref);
    } else {
      String[] providers = new String[0];
      if (pref == forwardLookupProvider) {
        providers = getContext().getResources().getStringArray(R.array.forward_lookup_providers);
      } else if (pref == reverseLookupProvider) {
        providers = getContext().getResources().getStringArray(R.array.reverse_lookup_providers);
      }

      boolean found = false;
      for (String p : providers) {
        if (p.equals(provider)) {
          found = true;
          break;
        }
      }

      if (!found) {
        setDefaultProvider(pref);
      } else {
        pref.setValue(provider);
      }
    }
  }

  private void setDefaultProvider(ListPreference pref) {
    Context context = getContext();
    if (pref == forwardLookupProvider) {
      LookupSettings.setForwardLookupProvider(context, LookupSettings.FLP_DEFAULT);
    } else if (pref == reverseLookupProvider) {
      LookupSettings.setReverseLookupProvider(context, LookupSettings.RLP_DEFAULT);
    }
  }
}
