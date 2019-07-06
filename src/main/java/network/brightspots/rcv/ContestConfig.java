/*
 * Universal RCV Tabulator
 * Copyright (c) 2017-2019 Bright Spots Developers.
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
 */

/*
 * Purpose:
 * Wrapper for RawContestConfig object. This class adds logic for looking up rule enum
 * names, candidate names, various configuration utilities, and cast vote record objects.
 */

package network.brightspots.rcv;

import static network.brightspots.rcv.Utils.isNullOrBlank;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import network.brightspots.rcv.RawContestConfig.CVRSource;
import network.brightspots.rcv.RawContestConfig.Candidate;
import network.brightspots.rcv.Tabulator.OvervoteRule;
import network.brightspots.rcv.Tabulator.TieBreakMode;

class ContestConfig {
  private static final int MIN_COLUMN_INDEX = 1;
  private static final int MAX_COLUMN_INDEX = 1000;
  private static final int MIN_ROW_INDEX = 1;
  private static final int MAX_ROW_INDEX = 100000;
  private static final int MIN_MAX_RANKINGS_ALLOWED = 1;
  private static final int MIN_MAX_SKIPPED_RANKS_ALLOWED = 0;
  private static final int MIN_NUMBER_OF_WINNERS = 1;
  private static final int MIN_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC = 1;
  private static final int MAX_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC = 20;
  private static final int MIN_MINIMUM_VOTE_THRESHOLD = 0;
  private static final int MAX_MINIMUM_VOTE_THRESHOLD = 1000000;
  private static final int MIN_RANDOM_SEED = 0;
  private static final String CDF_PROVIDER = "CDF";
  private static final String MAX_SKIPPED_RANKS_ALLOWED_UNLIMITED_OPTION = "unlimited";
  private static final String MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION = "max";

  // If any booleans are unspecified in config file, they should default to false no matter what
  static final boolean SUGGESTED_TABULATE_BY_PRECINCT = false;
  static final boolean SUGGESTED_GENERATE_CDF_JSON = false;
  static final boolean SUGGESTED_CANDIDATE_EXCLUDED = false;
  static final boolean SUGGESTED_SEQUENTIAL_MULTI_SEAT = false;
  static final boolean SUGGESTED_BOTTOMS_UP_MULTI_SEAT = false;
  static final boolean SUGGESTED_ALLOW_ONLY_ONE_WINNER_PER_ROUND = false;
  static final boolean SUGGESTED_NON_INTEGER_WINNING_THRESHOLD = false;
  static final boolean SUGGESTED_HARE_QUOTA = false;
  static final boolean SUGGESTED_BATCH_ELIMINATION = false;
  static final boolean SUGGESTED_CONTINUE_UNTIL_TWO_CANDIDATES_REMAIN = false;
  static final boolean SUGGESTED_EXHAUST_ON_DUPLICATE_CANDIDATES = false;
  static final boolean SUGGESTED_TREAT_BLANK_AS_UNDECLARED_WRITE_IN = false;
  static final int SUGGESTED_NUMBER_OF_WINNERS = 1;
  static final int SUGGESTED_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC = 4;
  static final BigDecimal SUGGESTED_MINIMUM_VOTE_THRESHOLD = BigDecimal.ZERO;
  static final int SUGGESTED_MAX_SKIPPED_RANKS_ALLOWED = 1;
  static final String SUGGESTED_MAX_RANKINGS_ALLOWED = MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION;

  // underlying rawConfig object data
  final RawContestConfig rawConfig;
  // this is used if we have a permutation-based tie-break mode
  private final ArrayList<String> candidatePermutation = new ArrayList<>();
  private final Set<String> excludedCandidates = new HashSet<>();
  // path from which any relative paths should be resolved
  private final String sourceDirectory;
  // used for sequential multi-seat
  private final List<String> sequentialWinners = new LinkedList<>();
  // mapping from candidate code to full name
  private Map<String, String> candidateCodeToNameMap;
  // whether or not there are any validation errors
  private boolean isValid;

  // function: ContestConfig
  // purpose: create a new ContestConfig object
  // param: rawConfig underlying rawConfig object this object wraps
  // param: sourceDirectory folder to use for resolving relative paths
  private ContestConfig(RawContestConfig rawConfig, String sourceDirectory) {
    this.rawConfig = rawConfig;
    this.sourceDirectory = sourceDirectory;
  }

  // function: loadContestConfig
  // purpose: create ContestConfig from pre-populated rawConfig and default folder
  // returns: new ContestConfig object if checks pass otherwise null
  static ContestConfig loadContestConfig(RawContestConfig rawConfig, String sourceDirectory) {
    ContestConfig config = new ContestConfig(rawConfig, sourceDirectory);
    try {
      config.processCandidateData();
    } catch (Exception e) {
      Logger.log(Level.SEVERE, "Error processing candidate data:\n%s", e.toString());
      config = null;
    }
    return config;
  }

  // function: loadContestConfig
  // purpose: factory method to create ContestConfig from configPath
  // - create rawContestConfig from file - can fail for IO issues or invalid json
  // returns: new ContestConfig object if checks pass otherwise null
  static ContestConfig loadContestConfig(String configPath, boolean silentMode) {
    if (configPath == null) {
      Logger.log(Level.SEVERE, "No contest config path specified!");
      return null;
    }
    // config will hold the new ContestConfig if construction succeeds
    ContestConfig config = null;

    // rawConfig holds the basic contest config data parsed from json
    // this will be null if there is a problem loading it
    RawContestConfig rawConfig = JsonParser.readFromFile(configPath, RawContestConfig.class);
    if (rawConfig == null) {
      Logger.log(Level.SEVERE, "Failed to load contest config: %s", configPath);
    } else {
      if (!silentMode) {
        Logger.log(Level.INFO, "Successfully loaded contest config: %s", configPath);
      }
      // source folder will be the parent of configPath
      String parentFolder = new File(configPath).getParent();
      // if there is no parent folder use current working directory
      if (parentFolder == null) {
        parentFolder = System.getProperty("user.dir");
      }
      config = loadContestConfig(rawConfig, parentFolder);
    }
    return config;
  }

  static ContestConfig loadContestConfig(String configPath) {
    return loadContestConfig(configPath, false);
  }

  static boolean isCdf(CVRSource source) {
    return source.getProvider() != null && source.getProvider().toUpperCase().equals(CDF_PROVIDER);
  }

  // function: resolveConfigPath
  // purpose: given a path returns absolute path for use in File IO
  // param: path from this config file (cvr or output folder)
  // returns: resolved path
  String resolveConfigPath(String configPath) {
    // create File for IO operations
    File userFile = new File(configPath);
    // resolvedPath will be returned to caller
    String resolvedPath;
    if (userFile.isAbsolute()) {
      // path is already absolute so use as-is
      resolvedPath = userFile.getAbsolutePath();
    } else {
      // return sourceDirectory/configPath
      resolvedPath = Paths.get(sourceDirectory, configPath).toAbsolutePath().toString();
    }
    return resolvedPath;
  }

  RawContestConfig getRawConfig() {
    return rawConfig;
  }

  // function: validate
  // purpose: validate the correctness of the config data
  // returns any detected problems
  boolean validate() {
    Logger.log(Level.INFO, "Validating contest config...");
    isValid = true;
    validateOutputSettings();
    validateCvrFileSources();
    validateCandidates();
    validateRules();
    if (isValid) {
      Logger.log(Level.INFO, "Contest config validation successful.");
    } else {
      Logger.log(
          Level.SEVERE,
          "Contest config validation failed! Please modify the contest config file and try again.\n"
              + "See config_file_documentation.txt for more details.");
    }

    return isValid;
  }

  // Makes sure String input can be converted to an int, and checks that int against boundaries
  private void checkStringToIntWithBoundaries(
      String input, String inputName, String inputLocation) {
    try {
      int columnIndex = Integer.parseInt(input);
      if (columnIndex < MIN_COLUMN_INDEX || columnIndex > MAX_COLUMN_INDEX) {
        isValid = false;
        Logger.log(
            Level.SEVERE,
            "%s must be from %d to %d if supplied: %s",
            inputName,
            MIN_COLUMN_INDEX,
            MAX_COLUMN_INDEX,
            inputLocation);
      }
    } catch (NumberFormatException e) {
      isValid = false;
      Logger.log(Level.SEVERE, "%s must be an integer if supplied: %s", inputName, inputLocation);
    }
  }

  private void validateOutputSettings() {
    if (isNullOrBlank(getContestName())) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest name is required!");
    }
  }

  private void validateCvrFileSources() {
    if (rawConfig.cvrFileSources == null || rawConfig.cvrFileSources.isEmpty()) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest config must contain at least one cast vote record file!");
    } else {
      HashSet<String> cvrFilePathSet = new HashSet<>();
      for (CVRSource source : rawConfig.cvrFileSources) {
        // perform checks on source input path
        if (isNullOrBlank(source.getFilePath())) {
          isValid = false;
          Logger.log(Level.SEVERE, "filePath is required for each cast vote record file!");
          continue;
        }

        // full path to CVR
        String cvrPath = resolveConfigPath(source.getFilePath());

        // look for duplicate paths
        if (cvrFilePathSet.contains(cvrPath)) {
          isValid = false;
          Logger.log(
              Level.SEVERE, "Duplicate cast vote record filePaths are not allowed: %s", cvrPath);
        } else {
          cvrFilePathSet.add(cvrPath);
        }

        // ensure file exists
        if (!new File(cvrPath).exists()) {
          isValid = false;
          Logger.log(Level.SEVERE, "Cast vote record file not found: %s", cvrPath);
        }

        // perform CDF checks
        if (isCdf(source)) {
          if (rawConfig.cvrFileSources.size() != 1) {
            isValid = false;
            Logger.log(Level.SEVERE, "CDF files must be tabulated individually.");
          }
          if (isTabulateByPrecinctEnabled()) {
            isValid = false;
            Logger.log(Level.SEVERE, "tabulateByPrecinct may not be used with CDF files.");
          }
        } else {
          // perform ES&S checks

          // ensure valid first vote column value
          if (source.getFirstVoteColumnIndex() == null) {
            isValid = false;
            Logger.log(Level.SEVERE, "firstVoteColumnIndex is required: %s", cvrPath);
          } else if (source.getFirstVoteColumnIndex() < MIN_COLUMN_INDEX
              || source.getFirstVoteColumnIndex() > MAX_COLUMN_INDEX) {
            isValid = false;
            Logger.log(
                Level.SEVERE,
                "firstVoteColumnIndex must be from %d to %d: %s",
                MIN_COLUMN_INDEX,
                MAX_COLUMN_INDEX,
                cvrPath);
          }

          // ensure valid first vote row value
          if (source.getFirstVoteRowIndex() == null) {
            isValid = false;
            Logger.log(Level.SEVERE, "firstVoteRowIndex is required: %s", cvrPath);
          } else if (source.getFirstVoteRowIndex() < MIN_ROW_INDEX
              || source.getFirstVoteRowIndex() > MAX_ROW_INDEX) {
            isValid = false;
            Logger.log(
                Level.SEVERE,
                "firstVoteRowIndex must be from %d to %d: %s",
                MIN_ROW_INDEX,
                MAX_ROW_INDEX,
                cvrPath);
          }

          // ensure valid id column value
          if (!isNullOrBlank(source.getIdColumnIndex())) {
            checkStringToIntWithBoundaries(source.getIdColumnIndex(), "idColumnIndex", cvrPath);
          }

          // ensure valid precinct column value
          if (!isNullOrBlank(source.getPrecinctColumnIndex())) {
            checkStringToIntWithBoundaries(
                source.getPrecinctColumnIndex(), "precinctColumnIndex", cvrPath);
          } else if (isTabulateByPrecinctEnabled()) {
            isValid = false;
            Logger.log(
                Level.SEVERE,
                "precinctColumnIndex is required when tabulateByPrecinct is enabled: %s",
                cvrPath);
          }
        }
      }
    }
  }

  private void validateCandidates() {
    HashSet<String> candidateNameSet = new HashSet<>();
    HashSet<String> candidateCodeSet = new HashSet<>();
    for (Candidate candidate : rawConfig.candidates) {
      if (isNullOrBlank(candidate.getName())) {
        isValid = false;
        Logger.log(Level.SEVERE, "Name is required for each candidate!");
      } else if (candidateNameSet.contains(candidate.getName())) {
        isValid = false;
        Logger.log(
            Level.SEVERE, "Duplicate candidate names are not allowed: %s", candidate.getName());
      } else {
        candidateNameSet.add(candidate.getName());
      }

      if (!isNullOrBlank(candidate.getCode())) {
        if (candidateCodeSet.contains(candidate.getCode())) {
          isValid = false;
          Logger.log(
              Level.SEVERE, "Duplicate candidate codes are not allowed: %s", candidate.getCode());
        } else {
          candidateCodeSet.add(candidate.getCode());
        }
      }
    }

    if (candidateCodeSet.size() > 0 && candidateCodeSet.size() != candidateNameSet.size()) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "If candidate codes are used, a unique code is required for each candidate!");
    }

    if (getNumDeclaredCandidates() < 1) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest config must contain at least 1 declared candidate!");
    } else if (getNumDeclaredCandidates() == excludedCandidates.size()) {
      isValid = false;
      Logger.log(Level.SEVERE, "Contest config must contain at least 1 non-excluded candidate!");
    }
  }

  private void validateRules() {
    if (getTiebreakMode() == TieBreakMode.MODE_UNKNOWN) {
      isValid = false;
      Logger.log(Level.SEVERE, "Invalid tie-break mode!");
    }

    if ((getTiebreakMode() == TieBreakMode.RANDOM ||
        getTiebreakMode() == TieBreakMode.PREVIOUS_ROUND_COUNTS_THEN_RANDOM ||
        getTiebreakMode() == TieBreakMode.GENERATE_PERMUTATION) &&
        getRandomSeed() == null) {
      isValid = false;
      Logger.log(Level.SEVERE, "When tiebreakMode involves a random element, randomSeed must be "
          + "supplied.");
    }

    if (getOvervoteRule() == OvervoteRule.RULE_UNKNOWN) {
      isValid = false;
      Logger.log(Level.SEVERE, "Invalid overvote rule!");
    } else if (!isNullOrBlank(getOvervoteLabel())
        && getOvervoteRule() != Tabulator.OvervoteRule.EXHAUST_IMMEDIATELY
        && getOvervoteRule() != Tabulator.OvervoteRule.ALWAYS_SKIP_TO_NEXT_RANK) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "When overvoteLabel is supplied, overvoteRule must be either exhaustImmediately "
              + "or alwaysSkipToNextRank!");
    }

    if (getMaxRankingsAllowed() == null) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "maxRankingsAllowed must either be \"%s\" or an integer!",
          MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION);
    } else if (getNumDeclaredCandidates() >= 1
        && getMaxRankingsAllowed() < MIN_MAX_RANKINGS_ALLOWED) {
      isValid = false;
      Logger.log(
          Level.SEVERE, "maxRankingsAllowed must be %d or higher!", MIN_MAX_RANKINGS_ALLOWED);
    }

    if (getMaxSkippedRanksAllowed() == null) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "maxSkippedRanksAllowed must either be \"%s\" or an integer!",
          MAX_SKIPPED_RANKS_ALLOWED_UNLIMITED_OPTION);
    } else if (getMaxSkippedRanksAllowed() < MIN_MAX_SKIPPED_RANKS_ALLOWED) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "maxSkippedRanksAllowed must be %d or higher!",
          MIN_MAX_SKIPPED_RANKS_ALLOWED);
    }

    if (getNumberOfWinners() == null
        || getNumberOfWinners() < MIN_NUMBER_OF_WINNERS
        || getNumberOfWinners() > getNumDeclaredCandidates()) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "numberOfWinners must be at least %d and no more than the number "
              + "of declared candidates!",
          MIN_NUMBER_OF_WINNERS);
    }

    if (getDecimalPlacesForVoteArithmetic() == null
        || getDecimalPlacesForVoteArithmetic() < MIN_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC
        || getDecimalPlacesForVoteArithmetic() > MAX_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "decimalPlacesForVoteArithmetic must be from %d to %d!",
          MIN_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC,
          MAX_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC);
    }

    if (getMinimumVoteThreshold() == null
        || getMinimumVoteThreshold().intValue() < MIN_MINIMUM_VOTE_THRESHOLD
        || getMinimumVoteThreshold().intValue() > MAX_MINIMUM_VOTE_THRESHOLD) {
      isValid = false;
      Logger.log(
          Level.SEVERE,
          "minimumVoteThreshold must be from %d to %d!",
          MIN_MINIMUM_VOTE_THRESHOLD,
          MAX_MINIMUM_VOTE_THRESHOLD);
    }

    // If this is a multi-seat contest, we validate a couple extra parameters.
    if (getNumberOfWinners() != null && getNumberOfWinners() > 1) {
      if (willContinueUntilTwoCandidatesRemain()) {
        isValid = false;
        Logger.log(
            Level.SEVERE,
            "continueUntilTwoCandidatesRemain can't be true in a multi-seat contest!");
      }

      if (isBatchEliminationEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "batchElimination can't be true in a multi-seat contest!");
      }
    } else {
      if (isSequentialMultiSeatEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "sequentialMultiSeat can't be true in a single-seat contest!");
      }

      if (isBottomsUpMultiSeatEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "bottomsUpMultiSeat can't be true in a single-seat contest!");
      }

      if (isHareQuotaEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "hareQuota can only be true in a multi-seat contest!");
      }
    }

    if (isBottomsUpMultiSeatEnabled()) {
      if (isBatchEliminationEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "batchElimination can't be true in a bottoms-up contest!");
      }

      if (isSequentialMultiSeatEnabled()) {
        isValid = false;
        Logger.log(Level.SEVERE, "sequentialMultiSeat can't be true in a bottoms-up contest!");
      }
    }

    if (getRandomSeed() != null && getRandomSeed() < MIN_RANDOM_SEED) {
      isValid = false;
      Logger.log(Level.SEVERE, "randomSeed can't be less than %d!", MIN_RANDOM_SEED);
    }
  }

  // function: getNumberWinners
  // purpose: how many winners for this contest
  // returns: number of winners
  Integer getNumberOfWinners() {
    return rawConfig.rules.numberOfWinners;
  }

  void setNumberOfWinners(int numberOfWinners) {
    rawConfig.rules.numberOfWinners = numberOfWinners;
  }

  List<String> getSequentialWinners() {
    return sequentialWinners;
  }

  void addSequentialWinner(String winner) {
    sequentialWinners.add(winner);
  }

  // function: getDecimalPlacesForVoteArithmetic
  // purpose: how many places to round votes to after performing fractional vote transfers
  // returns: number of places to round to
  Integer getDecimalPlacesForVoteArithmetic() {
    return rawConfig.rules.decimalPlacesForVoteArithmetic;
  }

  boolean isSequentialMultiSeatEnabled() {
    return rawConfig.rules.sequentialMultiSeat;
  }

  boolean isBottomsUpMultiSeatEnabled() {
    return rawConfig.rules.bottomsUpMultiSeat;
  }

  boolean isAllowOnlyOneWinnerPerRoundEnabled() {
    return rawConfig.rules.allowOnlyOneWinnerPerRound;
  }

  boolean isNonIntegerWinningThresholdEnabled() {
    return rawConfig.rules.nonIntegerWinningThreshold;
  }

  boolean isHareQuotaEnabled() {
    return rawConfig.rules.hareQuota;
  }

  // function: divide
  // purpose: perform a division operation according to the config settings
  // param: dividend is the numerator in the division operation
  // param: divisor is the denominator in the division operation
  // returns: the quotient
  BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
    return dividend.divide(divisor, getDecimalPlacesForVoteArithmetic(), RoundingMode.DOWN);
  }

  BigDecimal multiply(BigDecimal multiplier, BigDecimal multiplicand) {
    return multiplier
        .multiply(multiplicand)
        .setScale(getDecimalPlacesForVoteArithmetic(), RoundingMode.DOWN);
  }

  // function: getOutputDirectoryRaw
  // purpose: getter for outputDirectory
  // returns: raw string from config or falls back to user folder if none is set
  String getOutputDirectoryRaw() {
    // outputDirectory is where output files should be written
    return !isNullOrBlank(rawConfig.outputSettings.outputDirectory)
        ? rawConfig.outputSettings.outputDirectory
        : FileUtils.getUserDirectory();
  }

  // function: getOutputDirectory
  // purpose: get the directory location where output files should be written
  // returns: path to directory where output files should be written
  String getOutputDirectory() {
    return resolveConfigPath(getOutputDirectoryRaw());
  }

  // function: willContinueUntilTwoCandidatesRemain
  // purpose: getter for setting to keep tabulating beyond selecting winner until two candidates
  // remain
  // returns: whether to keep tabulating until two candidates remain
  boolean willContinueUntilTwoCandidatesRemain() {
    return rawConfig.rules.continueUntilTwoCandidatesRemain;
  }

  // function: getContestName
  // purpose: getter for contestName
  // returns: contest name
  String getContestName() {
    return rawConfig.outputSettings.contestName;
  }

  // function: getContestJurisdiction
  // purpose: getter for contestJurisdiction
  // returns: contest jurisdiction name
  String getContestJurisdiction() {
    return rawConfig.outputSettings.contestJurisdiction;
  }

  // function: getContestOffice
  // purpose: getter for contestOffice
  // returns: contest office name
  String getContestOffice() {
    return rawConfig.outputSettings.contestOffice;
  }

  // function: getContestDate
  // purpose: getter for contestDate
  // returns: contest date
  String getContestDate() {
    return rawConfig.outputSettings.contestDate;
  }

  // function: isTabulateByPrecinctEnabled
  // purpose: getter for tabulateByPrecinct
  // returns: true if and only if we should tabulate by precinct
  boolean isTabulateByPrecinctEnabled() {
    return rawConfig.outputSettings.tabulateByPrecinct;
  }

  boolean isGenerateCdfJsonEnabled() {
    return rawConfig.outputSettings.generateCdfJson;
  }

  // Converts a String to an Integer and also allows for an additional option as valid input
  private Integer stringToIntWithOption(String rawInput, String optionFlag, Integer optionResult) {
    Integer intValue;
    if (isNullOrBlank(rawInput)) {
      intValue = null;
    } else if (rawInput.toLowerCase().equals(optionFlag)) {
      intValue = optionResult;
    } else {
      try {
        intValue = Integer.parseInt(rawInput);
      } catch (NumberFormatException e) {
        intValue = null;
      }
    }
    return intValue;
  }

  // function: getMaxRankingsAllowed
  // purpose: getter for maxRankingsAllowed
  // returns: max rankings allowed
  Integer getMaxRankingsAllowed() {
    return stringToIntWithOption(
        rawConfig.rules.maxRankingsAllowed,
        MAX_RANKINGS_ALLOWED_NUM_CANDIDATES_OPTION,
        getNumDeclaredCandidates());
  }

  // function: isBatchEliminationEnabled
  // purpose: getter for batchElimination
  // returns: true if and only if we should use batch elimination
  boolean isBatchEliminationEnabled() {
    return rawConfig.rules.batchElimination;
  }

  // function: numDeclaredCandidates
  // purpose: calculate the number of declared candidates from the contest configuration
  // returns: the number of declared candidates from the contest configuration
  int getNumDeclaredCandidates() {
    // num will contain the resulting number of candidates
    int num = getCandidateCodeList().size();
    if (!isNullOrBlank(getUndeclaredWriteInLabel())
        && getCandidateCodeList().contains(getUndeclaredWriteInLabel())) {
      num--;
    }
    return num;
  }

  // function: numCandidates
  // purpose: return number of candidates including UWIs as a candidate if they are in use
  // num will contain the resulting number of candidates
  int getNumCandidates() {
    return getCandidateCodeList().size();
  }

  boolean candidateIsExcluded(String candidate) {
    return excludedCandidates.contains(candidate);
  }

  // function: getOvervoteRule
  // purpose: return overvote rule enum to use
  // returns: overvote rule to use for this config
  OvervoteRule getOvervoteRule() {
    OvervoteRule rule = OvervoteRule.getByLabel(rawConfig.rules.overvoteRule);
    return rule == null ? OvervoteRule.RULE_UNKNOWN : rule;
  }

  // function: getMinimumVoteThreshold
  // purpose: getter for minimumVoteThreshold rule
  // returns: minimum vote threshold to use or default value if it's not specified
  BigDecimal getMinimumVoteThreshold() {
    return rawConfig.rules.minimumVoteThreshold != null
        ? new BigDecimal(rawConfig.rules.minimumVoteThreshold)
        : null;
  }

  // function: getMaxSkippedRanksAllowed
  // purpose: getter for maxSkippedRanksAllowed rule
  // returns: max skipped ranks allowed in this config
  Integer getMaxSkippedRanksAllowed() {
    return stringToIntWithOption(
        rawConfig.rules.maxSkippedRanksAllowed,
        MAX_SKIPPED_RANKS_ALLOWED_UNLIMITED_OPTION,
        Integer.MAX_VALUE);
  }

  // function: getUndeclaredWriteInLabel
  // purpose: getter for UWI label
  // returns: UWI label for this config
  String getUndeclaredWriteInLabel() {
    return rawConfig.rules.undeclaredWriteInLabel;
  }

  // function: getOvervoteLabel
  // purpose: getter for overvote label rule
  // returns: overvote label for this config
  String getOvervoteLabel() {
    return rawConfig.rules.overvoteLabel;
  }

  // function: getUndervoteLabel
  // purpose: getter for undervote label
  // returns: undervote label for this config
  String getUndervoteLabel() {
    return rawConfig.rules.undervoteLabel;
  }

  // function: getTiebreakMode
  // purpose: return tiebreak mode to use
  // returns: tiebreak mode to use for this config
  TieBreakMode getTiebreakMode() {
    TieBreakMode mode = TieBreakMode.getByLabel(rawConfig.rules.tiebreakMode);
    return mode == null ? TieBreakMode.MODE_UNKNOWN : mode;
  }

  Integer getRandomSeed() {
    return rawConfig.rules.randomSeed;
  }

  // function: isTreatBlankAsUndeclaredWriteInEnabled
  // purpose: getter for treatBlankAsUndeclaredWriteIn rule
  // returns: true if we are to treat blank cell as UWI
  boolean isTreatBlankAsUndeclaredWriteInEnabled() {
    return rawConfig.rules.treatBlankAsUndeclaredWriteIn;
  }

  // function: isExhaustOnDuplicateCandidateEnabled
  // purpose: getter for exhaustOnDuplicateCandidate rule
  // returns: true if tabulation should exhaust ballot when encountering a duplicate candidate
  boolean isExhaustOnDuplicateCandidateEnabled() {
    return rawConfig.rules.exhaustOnDuplicateCandidate;
  }

  // function: getCandidateCodeList
  // purpose: return list of candidate codes for this config
  // returns: return list of candidate codes for this config
  Set<String> getCandidateCodeList() {
    return candidateCodeToNameMap.keySet();
  }

  // function: getNameForCandidateID
  // purpose: look up full candidate name given a candidate code
  // param: code the code of the candidate whose name we want to look up
  // returns: the full name for the given candidateID
  String getNameForCandidateCode(String code) {
    return getUndeclaredWriteInLabel() != null && getUndeclaredWriteInLabel().equals(code)
        ? "Undeclared"
        : candidateCodeToNameMap.get(code);
  }

  // function: getCandidatePermutation
  // purpose: getter for ordered list of candidates for tie-breaking
  // returns: ordered list of candidates
  ArrayList<String> getCandidatePermutation() {
    return candidatePermutation;
  }

  void setCandidateExclusionStatus(String candidateCode, boolean excluded) {
    if (excluded) {
      excludedCandidates.add(candidateCode);
    } else {
      excludedCandidates.remove(candidateCode);
    }
  }

  // function: processCandidateData
  // purpose: perform pre-processing on candidates:
  // 1) if there are any CDF input sources extract candidates names from them
  // 2) build map of candidate ID to candidate name
  // 3) generate tie-break ordering if needed
  private void processCandidateData() {
    candidateCodeToNameMap = new HashMap<>();

    for (RawContestConfig.CVRSource source : rawConfig.cvrFileSources) {
      // for any CDF sources extract candidate names
      if (isCdf(source)) {
        // cvrPath is the resolved path to this source
        String cvrPath = resolveConfigPath(source.getFilePath());
        CommonDataFormatReader reader = new CommonDataFormatReader(cvrPath, this);
        candidateCodeToNameMap = reader.getCandidates();
        candidatePermutation.addAll(candidateCodeToNameMap.keySet());
      }
    }

    if (rawConfig.candidates != null) {
      for (RawContestConfig.Candidate candidate : rawConfig.candidates) {
        String code = candidate.getCode();
        String name = candidate.getName();
        if (isNullOrBlank(code)) {
          code = name;
        }

        // duplicate names or codes get caught in validation
        candidateCodeToNameMap.put(code, name);
        candidatePermutation.add(code);
        if (candidate.isExcluded()) {
          excludedCandidates.add(code);
        }
      }
    }

    if (getTiebreakMode() == TieBreakMode.GENERATE_PERMUTATION) {
      // It's not valid to have a null random seed with this tie-break mode; the validation will
      // catch that and report a helpful error. Validation also hits this code path, though, so we
      // need to prevent a NullPointerException here.
      if (getRandomSeed() != null) {
        Collections.shuffle(candidatePermutation, new Random(getRandomSeed()));
      }
    }

    String uwiLabel = getUndeclaredWriteInLabel();
    if (!isNullOrBlank(uwiLabel)) {
      candidateCodeToNameMap.put(uwiLabel, uwiLabel);
    }
  }
}
