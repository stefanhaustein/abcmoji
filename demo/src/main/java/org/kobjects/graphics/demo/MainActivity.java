package org.kobjects.graphics.demo;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
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
    layout.addView(editText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
    editText.setText(
        "% Generated more or less automatically by swtoabc by Erich Rickheit KSC\n"
    + "X:1\n"
    + "T:Frere Jacques\n"
    + "M:4/4\n"
    + "L:1/4\n"
    + "K:C\n"
    + "c d e c| c d e c| e f g2| e f g2| g/2a/2 g/2f/2 e c| g/2a/2 g/2f/2 e c|c G c2| c G c2|\n");


    Button button = new Button(this);
    button.setText("Play notes");
    layout.addView(button);

    button.setOnClickListener(view -> {
      new Sound(sampleManager, editText.getText().toString()).play();
    });

    setContentView(layout);
  }
}