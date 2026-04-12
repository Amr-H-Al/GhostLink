package com.example.ghostlink;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.List;

public class SampleScamActivity extends AppCompatActivity {

    private static final class Sample {
        final String text;
        final String expectedLabel;

        Sample(String text, String expectedLabel) {
            this.text = text;
            this.expectedLabel = expectedLabel;
        }
    }

    private final List<Sample> samples = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_scam);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Sample Messages");
            }
        }

        buildSamples();

        ListView listView = findViewById(R.id.sampleList);
        listView.setAdapter(new SampleAdapter());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void buildSamples() {
        samples.add(new Sample(
                "URGENT: Your Chase bank account has been suspended. Verify immediately: http://chase-secure.xyz",
                "🚨 SCAM"));
        samples.add(new Sample(
                "IRS: You owe $3,200 in taxes. Failure to pay will result in arrest. Call 1-800-555-0199.",
                "🚨 SCAM"));
        samples.add(new Sample(
                "Hi Mom, I'm in trouble and need $500 via Zelle. Don't call, just send to 555-0187.",
                "🚨 SCAM"));
        samples.add(new Sample(
                "USPS: Your package is on hold. Pay $2.99 redelivery fee at: http://usps-parcel.com",
                "🚨 SCAM"));
        samples.add(new Sample(
                "Hey! Are you coming to the party on Saturday? Let me know if you need a ride.",
                "👍 SAFE"));
        samples.add(new Sample(
                "Your Amazon order #112-8374629 has shipped! Track at amazon.com/orders",
                "👍 SAFE"));
        samples.add(new Sample(
                "Security alert: An unrecognized device logged into your account. If this wasn't you, reset password.",
                "⚠️ RISKY"));
    }

    private class SampleAdapter extends ArrayAdapter<Sample> {
        SampleAdapter() {
            super(SampleScamActivity.this, 0, samples);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_sample, parent, false);
            }

            Sample s = getItem(position);
            if (s == null) return convertView;

            TextView tvMessage = convertView.findViewById(R.id.tvSampleMessage);
            TextView tvLabel   = convertView.findViewById(R.id.tvSampleLabel);

            tvMessage.setText(s.text);
            tvLabel.setText(s.expectedLabel);

            if (s.expectedLabel.contains("SCAM")) {
                tvLabel.setBackgroundColor(0xFFB71C1C);
            } else if (s.expectedLabel.contains("RISKY")) {
                tvLabel.setBackgroundColor(0xFFE65100);
            } else {
                tvLabel.setBackgroundColor(0xFF1B5E20);
            }

            return convertView;
        }
    }
}
