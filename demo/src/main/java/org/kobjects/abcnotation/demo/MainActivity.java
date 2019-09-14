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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;



public class MainActivity extends Activity {

  String[] URLS = {"Frere Jaques","http://www.lotro-abc.com/abc/haraxe.abc","http://www.lotro-abc.com/abc/ateam.abc","http://www.lotro-abc.com/abc/beefur.abc"};

  String SAMPLE_SONG = "% Generated more or less automatically by swtoabc by Erich Rickheit KSC\n"
      + "X:1\n"
      + "T:Frere Jacques\n"
      + "M:4/4\n"
      + "L:1/4\n"
      + "K:C\n"
      + "c d e c| c d e c| e f g2| e f g2| g/2a/2 g/2f/2 e c| g/2a/2 g/2f/2 e c|c G c2| c G c2|\n";


  SampleManager sampleManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    sampleManager = new SampleManager(this);

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    Spinner urlSpinner = new Spinner(this);
    ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(
        this, android.R.layout.simple_spinner_item, URLS);
    spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    urlSpinner.setAdapter(spinnerArrayAdapter);
    layout.addView(urlSpinner);

    EditText editText = new EditText(this);
    layout.addView(editText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    editText.setText(SAMPLE_SONG);
    editText.setGravity(Gravity.LEFT | Gravity.TOP);

    urlSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0) {
          editText.setText(SAMPLE_SONG);
          return;
        }
        new Thread(() -> {
          StringBuilder sb = new StringBuilder();
          try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(URLS[position]).openStream()));
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

      @Override
      public void onNothingSelected(AdapterView<?> parent) {

      }
    });


    Button button = new Button(this);
    button.setText("Play notes");
    layout.addView(button);

    button.setOnClickListener(view -> {
      new AbcScore(sampleManager, editText.getText().toString()).play();
    });

    setContentView(layout);
  }
}