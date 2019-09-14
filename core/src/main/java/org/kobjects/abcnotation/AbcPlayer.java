package org.kobjects.abcnotation;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class AbcPlayer {
  private final AbcScore score;
  private final AbcTokenizer tokenizer;
  private DelayQueue<Note> noteQueue = new DelayQueue<>();
  private long timeMs;
  private long chordStartTimeMs;
  private long[] chordEndTimeMs = new long[10];
  private int chordIndex = -1;
  private long[] voiceTimeMS = new long [10];
  private int voiceIndex = 0;

  AbcPlayer(AbcScore score) {
    this.score = score;
    this.tokenizer = new AbcTokenizer(score.abcData);
  }

  private void processMeter() {
    float meter;
    if (tokenizer.c == 'C') {
      meter = 1;
    } else {
      meter = tokenizer.consumeFraction();
    }
    if (!score.explicitLength && meter < 0.75) {
      score.baseLength = 1f/16f;
    }
    score.meterDecimal = 1f/tokenizer.divisor;
    score.explicitLength = true; // prevent changes by M in body.
    tokenizer.skipToLineEnd();
  }

  private void processLength() {
    score.baseLength = tokenizer.consumeFraction();
    score.explicitLength = true;
    tokenizer.skipToLineEnd();
  }

  private void processTempo() {
    tokenizer.skipSpace();
    float beforeEq = 0;
    boolean eqFound = false;
    while (tokenizer.tokenType != AbcTokenizer.TokenType.END && tokenizer.tokenType != AbcTokenizer.TokenType.NEWLINE) {
      if (tokenizer.c == '=') {
        eqFound = true;
        break;
      }
      beforeEq += tokenizer.consumeFraction();
      tokenizer.skipSpace();
    }
    if (eqFound) {
      tokenizer.next();
      score.bpm =  beforeEq * tokenizer.consumeNumber();
    } else {
      score.bpm = beforeEq;
    }
  }

  private void processControl(char controlType) {
    tokenizer.next();
    switch (controlType) {
      case 'L':
        processLength();
        break;
      case 'M':
        processMeter();
        break;
      case 'Q':
        processTempo();
        break;
      case 'V':
        voiceTimeMS[voiceIndex] = timeMs;
        voiceIndex = (int) tokenizer.consumeNumber();
        timeMs = voiceTimeMS[voiceIndex];
        tokenizer.skipToLineEnd();
        break;

      default:
        tokenizer.skipToLineEnd();
        break;
    }
  }

  void play() throws InterruptedException {
    timeMs = System.currentTimeMillis();
    for (int i = 0; i < voiceTimeMS.length; i++) {
      voiceTimeMS[i] = timeMs;
    }
    tokenizer.next();

    new Thread(() -> {
      while (true) {
        try {
          Note note = noteQueue.take();
          note.play();
        } catch (InterruptedException e) {
          throw new RuntimeException();
        }
      }
    }).start();

    while (tokenizer.tokenType != AbcTokenizer.TokenType.END) {
      switch (tokenizer.tokenType) {
        case NEWLINE:
        case SPACE:
          // Skip
          tokenizer.next();
          break;
        case CONTROL:
          processControl(tokenizer.c);
          break;
        case OPEN_BRACKET:
          chordIndex = 0;
          chordStartTimeMs = timeMs;
          tokenizer.next();
          break;
        case CLOSE_BRACKET:
          for (int i = 0; i < chordIndex; i++) {
            timeMs = Math.min(timeMs, chordEndTimeMs[i]);
          }
          chordIndex = -1;
          tokenizer.next();
          break;
        case SHARP:
        case FLAT:
        case LETTER:
          if (chordIndex == -1) {
            processNote();
          } else {
            timeMs = chordStartTimeMs;
            //timeMs = Math.max(chordEndTimeMs[chordIndex], timeMs);
            processNote();
            chordEndTimeMs[chordIndex++] = timeMs;
            //timeMs = chordStartTimeMs;
          }
          break;

        case OTHER: {
          String name = Integer.toHexString(tokenizer.codePoint);
          int length = score.manager.getDurationMs(name);
          if (length != 0) {
            noteQueue.offer(new Note(timeMs, name, length));
          }
          timeMs += length;
          tokenizer.next();
          break;
        }
        default:
          System.out.println("Unexpected symbol: " + tokenizer.tokenType);
          tokenizer.next();
      }
    }
  }

  private void processNote() throws InterruptedException {
    float note = 0;
    do {
      switch (tokenizer.tokenType) {
        case FLAT:
          note--;
          tokenizer.next();
          continue;
        case SHARP:
          note++;
          tokenizer.next();
          continue;
      }
    } while (false);

    if (tokenizer.tokenType != AbcTokenizer.TokenType.LETTER) {
      throw new RuntimeException("Letter expected after sharp or flatr");
    }
    boolean rest = false;
    switch (tokenizer.c) {
      case 'C':
        note -= 9;
        break;
      // C#
      case 'D':
        note -= 7;
        break;
      // D#
      case 'E':
        note -= 5;
        break;
      case 'F':
        note -= 4;
        break;
      // F#
      case 'G':
        note -= 2;
        break;
      // G#
      case 'A':
        note += 0;
        break;  // 440Hz
      // A#
      case 'B':
        note += 2;
        break;
      case 'c':
        note += 3;
        break;
      // c#
      case 'd':
        note += 5;
        break;
      // d#
      case 'e':
        note += 7;
        break;
      case 'f':
        note += 8;
        break;
      // f#
      case 'g':
        note += 10;
        break;
      // g#
      case 'a':
        note += 12;
        break;
      // a#
      case 'b':
        note += 14;
        break;
      case 'x':
      case 'z':
        rest = true;
        break;
      default:
    }

    while(true) {
      switch(tokenizer.next()) {
        case UP:
          note += 12;
          continue;
        case DOWN:
          note -= 12;
          continue;
      }
      break;
    }

    float duration = score.baseLength * tokenizer.consumeFraction() * 60 / score.bpm;
    float frequency = (float) (440 * Math.pow(2, note/12f));

    int durationMs = (int) (duration*1000);

    if (!rest) {
      noteQueue.offer(new Note(timeMs, frequency, durationMs));
    }
    timeMs += durationMs;
  }


  class Note implements Delayed {
    final long scheduledTimeMs;
    final String name;
    final float frequency;
    final int length;

    Note (long scheduledTimeMs, String name, int length) {
      this.scheduledTimeMs = scheduledTimeMs;
      this.name = name;
      this.length = length;
      this.frequency = -1;
    }

    Note (long scheduledTimeMs, float frequency, int length) {
      this.scheduledTimeMs = scheduledTimeMs;
      this.frequency = frequency;
      this.length = length;
      this.name = null;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(scheduledTimeMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    void play() {
      if (name == null) {
        score.toneGenerator.play(frequency, length);
      } else {
        new Thread(() -> score.manager.play(name, null)).start();
      }
    }
  }
}
