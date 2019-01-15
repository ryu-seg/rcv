/*
 * Ranked Choice Voting Universal Tabulator
 * Copyright (c) 2018 Jonath  an Moldover, Louis Eisenberg, and Hylton Edingfield
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this
 * program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * class TabulatorTests
 * purpose: these regression tests run various tabulations and compare the generated results to
 * expected results.  Passing these tests ensures that changes to tabulation code have not
 * altered the results of the tabulation.
 *
 */

package com.rcv;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TabulatorTests {

  // folder where we store test inputs
  private static final String TEST_ASSET_FOLDER = "test_data";

  // function: fileCompare
  // purpose: compare file contents line by line to identify any differences and give an
  // indication of where they lie
  // param: path1 path to first file to be compared
  // param: path2 path to second file to be compared
  // returns: true if the file contents are equal otherwise false
  // file access: read-only for path1 and path2
  static boolean fileCompare(String path1, String path2) {
    // result will be true if file contents are equal - assume equal until learning otherwise
    boolean result = true;
    try {
      // reader1 and reader2 are used to read lines from the files for comparison
      BufferedReader reader1 = new BufferedReader(new FileReader(path1));
      BufferedReader reader2 = new BufferedReader(new FileReader(path2));
      // track current line to tell user where problems occur
      int currentLine = 1;

      // loop until EOF is encountered
      while (true) {
        // line1 and line2 store current line read from readers or null if EOF
        String line1 = reader1.readLine();
        String line2 = reader2.readLine();
        // see if the files are done
        if (line1 == null && line2 == null) {
          // both files ended
          break;
        } else if (line1 == null || line2 == null) {
          // one file ended but the other did not
          Logger.log(Level.SEVERE, "files are unequal lengths");
          result = false;
          break;
        }
        // both files have content so compare it
        if (!line1.equals(line2)) {
          // report inequality and keep processing in case there are more inequalities
          Logger.log(Level.SEVERE, "files are not equal (%d)\n%s\n%s", currentLine, line1, line2);
          result = false;
        }
        currentLine++;
      }
    } catch (FileNotFoundException e) {
      Logger.log(Level.SEVERE, "file not found: " + e.toString());
      result = false;
    } catch (IOException e) {
      Logger.log(Level.SEVERE, "error reading file: " + e.toString());
      result = false;
    }
    return result;
  }

  // function: runTabulationTest
  // purpose: helper function to support running various tabulation tests
  // param: stem base name of folder containing config file cvr files and expected result files
  static void runTabulationTest(String stem) {
    // full path to config file
    String configPath = Paths.get(System.getProperty("user.dir"),
        TEST_ASSET_FOLDER, stem,
        stem + "_config.json")
        .toAbsolutePath()
        .toString();
    // full path to expected results file
    String expectedPath = Paths.get(System.getProperty("user.dir"),
        TEST_ASSET_FOLDER,
        stem,
        stem + "_expected.json")
        .toAbsolutePath()
        .toString();

    // create a session object and run the tabulation
    TabulatorSession session = new TabulatorSession(configPath);
    session.tabulate();
    // actualSummaryOutputPath is the summary json we just tabulated
    String actualSummaryOutputPath = session.summaryOutputPath;
    // compare actual to expected
    assertTrue(fileCompare(expectedPath, actualSummaryOutputPath));
  }

  // function: setup
  // purpose: runs once at the beginning of testing to setup logging
  @BeforeAll
  public static void setup() {
    try {
      Logger.setup();
    } catch (IOException exception) {
      // this is non-fatal
      System.err.print(String.format("Failed to start system logging!\n%s", exception.toString()));
    }
  }

  // function: testPortlandMayor
  // purpose: test tabulation of Portland contest
  @Test
  @DisplayName("2015 Portland Mayor")
  void testPortlandMayor() {
    runTabulationTest("2015_portland_mayor");
  }

  // function: testPortlandMayor
  // purpose: test tabulation of Portland contest using candidate codes
  @Test
  @DisplayName("2015 Portland Mayor Candidate Codes")
  void testPortlandMayorCodes() {
    runTabulationTest("2015_portland_mayor_codes");
  }

  // function: testPortlandMayorMulti
  // purpose: test tabulation of Portland contest
  @Test
  @DisplayName("2015 Portland Mayor multi-seat contest")
  void testPortlandMayorMulti() {
    runTabulationTest("2015_portland_mayor_multi");
  }

  // function: test2013MinneapolisMayorScale
  // purpose: test large scale (1,000,000+) cvr contest
  @Test
  @DisplayName("2013 Minneapolis Mayor Scale")
  void test2013MinneapolisMayorScale() {
    runTabulationTest("2013_minneapolis_mayor_scale");
  }

  // function: testContinueUntilTwoCandidatesRemain
  // purpose: test rule to continue tabulation until only two candidates remain potentially after
  // all winner(s) have been elected
  @Test
  @DisplayName("Continue Until Two Candidates Remain")
  void testContinueUntilTwoCandidatesRemain() {
    runTabulationTest("continue_tabulation_test");
  }

  // function: test2017MinneapolisMayor
  // purpose: test 2017 Minneapolis Mayor contest
  @Test
  @DisplayName("2017 Minneapolis Mayor")
  void test2017MinneapolisMayor() {
    runTabulationTest("2017_minneapolis_mayor");
  }

  // function: test2013MinneapolisMayor
  // purpose: test 2013 Minneapolis Mayor contest
  @Test
  @DisplayName("2013 Minneapolis Mayor")
  void test2013MinneapolisMayor() {
    runTabulationTest("2013_minneapolis_mayor");
  }

  // function: test2013MinneapolisPark
  // purpose: test 2013 Minneapolis Park contest
  @Test
  @DisplayName("2013 Minneapolis Park")
  void test2013MinneapolisPark() {
    runTabulationTest("2013_minneapolis_park");
  }

  // function: test2018MaineGovPrimaryDem
  // purpose: test 2018 Maine Governor Democratic Primary contest
  @Test
  @DisplayName("2018 Maine Governor Democratic Primary")
  void test2018MaineGovPrimaryDem() {
    runTabulationTest("2018_maine_governor_primary");
  }

  // function: testMinneapolisMultiSeatThreshold
  // purpose: test testMinneapolisMultiSeatThreshold
  @Test
  @DisplayName("testMinneapolisMultiSeatThreshold")
  void testMinneapolisMultiSeatThreshold() {
    runTabulationTest("minneapolis_multi_seat_threshold");
  }

  // function: testDuplicate
  // purpose: test for overvotes
  @Test
  @DisplayName("test for overvotes")
  void testDuplicate() {
    runTabulationTest("duplicate_test");
  }

  // function: testExcludedCandidate
  // purpose: test excluding candidates in config file
  @Test
  @DisplayName("test excluding candidates in config file")
  void testExcludedCandidate() {
    runTabulationTest("excluded_test");
  }

  // function: testMinimumThreshold
  // purpose: test minimum vote threshold setting
  @Test
  @DisplayName("test minimum vote threshold setting")
  void testMinimumThreshold() {
    runTabulationTest("minimum_threshold_test");
  }

  // function: skipToNextTest
  // purpose: test skipping to next candidate after overvote
  @Test
  @DisplayName("test skipping to next candidate after overvote")
  void skipToNextTest() {
    runTabulationTest("skip_to_next_test");
  }
}
