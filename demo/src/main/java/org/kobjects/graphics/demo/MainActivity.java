package org.kobjects.graphics.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import org.kobjects.sound.SampleManager;
import org.kobjects.sound.Sound;


public class MainActivity extends Activity {

  SampleManager sampleManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    sampleManager = new SampleManager(this);

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    EditText editText = new EditText(this);
    layout.addView(editText);

    Button button = new Button(this);
    button.setText("Play notes");
    layout.addView(button);

    button.setOnClickListener(view -> new Sound(sampleManager, editText.getText().toString()).play());

    setContentView(layout);
  }
}