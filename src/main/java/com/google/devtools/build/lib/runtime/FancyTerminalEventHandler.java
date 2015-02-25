// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.runtime;

import com.google.common.base.Splitter;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.util.Pair;
import com.google.devtools.build.lib.util.io.AnsiTerminal;
import com.google.devtools.build.lib.util.io.OutErr;

import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An event handler for ANSI terminals which uses control characters to
 * provide eye-candy, reduce scrolling, and generally improve usability
 * for users running directly from the shell.
 *
 * <p/>
 * This event handler differs from a normal terminal because it only adds
 * control characters to stderr, not stdout.  All blaze status feedback
 * is sent to stderr, so adding control characters just to that stream gives
 * the benefits described above without modifying the normal output stream.
 * For commands like build that don't generate stdout output this doesn't
 * matter, but for commands like query and ide_build_info, inserting these
 * control characters in stdout invalidated their output.
 *
 * <p/>
 * The underlying streams may be either line-bufferred or unbuffered.
 * Normally each event will write out a sequence of output to a single
 * stream, and will end with a newline, which ensures a flush.
 * But care is required when outputting incomplete lines, or when mixing
 * output between the two different streams (stdout and stderr):
 * it may be necessary to explicitly flush the output in those cases.
 * However, we also don't want to flush too often; that can lead to
 * a choppy UI experience.
 */
public class FancyTerminalEventHandler extends BlazeCommandEventHandler {
  private static Logger LOG = Logger.getLogger(FancyTerminalEventHandler.class.getName());
  private static final Pattern progressPattern = Pattern.compile(
      // Match strings that look like they start with progress info:
      //   [42%] Compiling base/base.cc
      //   [1,442 / 23,476] Compiling base/base.cc
      "^\\[(?:(?:\\d\\d?\\d?%)|(?:[\\d+,]+ / [\\d,]+))\\] ");
  private static final Splitter LINEBREAK_SPLITTER = Splitter.on('\n');

  private final AnsiTerminal terminal;

  private final boolean useColor;
  private final boolean useCursorControls;
  private final boolean progressInTermTitle;
  public final int terminalWidth;

  private boolean terminalClosed = false;
  private boolean previousLineErasable = false;
  private int numLinesPreviousErasable = 0;

  public FancyTerminalEventHandler(OutErr outErr, BlazeCommandEventHandler.Options options) {
    super(outErr, options);
    this.terminal = new AnsiTerminal(outErr.getErrorStream());
    this.terminalWidth = (options.terminalColumns > 0 ? options.terminalColumns : 80);
    useColor = options.useColor();
    useCursorControls = options.useCursorControl();
    progressInTermTitle = options.progressInTermTitle;
  }

  @Override
  public void handle(Event event) {
    if (terminalClosed) {
      return;
    }
    if (!eventMask.contains(event.getKind())) {
      return;
    }
    
    try {
      boolean previousLineErased = false;
      if (previousLineErasable) {
        previousLineErased = maybeOverwritePreviousMessage();
      }
      switch (event.getKind()) {
        case PROGRESS:
        case START:
          {
            String message = event.getMessage();
            Pair<String,String> progressPair = matchProgress(message);
            if (progressPair != null) {
              progress(progressPair.getFirst(), progressPair.getSecond());
            } else {
              progress("INFO: ", message);
            }
            break;
          }
        case FINISH:
          {
            String message = event.getMessage();
            Pair<String,String> progressPair = matchProgress(message);
            if (progressPair != null) {
              String percentage = progressPair.getFirst();
              String rest = progressPair.getSecond();
              progress(percentage, rest + " DONE");
            } else {
              progress("INFO: ", message + " DONE");
            }
            break;
          }
        case PASS:
          progress("PASS: ", event.getMessage());
          break;
        case INFO:
          info(event);
          break;
        case ERROR:
        case FAIL:
        case TIMEOUT:
          // For errors, scroll the message, so it appears above the status
          // line, and highlight the word "ERROR" or "FAIL" in boldface red.
          errorOrFail(event);
          break;
        case WARNING:
          // For warnings, highlight the word "Warning" in boldface magenta,
          // and scroll it.
          warning(event);
          break;
        case SUBCOMMAND:
          subcmd(event);
          break;
        case STDOUT:
          if (previousLineErased) {
            terminal.flush();
          }
          previousLineErasable = false;
          super.handle(event);
          // We don't need to flush stdout here, because
          // super.handle(event) will take care of that.
          break;
        case STDERR:
          putOutput(event);
          break;
        default:
          // Ignore all other event types.
          break;
      }
    } catch (IOException e) {
      // The terminal shouldn't have IO errors, unless the shell is killed, which
      // should also kill the blaze client. So this isn't something that should
      // occur here; it will show up in the client/server interface as a broken
      // pipe.
      LOG.warning("Terminal was closed during build: " + e);
      terminalClosed = true;
    }
  }

  /**
   * Displays a progress message that may be erased by subsequent messages.
   *
   * @param  prefix   a short string such as "[99%] " or "INFO: ", which will be highlighted
   * @param  rest     the remainder of the message; may be multiple lines
   */
  private void progress(String prefix, String rest) throws IOException {
    previousLineErasable = true;

    if (progressInTermTitle) {
      int newlinePos = rest.indexOf('\n');
      if (newlinePos == -1) {
        terminal.setTitle(prefix + rest);
      } else {
        terminal.setTitle(prefix + rest.substring(0, newlinePos));
      }
    }

    if (useColor) {
      terminal.textGreen();
    }
    int prefixWidth = prefix.length();
    terminal.writeString(prefix);
    terminal.resetTerminal();
    if (showTimestamp) {
      String timestamp = timestamp();
      prefixWidth += timestamp.length();
      terminal.writeString(timestamp);
    }
    int numLines = 0;
    Iterator<String> lines = LINEBREAK_SPLITTER.split(rest).iterator();
    String firstLine = lines.next();
    terminal.writeString(firstLine);
    // Subtract one, because when the line length is the same as the terminal
    // width, the terminal doesn't line-advance, so we don't want to erase
    // two lines.
    numLines += (prefixWidth + firstLine.length() - 1) / terminalWidth + 1;
    crlf();
    while (lines.hasNext()) {
      String line = lines.next();
      terminal.writeString(line);
      crlf();
      numLines += (line.length() - 1) / terminalWidth + 1;
    }
    numLinesPreviousErasable = numLines;
  }

  /**
   * Try to match a message against the "progress message" pattern. If it
   * matches, return the progress percentage, and the rest of the message.
   * @param message the message to match
   * @return a pair containing the progress percentage, and the rest of the
   *    progress message, or null if the message isn't a progress message.
   */
  private Pair<String,String> matchProgress(String message) {
    Matcher m = progressPattern.matcher(message);
    if (m.find()) {
      return Pair.of(message.substring(0, m.end()), message.substring(m.end()));
    } else {
      return null;
    }
  }

  /**
   * Send the terminal controls that will put the cursor on the beginning
   * of the same line if cursor control is on, or the next line if not.
   * @returns True if it did any output; if so, caller is responsible for
   *          flushing the terminal if needed.
   */
  private boolean maybeOverwritePreviousMessage() throws IOException {
    if (useCursorControls && numLinesPreviousErasable != 0) {
      for (int i = 0; i < numLinesPreviousErasable; i++) {
        terminal.cr();
        terminal.cursorUp(1);
        terminal.clearLine();
      }
      return true;
    } else {
      return false;
    }
  }

  private void errorOrFail(Event event) throws IOException {
    previousLineErasable = false;
    if (useColor) {
      terminal.textRed();
      terminal.textBold();
    }
    terminal.writeString(event.getKind().toString() + ": ");
    if (useColor) {
      terminal.resetTerminal();
    }
    writeTimestampAndLocation(event);
    terminal.writeString(event.getMessage());
    terminal.writeString(".");
    crlf();
  }

  private void warning(Event warning) throws IOException {
    previousLineErasable = false;
    if (useColor) {
      terminal.textMagenta();
    }
    terminal.writeString("WARNING: ");
    terminal.resetTerminal();
    writeTimestampAndLocation(warning);
    terminal.writeString(warning.getMessage());
    terminal.writeString(".");
    crlf();
  }

  private void info(Event event) throws IOException {
    previousLineErasable = false;
    if (useColor) {
      terminal.textGreen();
    }
    terminal.writeString(event.getKind().toString() + ": ");
    terminal.resetTerminal();
    writeTimestampAndLocation(event);
    terminal.writeString(event.getMessage());
    // No period; info messages often end in '...'.
    crlf();
  }

  private void subcmd(Event subcmd) throws IOException {
    previousLineErasable = false;
    if (useColor) {
      terminal.textBlue();
    }
    terminal.writeString(">>>>> ");
    terminal.resetTerminal();
    writeTimestampAndLocation(subcmd);
    terminal.writeString(subcmd.getMessage());
    crlf();
  }

  /* Handle STDERR events. */
  private void putOutput(Event event) throws IOException {
    previousLineErasable = false;
    terminal.writeBytes(event.getMessageBytes());
/*
 * The following code doesn't work because buildtool.TerminalTestNotifier
 * writes ANSI-formatted text via this mechanism, one character at a time,
 * and if we try to insert additional ANSI sequences in between the characters
 * of another ANSI escape sequence, we screw things up. (?)
 * TODO(bazel-team): (2009) fix this.  TerminalTestNotifier should go via the Reporter
 * rather than via an AnsiTerminalWriter.
 */
//    terminal.resetTerminal();
//    writeTimestampAndLocation(event);
//    if (useColor) {
//      terminal.textNormal();
//    }
//    terminal.writeBytes(event.getMessageBytes());
//    terminal.resetTerminal();
  }

  /**
   * Add a carriage return, shifting to the next line on the terminal, while
   * guaranteeing that the terminal control codes don't cause any strange
   * effects.  Without the CR before the "\n", the "\n" can cause a line-break
   * moving text to the next line, where the new message will be generated.
   * Emitting a "CR" before means that the actual terminal controls generated
   * here are CR+CR+LF; the double-CR resets the terminal line state, which
   * prevents the potentially ugly formatting issue.
   */
  private void crlf() throws IOException {
    terminal.cr();
    terminal.writeString("\n");
  }

  private void writeTimestampAndLocation(Event event) throws IOException {
    if (showTimestamp) {
      terminal.writeString(timestamp());
    }
    if (event.getLocation() != null) {
      terminal.writeString(event.getLocation() + ": ");
    }
  }

  public void resetTerminal() {
    try {
      terminal.resetTerminal();
    } catch (IOException e) {
      LOG.warning("IO Error writing to user terminal: " + e);
    }
  }
}
