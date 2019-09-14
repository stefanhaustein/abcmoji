package org.kobjects.abcnotation.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.kobjects.abcnotation.SampleManager;
import org.kobjects.abcnotation.AbcScore;
import org.kobjects.abcnotation.SoundLicenses;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;



public class MainActivity extends Activity {

  String[] OPTIONS = {
      "c2 d2 e2 c2| c2 d2 e2 c2| e2 f2 g4| e2 f2 g4| ga gf e2 c2| ga gf e2 c2|c2 G2 c4| c2 G2 c4|",
      "\uD83D\uDD2B\uD83D\uDD2B\uD83D\uDD2B\uD83D\uDCA5",
      "http://www.lotro-abc.com/abc/beefur.abc",
  //    "http://www.lotro-abc.com/abc/haraxe.abc",
    //  "http://www.lotro-abc.com/abc/ateam.abc",
      "About"};


  SampleManager sampleManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    sampleManager = new SampleManager(this);

    LinearLayout mainLayout = new LinearLayout(this);
    mainLayout.setOrientation(LinearLayout.VERTICAL);

    LinearLayout controlLayout = new LinearLayout(this);
    Spinner urlSpinner = new Spinner(this);
    ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(
        this, android.R.layout.simple_spinner_item, OPTIONS);
    spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    urlSpinner.setAdapter(spinnerArrayAdapter);
    controlLayout.addView(urlSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

    Button button = new Button(this);
    button.setText("Play");
    controlLayout.addView(button);

    mainLayout.addView(controlLayout);

    EditText editText = new EditText(this);
    mainLayout.addView(editText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    editText.setGravity(Gravity.LEFT | Gravity.TOP);

    button.setOnClickListener(view -> {
      new AbcScore(sampleManager, editText.getText().toString()).play();
    });

    urlSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String url = OPTIONS[position];
        if ("About".equals(url)) {
          StringBuilder sb = new StringBuilder("% Android AbcNotation Demo\n");
          sb.append("% (C) 2019 Stefan Haustein\n\n");
          sb.append("% Sound effect licenses:\n\n");
          for (String license : SoundLicenses.LICENSES) {
            sb.append(license.substring(0, 3)).append("%").append(license.substring(2)).append('\n');
          }
          editText.setText(sb.toString());
        } else if (!url.startsWith("http")) {
          editText.setText(url);
        } else {
          new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try {
              BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()));
              while (true) {
                int c = reader.read();
                if (c <= 0) {
                  break;
                }
                sb.append((char) c);
              }
              reader.close();
            } catch (Exception e) {
              sb.append(e.toString());
            }
            runOnUiThread(() -> editText.setText(sb.toString()));
          }).start();
        }
      }

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });



    setContentView(mainLayout);
  }
}