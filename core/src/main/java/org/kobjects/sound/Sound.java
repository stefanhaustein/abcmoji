package org.kobjects.sound;


import org.kobjects.atg.ToneGenerator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// TODO: It will make sense to factor out play state into an inner class, in particular with multiple
public class Sound {

  final SampleManager manager;
  float baseLength = 1f/8f;
  boolean explicitLength = false;
  ToneGenerator toneGenerator = new ToneGenerator();
  String[] lines;
  String line;
  int lineNumber;
  int pos;
  int len;
  float dividend;
  float divisor;
  float bpmMultiplier = 1f/4f;
  float bpm = 120/4;

  public Sound(SampleManager manager, String abcData) {
    this.manager = manager;
    lines = abcData.split("\n");
  }


  public void play() {
    play(false);
  }

  public void play(boolean blocking) {
    if (blocking) {
      try {
        playImpl();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    } else {
      new Thread(() -> play(true)).start();
    }
  }

  private void playImpl() throws InterruptedException {
    for (lineNumber = 0; lineNumber < lines.length; lineNumber++) {
      line = lines[lineNumber];
      len = line.length();
      pos = 0;
      if (len > 1 && line.charAt(0) >= 'A' && line.charAt(1) <= 'Z' && line.charAt(1) == ':') {
        pos += 2;
        processCommand(line.charAt(0), line.substring(2).trim());
      } else {
        boolean nowait = false;
        int count = 0;
        int firstTime = 0;
        while (pos < len && line.charAt(pos) != '%') {
          switch (line.charAt(pos)) {
            case '[':
              nowait = true;
              count = 0;
              pos++;
              continue;
            case ']':
              nowait = false;
              if (count > 1){
                Thread.sleep(firstTime);
              }
              pos++;
              continue;
          }
          int time = processNote(nowait);
          if (count == 0) {
            firstTime = time;
          }
          count++;
        }
      }
    }
  }


  private float parseNumber() {
    dividend = 0;
    int p0 = pos;
    while (pos < len && line.charAt(pos) >= '0' && line.charAt(pos) <= '9') {
      dividend = dividend * 10 + (line.charAt(pos++) - '0');
    }
    if (pos == p0) {
      dividend = 1;
    }
    if (pos >= len || line.charAt(pos) != '/') {
      divisor = 1;
    } else {
      divisor = 0;
      pos++;
      int p1 = pos;
      while (pos < len && line.charAt(pos) >= '0' && line.charAt(pos) <= '9') {
        divisor = divisor * 10 + (line.charAt(pos++) - '0');
      }
      if (p1 == p0) {
        divisor = 2;
      }
    }
    return dividend/divisor;
  }

  void skipSpace() {
    while (pos < len && line.charAt(pos) == ' ') {
      pos++;
    }
  }

  private void parseTempo() {
    skipSpace();
    float beforeEq = 0;
    boolean eqFound = false;
    while (pos < len) {
      if (line.charAt(pos) == '=') {
        eqFound = true;
        break;
      }
      beforeEq += parseNumber();
      skipSpace();
    }
    if (eqFound) {
      bpmMultiplier = beforeEq;
      skipSpace();
      bpm = parseNumber() * bpmMultiplier;
    } else {
      bpm = beforeEq * bpmMultiplier;
    }
  }

  private void processCommand(char command, String data) {
    switch (command) {
      case 'L':
        baseLength = parseNumber();
        explicitLength = true;
        System.out.println("base length: " + baseLength);
        break;
      case 'M':
        float meter = data.equals("C") || data.equals("C|") ? 1 : parseNumber();
        if (!explicitLength && meter < 0.75) {
          baseLength = 1f/16f;
        }
        bpmMultiplier = 1f/divisor;
        explicitLength = true; // prevent changes by M in body.
        break;
      case 'Q':
        parseTempo();
        break;

      default:
        System.out.println("Ignored command: " + line);
    }
  }

  private int processNote(boolean nowait) throws InterruptedException {
    float note = 0;
    while (pos < len) {
      switch (line.charAt(pos)) {
        case '_':
          note--;
          pos++;
          continue;
        case '^':
          note++;
          pos++;
          continue;
      }
      break;
    }

    if (pos >= len) {
      return 0;
    }

    final int codePoint = Character.codePointAt(line, pos);
    pos += Character.charCount(codePoint);
    boolean rest = false;
    switch (codePoint) {
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
        synchronized (this) {
          BlockingQueue<String> queue = new LinkedBlockingQueue<>();
          manager.play(
              Integer.toHexString(codePoint),
              () -> {
                queue.add("done");
            });
          queue.take();
          return 0;
        }
    }
    while (pos < len) {
      switch (line.charAt(pos)) {
        case '\'':
          note += 12;
          pos++;
          continue;
        case ',':
          note -= 12;
          pos++;
          continue;
      }
      break;
    }

    float duration = baseLength * parseNumber() * 60 / bpm;
    float frequency = (float) (440 * Math.pow(2, note/12f));

    int durationMs = (int) (duration*1000);
    if (!rest) {
      toneGenerator.play(frequency, durationMs);
    }
    if (!nowait) {
      Thread.sleep(durationMs);
    }
    return durationMs;
  }
}
