package com.radiant.mesh;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.radiant.mesh.data.PreferenceManager;

/**
 * Minimal Settings Screen.
 * Allows toggling "Long Range Mode".
 * Note: Changes here ideally trigger a restart of the Advertiser in a full production app.
 */
public class SettingsActivity extends AppCompatActivity {

    private PreferenceManager preferenceManager;
    private Switch switchLongRange;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferenceManager = new PreferenceManager(this);

        // Assume R.id.switch_long_range exists in layout
        switchLongRange = findViewById(R.id.switch_long_range);

        // Initialize state
        if (switchLongRange != null) {
            switchLongRange.setChecked(preferenceManager.isLongRangeMode());

            switchLongRange.setOnCheckedChangeListener((buttonView, isChecked) -> {
                preferenceManager.setLongRangeMode(isChecked);
                Toast.makeText(SettingsActivity.this, 
                        "Restart Mesh to apply changes.", 
                        Toast.LENGTH_SHORT).show();
            });
        }
    }
}