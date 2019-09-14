package org.kobjects.abcnotation;


import org.kobjects.atg.ToneGenerator;


public class AbcScore {

  final SampleManager manager;
  float baseLength = 1f/8f;
  boolean explicitLength = false;
  ToneGenerator toneGenerator = new ToneGenerator();
  String abcData;
  float meterDecimal = 4f/4f;

  float bpm = 120/4;

  public AbcScore(SampleManager manager, String abcData) {
    this.manager = manager;
    this.abcData = abcData;
  }

  public void play() {
    play(false);
  }

  public AbcPlayer play(boolean blocking) {
    final AbcPlayer player = new AbcPlayer(this);
    if (blocking) {
      try {
        player.play();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    } else {
      new Thread(() -> {
        try {
          player.play();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }).start();
    }
    return player;
  }



}
