package org.kobjects.sound;

class AbcTokenizer {

  enum TokenType {
    FLAT, SHARP, CONTROL, END, NUMBER, LETTER, FRACTION, OTHER, OPEN_BRACKET, CLOSE_BRACKET, NEWLINE, SPACE, UP, DOWN;
  }

  int pos;
  float nVal;
  char c;
  int codePoint;
  TokenType tokenType;
  float dividend;
  float divisor;
  String abcData;

  AbcTokenizer(String abcData) {
    this.abcData = abcData;
  }

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
