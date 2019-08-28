package org.kobjects.sound;


import org.kobjects.atg.ToneGenerator;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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
        new Voice().playImpl();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    } else {
      new Thread(() -> play(true)).start();
    }
  }

  enum TokenType {
    FLAT, SHARP, CONTROL, END, NUMBER, LETTER, FRACTION, OTHER, OPEN_BRACKET, CLOSE_BRACKET, NEWLINE, SPACE, UP, DOWN;
  }

  class AbcTokenizer {
    int pos;
    float nVal;
    char c;
    int codePoint;
    TokenType tokenType;
    float dividend;
    float divisor;

    TokenType next() {
      int len = abcData.length();

      // Comment loop
      while (true) {
        // EOF recognition
        if (pos >= len) {
          return tokenType = TokenType.END;
        }

        codePoint = c = abcData.charAt(pos++);

        if (c != '%') {
          break;
        }
        while (pos < len && abcData.charAt(pos) != '\n') {
          pos++;
        }
      }

      boolean lineStart = pos == 1 || abcData.charAt(pos - 2) == '\n';
      if (lineStart && c >= 'A' && c <= 'Z' && pos < len && abcData.charAt(pos) == ':') {
        pos++;
        return tokenType = TokenType.CONTROL;
      }

      switch (c) {
        case ' ': return tokenType = TokenType.SPACE;
        case '\n': return tokenType = TokenType.NEWLINE;
        case '^': return tokenType = TokenType.SHARP;
        case '_': return tokenType = TokenType.FLAT;
        case '/': return tokenType = TokenType.FRACTION;
        case '[': return tokenType = TokenType.OPEN_BRACKET;
        case ']': return tokenType = TokenType.CLOSE_BRACKET;
        case '\'': return tokenType = TokenType.UP;
        case ',': return tokenType = TokenType.DOWN;
      }

      if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
        return tokenType = TokenType.LETTER;
      }

      if (c >= '0' && c <= '9') {
        nVal = c - '0';
        while (pos < len && abcData.charAt(pos) >= '0' && abcData.charAt(pos) <= '9') {
          nVal = nVal * 10 + (abcData.charAt(pos++) - '0');
        }
        return tokenType = TokenType.NUMBER;
      }

      pos--;
      codePoint = Character.codePointAt(abcData, pos);
      pos += Character.charCount(codePoint);
      return tokenType = TokenType.OTHER;
    }

    float consumeNumber() {
      if (tokenType != TokenType.NUMBER) {
        throw new IllegalStateException("Number token expected but got " + tokenType);
      }
      float result = nVal;
      next();
      return result;
    }

    float consumeFraction() {
      dividend = tokenType == TokenType.NUMBER ? consumeNumber() : 1;
      if (tokenType == TokenType.FRACTION) {
        next();
        divisor = (tokenType == TokenType.NUMBER ? consumeNumber() : 2);
      } else {
        divisor = 1;
      }
      return dividend / divisor;
    }

    void skipToLineEnd() {
      while (tokenType != TokenType.NEWLINE && tokenType != TokenType.END) {
        next();
      }
    }

    void skipSpace() {
      while (tokenType == TokenType.SPACE) {
        next();
      }
    }
  }

  class Voice {
    AbcTokenizer tokenizer = new AbcTokenizer();

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
      System.out.println("base length: " + baseLength);
      tokenizer.skipToLineEnd();
    }

    private void parseTempo() {
      tokenizer.skipSpace();
      float beforeEq = 0;
      boolean eqFound = false;
      while (tokenizer.tokenType != TokenType.END && tokenizer.tokenType != TokenType.NEWLINE) {
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

        default:
          do {
            tokenizer.next();
          } while (tokenizer.tokenType != TokenType.NEWLINE && tokenizer.tokenType != TokenType.END);
          break;
      }
    }

    private void playImpl() throws InterruptedException {
      tokenizer.next();
      boolean nowait = false;
      int count = 0;
      int firstTime = 0;
      while (tokenizer.tokenType != TokenType.END) {
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
            nowait = true;
            count = 0;
            tokenizer.next();
            break;
          case CLOSE_BRACKET:
            nowait = false;
            if (count > 1){
              Thread.sleep(firstTime);
            }
            break;
          case SHARP:
          case FLAT:
          case LETTER:
            int time = processNote(nowait);
            if (count == 0) {
              firstTime = time;
            }
            count++;
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
        }
      }
    }

    private int processNote(boolean nowait) throws InterruptedException {
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

      if (tokenizer.tokenType != TokenType.LETTER) {
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

      do {
        switch(tokenizer.next()) {
          case UP:
            note += 12;
            continue;
          case DOWN:
            note -= 12;
            continue;
        }
      } while (false);

      float duration = baseLength * tokenizer.consumeFraction() * 60 / bpm;
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
}
