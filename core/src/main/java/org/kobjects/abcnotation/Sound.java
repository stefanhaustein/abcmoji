package org.kobjects.abcnotation;


import org.kobjects.atg.ToneGenerator;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Sound {

  final SampleManager manager;
  float baseLength = 1f/8f;
  boolean explicitLength = false;
  ToneGenerator toneGenerator = new ToneGenerator();
  String abcData;
  float bpmMultiplier = 1f/4f;
  float bpm = 120/4;

  public Sound(SampleManager manager, String abcData) {
    this.manager = manager;
    this.abcData = abcData;
  }

  public void play() {
    play(false);
  }

  public void play(boolean blocking) {
    if (blocking) {
      try {
        new Player().play();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    } else {
      new Thread(() -> play(true)).start();
    }
  }

  class Note implements Delayed {
    final long scheduledTimeMs;
    final float frequency;
    final int length;

    Note (long scheduledTimeMs, float frequency, int length) {
      this.scheduledTimeMs = scheduledTimeMs;
      this.frequency = frequency;
      this.length = length;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(scheduledTimeMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }
  }


  class Player {
    AbcTokenizer tokenizer = new AbcTokenizer(abcData);
    DelayQueue<Note> noteQueue = new DelayQueue<>();
    long timeMs;
    long chordStartTimeMs;
    long[] chordEndTimeMs = new long[10];
    int chordIndex = -1;
    long[] voiceTimeMS = new long [10];
    int voiceIndex = 0;

    private void processMeter() {
      float meter;
      if (tokenizer.c == 'C') {
        meter = 1;
      } else {
        meter = tokenizer.consumeFraction();
      }
      if (!explicitLength && meter < 0.75) {
        baseLength = 1f/16f;
      }
      bpmMultiplier = 1f/tokenizer.divisor;
      explicitLength = true; // prevent changes by M in body.
      tokenizer.skipToLineEnd();
    }

    private void processLength() {
      baseLength = tokenizer.consumeFraction();
      explicitLength = true;
      tokenizer.skipToLineEnd();
    }

    private void parseTempo() {
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
        bpmMultiplier = beforeEq;
        tokenizer.skipSpace();
        bpm = tokenizer.consumeNumber() * bpmMultiplier;
      } else {
        bpm = beforeEq * bpmMultiplier;
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
          parseTempo();
          break;
        case 'V':
          voiceTimeMS[voiceIndex] = timeMs;
          voiceIndex = (int) tokenizer.consumeNumber();
          timeMs = voiceTimeMS[voiceIndex];
          do {
            tokenizer.next();
          } while (tokenizer.tokenType != AbcTokenizer.TokenType.NEWLINE && tokenizer.tokenType != AbcTokenizer.TokenType.END);
          break;

        default:
          do {
            tokenizer.next();
          } while (tokenizer.tokenType != AbcTokenizer.TokenType.NEWLINE && tokenizer.tokenType != AbcTokenizer.TokenType.END);
          break;
      }
    }

    private void play() throws InterruptedException {
      timeMs = System.currentTimeMillis();
      for (int i = 0; i < voiceTimeMS.length; i++) {
        voiceTimeMS[i] = timeMs;
      }
      tokenizer.next();

      new Thread(() -> {
        while (true) {
          try {
            Note note = noteQueue.take();
            toneGenerator.play(note.frequency, note.length);
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
            chordIndex = -1;
            timeMs = chordEndTimeMs[0];
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

          case OTHER:
              BlockingQueue<String> queue = new LinkedBlockingQueue<>();
              manager.play(
                      Integer.toHexString(tokenizer.codePoint),
                      () -> {
                        queue.add("done");
                      });
              queue.take();
            tokenizer.next();
            break;

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

      float duration = baseLength * tokenizer.consumeFraction() * 60 / bpm;
      float frequency = (float) (440 * Math.pow(2, note/12f));

      int durationMs = (int) (duration*1000);

      if (!rest) {
        noteQueue.offer(new Note(timeMs, frequency, durationMs));
      }
      timeMs += durationMs;
    }
  }
}
