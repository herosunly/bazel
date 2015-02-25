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
package com.google.devtools.build.lib.testutil;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth.assert_;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A helper class for tests providing a simple interface for asserts.
 */
public class MoreAsserts {

  public static void assertContainsRegex(String regex, String actual) {
    assertThat(actual).containsMatch(regex);
  }

  public static void assertContainsRegex(String msg, String regex, String actual) {
    assertWithMessage(msg).that(actual).containsMatch(regex);
  }

  public static void assertNotContainsRegex(String regex, String actual) {
    assertThat(actual).doesNotContainMatch(regex);
  }

  public static void assertNotContainsRegex(String msg, String regex, String actual) {
    assertWithMessage(msg).that(actual).doesNotContainMatch(regex);
  }

  public static void assertMatchesRegex(String regex, String actual) {
    assertThat(actual).matches(regex);
  }

  public static void assertMatchesRegex(String msg, String regex, String actual) {
    assertWithMessage(msg).that(actual).matches(regex);
  }

  public static void assertNotMatchesRegex(String regex, String actual) {
    assertThat(actual).doesNotMatch(regex);
  }

  public static <T> void assertEquals(T expected, T actual, Comparator<T> comp) {
    assertThat(comp.compare(expected, actual)).isEqualTo(0);
  }

  public static <T> void assertContentsAnyOrder(
      Iterable<? extends T> expected, Iterable<? extends T> actual,
      Comparator<? super T> comp) {
    assertThat(actual).hasSize(Iterables.size(expected));
    int i = 0;
    for (T e : expected) {
      for (T a : actual) {
        if (comp.compare(e, a) == 0) {
          i++;
        }
      }
    }
    assertThat(actual).hasSize(i);
  }

  public static void assertGreaterThanOrEqual(long target, long actual) {
    assertThat(actual).isAtLeast(target);
  }

  public static void assertGreaterThanOrEqual(String msg, long target, long actual) {
    assertWithMessage(msg).that(actual).isAtLeast(target);
  }

  public static void assertGreaterThan(long target, long actual) {
    assertThat(actual).isGreaterThan(target);
  }

  public static void assertGreaterThan(String msg, long target, long actual) {
    assertWithMessage(msg).that(actual).isGreaterThan(target);
  }

  public static void assertLessThanOrEqual(long target, long actual) {
    assertThat(actual).isAtMost(target);
  }

  public static void assertLessThanOrEqual(String msg, long target, long actual) {
    assertWithMessage(msg).that(actual).isAtMost(target);
  }

  public static void assertLessThan(long target, long actual) {
    assertThat(actual).isLessThan(target);
  }

  public static void assertLessThan(String msg, long target, long actual) {
    assertWithMessage(msg).that(actual).isLessThan(target);
  }

  public static void assertEndsWith(String ending, String actual) {
    assertThat(actual).endsWith(ending);
  }

  public static void assertStartsWith(String prefix, String actual) {
    assertThat(actual).startsWith(prefix);
  }

  /**
   * Scans if an instance of given class is strongly reachable from a given
   * object.
   * <p>Runs breadth-first search in object reachability graph to check if
   * an instance of <code>clz</code> can be reached.
   * <strong>Note:</strong> This method can take a long time if analyzed
   * data structure spans across large part of heap and may need a lot of
   * memory.
   *
   * @param start object to start the search from
   * @param clazz class to look for
   */
  public static void assertInstanceOfNotReachable(
      Object start, final Class<?> clazz) {
    Predicate<Object> p = new Predicate<Object>() {
      @Override
      public boolean apply(Object obj) {
        return clazz.isAssignableFrom(obj.getClass());
      }
    };
    if (isRetained(p, start)) {
      assert_().fail("Found an instance of " + clazz.getCanonicalName() +
          " reachable from " + start.toString());
    }
  }

  private static final Field NON_STRONG_REF;

  static {
    try {
      NON_STRONG_REF = Reference.class.getDeclaredField("referent");
    } catch (SecurityException e) {
      throw new RuntimeException(e);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  static final Predicate<Field> ALL_STRONG_REFS = new Predicate<Field>() {
    @Override
    public boolean apply(Field field) {
      return NON_STRONG_REF.equals(field);
    }
  };

  private static boolean isRetained(Predicate<Object> predicate, Object start) {
    Map<Object, Object> visited = Maps.newIdentityHashMap();
    visited.put(start, start);
    Queue<Object> toScan = Lists.newLinkedList();
    toScan.add(start);

    while (!toScan.isEmpty()) {
      Object current = toScan.poll();
      if (current.getClass().isArray()) {
        if (current.getClass().getComponentType().isPrimitive()) {
          continue;
        }

        for (Object ref : (Object[]) current) {
          if (ref != null) {
            if (predicate.apply(ref)) {
              return true;
            }
            if (visited.put(ref, ref) == null) {
              toScan.add(ref);
            }
          }
        }
      } else {
        // iterate *all* fields (getFields() returns only accessible ones)
        for (Class<?> clazz = current.getClass(); clazz != null;
            clazz = clazz.getSuperclass()) {
          for (Field f : clazz.getDeclaredFields()) {
            if (f.getType().isPrimitive() || ALL_STRONG_REFS.apply(f)) {
              continue;
            }

            f.setAccessible(true);
            try {
              Object ref = f.get(current);
              if (ref != null) {
                if (predicate.apply(ref)) {
                  return true;
                }
                if (visited.put(ref, ref) == null) {
                  toScan.add(ref);
                }
              }
            } catch (IllegalArgumentException e) {
              throw new IllegalStateException("Error when scanning the heap", e);
            } catch (IllegalAccessException e) {
              throw new IllegalStateException("Error when scanning the heap", e);
            }
          }
        }
      }
    }
    return false;
  }

  private static String getClassDescription(Object object) {
    return object == null
        ? "null"
        : ("instance of " + object.getClass().getName());
  }

  public static String chattyFormat(String message, Object expected, Object actual) {
    String expectedClass = getClassDescription(expected);
    String actualClass = getClassDescription(actual);

    return Joiner.on('\n').join((message != null) ? ("\n" + message) : "",
        "  expected " + expectedClass + ": <" + expected + ">",
        "  but was " + actualClass + ": <" + actual + ">");
  }

  public static void assertEqualsUnifyingLineEnds(String expected, String actual) {
    if (actual != null) {
      actual = actual.replaceAll(System.getProperty("line.separator"), "\n");
    }
    assertThat(actual).isEqualTo(expected);
  }

  public static void assertContainsWordsWithQuotes(String message,
      String... strings) {
    for (String string : strings) {
      assertTrue(message + " should contain '" + string + "' (with quotes)",
          message.contains("'" + string + "'"));
    }
  }

  public static void assertNonZeroExitCode(int exitCode, String stdout, String stderr) {
    if (exitCode == 0) {
      fail("expected non-zero exit code but exit code was 0 and stdout was <"
          + stdout + "> and stderr was <" + stderr + ">");
    }
  }

  public static void assertExitCode(int expectedExitCode,
      int exitCode, String stdout, String stderr) {
    if (exitCode != expectedExitCode) {
      fail(String.format("expected exit code <%d> but exit code was <%d> and stdout was <%s> "
          + "and stderr was <%s>", expectedExitCode, exitCode, stdout, stderr));
    }
  }

  public static void assertStdoutContainsString(String expected, String stdout, String stderr) {
    if (!stdout.contains(expected)) {
      fail("expected stdout to contain string <" + expected + "> but stdout was <"
          + stdout + "> and stderr was <" + stderr + ">");
    }
  }

  public static void assertStderrContainsString(String expected, String stdout, String stderr) {
    if (!stderr.contains(expected)) {
      fail("expected stderr to contain string <" + expected + "> but stdout was <"
          + stdout + "> and stderr was <" + stderr + ">");
    }
  }

  public static void assertStdoutContainsRegex(String expectedRegex,
      String stdout, String stderr) {
    if (!Pattern.compile(expectedRegex).matcher(stdout).find()) {
      fail("expected stdout to contain regex <" + expectedRegex + "> but stdout was <"
          + stdout + "> and stderr was <" + stderr + ">");
    }
  }

  public static void assertStderrContainsRegex(String expectedRegex,
      String stdout, String stderr) {
    if (!Pattern.compile(expectedRegex).matcher(stderr).find()) {
      fail("expected stderr to contain regex <" + expectedRegex + "> but stdout was <"
          + stdout + "> and stderr was <" + stderr + ">");
    }
  }

  public static Set<String> asStringSet(Iterable<?> collection) {
    Set<String> set = Sets.newTreeSet();
    for (Object o : collection) {
      set.add("\"" + String.valueOf(o) + "\"");
    }
    return set;
  }

  public static <T> void
  assertSameContents(Iterable<? extends T> expected, Iterable<? extends T> actual) {
    if (!Sets.newHashSet(expected).equals(Sets.newHashSet(actual))) {
      fail("got string set: " + asStringSet(actual).toString()
          + "\nwant: " + asStringSet(expected).toString());
    }
  }
}
