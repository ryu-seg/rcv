/*
 * Ranked Choice Voting Universal Tabulator
 * Copyright (c) 2018 Jonathan Moldover, Louis Eisenberg, and Hylton Edingfield
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

package com.rcv;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcv.RawContestConfig.CVRSource;
import com.rcv.RawContestConfig.Candidate;
import com.rcv.RawContestConfig.ContestRules;
import com.rcv.RawContestConfig.OutputSettings;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.StringConverter;

public class GuiConfigController implements Initializable {

  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final String CONFIG_FILE_NAME = "config_file_documentation.txt";

  // Used to check if changes have been made to a new config
  private String emptyConfigString;
  // File previously loaded or saved
  private File selectedFile;

  @FXML
  private TextArea textAreaStatus;
  @FXML
  private TextArea textAreaHelp;
  @FXML
  private Label labelCurrentlyLoaded;
  @FXML
  private TextField textFieldContestName;
  @FXML
  private TextField textFieldOutputDirectory;
  @FXML
  private DatePicker datePickerContestDate;
  @FXML
  private TextField textFieldContestJurisdiction;
  @FXML
  private TextField textFieldContestOffice;
  @FXML
  private CheckBox checkBoxTabulateByPrecinct;
  @FXML
  private TableView<CVRSource> tableViewCvrFiles;
  @FXML
  private TableColumn<CVRSource, String> tableColumnCvrFilePath;
  @FXML
  private TableColumn<CVRSource, Integer> tableColumnCvrFirstVoteCol;
  @FXML
  private TableColumn<CVRSource, Integer> tableColumnCvrIdCol;
  @FXML
  private TableColumn<CVRSource, Integer> tableColumnCvrPrecinctCol;
  @FXML
  private TableColumn<CVRSource, String> tableColumnCvrProvider;
  @FXML
  private TextField textFieldCvrFilePath;
  @FXML
  private TextField textFieldCvrFirstVoteCol;
  @FXML
  private TextField textFieldCvrIdCol;
  @FXML
  private TextField textFieldCvrPrecinctCol;
  @FXML
  private TextField textFieldCvrProvider;
  @FXML
  private TableView<Candidate> tableViewCandidates;
  @FXML
  private TableColumn<Candidate, String> tableColumnCandidateName;
  @FXML
  private TableColumn<Candidate, String> tableColumnCandidateCode;
  @FXML
  private TextField textFieldCandidateName;
  @FXML
  private TextField textFieldCandidateCode;
  @FXML
  private ChoiceBox<Tabulator.TieBreakMode> choiceTiebreakMode;
  @FXML
  private ChoiceBox<Tabulator.OvervoteRule> choiceOvervoteRule;
  @FXML
  private TextField textFieldMaxRankingsAllowed;
  @FXML
  private TextField textFieldMaxSkippedRanksAllowed;
  @FXML
  private TextField textFieldNumberOfWinners;
  @FXML
  private TextField textFieldDecimalPlacesForVoteArithmetic;
  @FXML
  private TextField textFieldMinimumVoteThreshold;
  @FXML
  private TextField textFieldOvervoteLabel;
  @FXML
  private TextField textFieldUndervoteLabel;
  @FXML
  private TextField textFieldUndeclaredWriteInLabel;
  @FXML
  private TextField textFieldRulesDescription;
  @FXML
  private CheckBox checkBoxBatchElimination;
  @FXML
  private CheckBox checkBoxContinueUntilTwoCandidatesRemain;
  @FXML
  private CheckBox checkBoxExhaustOnDuplicateCandidate;
  @FXML
  private CheckBox checkBoxTreatBlankAsUndeclaredWriteIn;
  @FXML
  private ButtonBar buttonBar;

  public void buttonNewConfigClicked() {
    if (checkForSaveAndContinue()) {
      Logger.log(Level.INFO, "Creating new config.");
      GuiContext.getInstance().setConfig(null);
      selectedFile = null;
      clearConfig();
    }
  }

  private void loadFile(File fileToLoad) {
    GuiContext.getInstance().setConfig(Main.loadContestConfig(fileToLoad.getAbsolutePath()));
    if (GuiContext.getInstance().getConfig() != null) {
      loadConfig(GuiContext.getInstance().getConfig());
      labelCurrentlyLoaded.setText("Currently loaded: " + fileToLoad.getAbsolutePath());
    }
  }

  public void buttonLoadConfigClicked() {
    if (checkForSaveAndContinue()) {
      FileChooser fc = new FileChooser();
      if (selectedFile == null) {
        fc.setInitialDirectory(new File(System.getProperty("user.dir")));
      } else {
        fc.setInitialDirectory(new File(selectedFile.getParent()));
      }
      fc.getExtensionFilters().add(new ExtensionFilter("JSON files", "*.json"));
      fc.setTitle("Load Config");
      File fileToLoad = fc.showOpenDialog(GuiContext.getInstance().getMainWindow());
      if (fileToLoad != null) {
        selectedFile = fileToLoad;
        loadFile(fileToLoad);
      }
    }
  }

  private File getSaveFile() {
    FileChooser fc = new FileChooser();
    if (selectedFile == null) {
      fc.setInitialDirectory(new File(System.getProperty("user.dir")));
    } else {
      fc.setInitialDirectory(new File(selectedFile.getParent()));
      fc.setInitialFileName(selectedFile.getName());
    }
    fc.getExtensionFilters().add(new ExtensionFilter("JSON files", "*.json"));
    fc.setTitle("Save Config");
    return fc.showSaveDialog(GuiContext.getInstance().getMainWindow());
  }

  private void saveFile(File fileToSave) {
    JsonParser.createFileFromRawContestConfig(fileToSave, createRawContestConfig());
    // Reload to keep GUI fields updated in case invalid values are replaced during save process
    loadFile(fileToSave);
  }

  public void buttonSaveClicked() {
    File fileToSave = getSaveFile();
    if (fileToSave != null) {
      selectedFile = fileToSave;
      saveFile(fileToSave);
    }
  }

  public void buttonValidateClicked() {
    buttonBar.setDisable(true);
    ValidatorService service = new ValidatorService(createRawContestConfig());
    service.setOnSucceeded(event -> buttonBar.setDisable(false));
    service.setOnCancelled(event -> buttonBar.setDisable(false));
    service.setOnFailed(event -> buttonBar.setDisable(false));
    service.start();
  }

  public void buttonTabulateClicked() {
    if (checkForSaveAndTabulate()) {
      if (GuiContext.getInstance().getConfig() != null) {
        buttonBar.setDisable(true);
        TabulatorService service = new TabulatorService();
        service.setOnSucceeded(event -> buttonBar.setDisable(false));
        service.setOnCancelled(event -> buttonBar.setDisable(false));
        service.setOnFailed(event -> buttonBar.setDisable(false));
        service.start();
      } else {
        Logger.log(Level.WARNING, "Please load a config file before attempting to tabulate!");
      }
    }
  }

  public void buttonExitClicked() {
    if (checkForSaveAndContinue()) {
      Logger.log(Level.INFO, "Exiting tabulator GUI.");
      Platform.exit();
    }
  }

  public void buttonOutputDirectoryClicked() {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setInitialDirectory(new File(System.getProperty("user.dir")));
    dc.setTitle("Output Directory");

    File outputDirectory = dc.showDialog(GuiContext.getInstance().getMainWindow());
    if (outputDirectory != null) {
      textFieldOutputDirectory.setText(outputDirectory.getAbsolutePath());
    }
  }

  public void buttonClearDatePickerContestDateClicked() {
    datePickerContestDate.setValue(null);
  }

  public void buttonCvrFilePathClicked() {
    FileChooser fc = new FileChooser();
    fc.setInitialDirectory(new File(System.getProperty("user.dir")));
    fc.getExtensionFilters().add(new ExtensionFilter("Excel files", "*.xls", "*.xlsx"));
    fc.setTitle("Select CVR File");

    File openFile = fc.showOpenDialog(GuiContext.getInstance().getMainWindow());
    if (openFile != null) {
      textFieldCvrFilePath.setText(openFile.getAbsolutePath());
    }
  }

  public void buttonAddCvrFileClicked() {
    CVRSource cvrSource = new CVRSource();
    if (textFieldCvrFilePath.getText().isEmpty()) {
      Logger.log(Level.WARNING, "CVR file path is required!");
    } else if (textFieldCvrFirstVoteCol.getText().isEmpty()) {
      Logger.log(Level.WARNING, "CVR first vote column is required!");
    } else {
      cvrSource.setFilePath(textFieldCvrFilePath.getText());
      cvrSource.setFirstVoteColumnIndex(getIntValueElse(textFieldCvrFirstVoteCol, null));
      cvrSource.setIdColumnIndex(getIntValueElse(textFieldCvrIdCol, null));
      cvrSource.setPrecinctColumnIndex(getIntValueElse(textFieldCvrPrecinctCol, null));
      cvrSource.setProvider(textFieldCvrProvider.getText());
      tableViewCvrFiles.getItems().add(cvrSource);
      textFieldCvrFilePath.clear();
      textFieldCvrFirstVoteCol.clear();
      textFieldCvrIdCol.clear();
      textFieldCvrPrecinctCol.clear();
      textFieldCvrProvider.clear();
    }
  }

  public void buttonDeleteCvrFileClicked() {
    tableViewCvrFiles
        .getItems()
        .removeAll(tableViewCvrFiles.getSelectionModel().getSelectedItems());
  }

  public void buttonAddCandidateClicked() {
    Candidate candidate = new Candidate();
    if (textFieldCandidateName.getText().isEmpty()) {
      Logger.log(Level.WARNING, "Candidate name field is required!");
    } else {
      candidate.setName(textFieldCandidateName.getText());
      candidate.setCode(textFieldCandidateCode.getText());
      tableViewCandidates.getItems().add(candidate);
      textFieldCandidateName.clear();
      textFieldCandidateCode.clear();
    }
  }

  public void buttonDeleteCandidateClicked() {
    tableViewCandidates
        .getItems()
        .removeAll(tableViewCandidates.getSelectionModel().getSelectedItems());
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    Logger.addGuiLogging(this.textAreaStatus);
    Logger.log(Level.INFO, "Opening tabulator GUI.");

    String helpText;
    try {
      helpText =
          new BufferedReader(
              new InputStreamReader(ClassLoader.getSystemResourceAsStream(CONFIG_FILE_NAME)))
              .lines()
              .collect(Collectors.joining("\n"));
    } catch (Exception exception) {
      Logger.log(Level.SEVERE, "Error loading: %s\n%s", CONFIG_FILE_NAME, exception.toString());
      helpText = String.format("<Error loading %s>", CONFIG_FILE_NAME);
    }
    textAreaHelp.setText(helpText);

    datePickerContestDate.setConverter(
        new StringConverter<>() {
          @Override
          public String toString(LocalDate date) {
            return date != null ? DATE_TIME_FORMATTER.format(date) : "";
          }

          @Override
          public LocalDate fromString(String string) {
            return string != null && !string.isEmpty()
                ? LocalDate.parse(string, DATE_TIME_FORMATTER)
                : null;
          }
        });

    textFieldCvrFirstVoteCol
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldCvrFirstVoteCol));
    textFieldCvrIdCol.textProperty().addListener(new TextFieldListenerNonNegInt(textFieldCvrIdCol));
    textFieldCvrPrecinctCol
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldCvrPrecinctCol));
    tableColumnCvrFilePath.setCellValueFactory(new PropertyValueFactory<>("filePath"));
    tableColumnCvrFirstVoteCol.setCellValueFactory(
        new PropertyValueFactory<>("firstVoteColumnIndex"));
    tableColumnCvrIdCol.setCellValueFactory(new PropertyValueFactory<>("idColumnIndex"));
    tableColumnCvrPrecinctCol.setCellValueFactory(
        new PropertyValueFactory<>("precinctColumnIndex"));
    tableColumnCvrProvider.setCellValueFactory(new PropertyValueFactory<>("provider"));
    tableViewCvrFiles.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    tableColumnCandidateName.setCellValueFactory(new PropertyValueFactory<>("name"));
    tableColumnCandidateCode.setCellValueFactory(new PropertyValueFactory<>("code"));
    tableViewCandidates.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

    choiceTiebreakMode.getItems().addAll(Tabulator.TieBreakMode.values());
    choiceTiebreakMode.getItems().remove(Tabulator.TieBreakMode.MODE_UNKNOWN);
    choiceOvervoteRule.getItems().addAll(Tabulator.OvervoteRule.values());
    choiceOvervoteRule.getItems().remove(Tabulator.OvervoteRule.RULE_UNKNOWN);

    textFieldMaxRankingsAllowed
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldMaxRankingsAllowed));
    textFieldMaxSkippedRanksAllowed
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldMaxSkippedRanksAllowed));
    textFieldNumberOfWinners
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldNumberOfWinners));
    textFieldDecimalPlacesForVoteArithmetic
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldDecimalPlacesForVoteArithmetic));
    textFieldMinimumVoteThreshold
        .textProperty()
        .addListener(new TextFieldListenerNonNegInt(textFieldMinimumVoteThreshold));

    setDefaultValues();

    try {
      emptyConfigString =
          new ObjectMapper()
              .writer()
              .withDefaultPrettyPrinter()
              .writeValueAsString(createRawContestConfig());
    } catch (JsonProcessingException exception) {
      Logger.log(
          Level.WARNING,
          "Unable to set emptyConfigString, but everything should work fine anyway!\n%s",
          exception.toString());
    }
  }

  private void setTextFieldToInteger(TextField textField, Integer value) {
    if (value != null) {
      textField.setText(Integer.toString(value));
    }
  }

  private void setDefaultValues() {
    labelCurrentlyLoaded.setText("Currently loaded: <New Config>");

    checkBoxTabulateByPrecinct.setSelected(ContestConfig.DEFAULT_TABULATE_BY_PRECINCT);
    checkBoxBatchElimination.setSelected(ContestConfig.DEFAULT_BATCH_ELIMINATION);
    checkBoxContinueUntilTwoCandidatesRemain.setSelected(
        ContestConfig.DEFAULT_CONTINUE_UNTIL_TWO_CANDIDATES_REMAIN);
    checkBoxExhaustOnDuplicateCandidate.setSelected(
        ContestConfig.DEFAULT_EXHAUST_ON_DUPLICATE_CANDIDATES);
    checkBoxTreatBlankAsUndeclaredWriteIn.setSelected(
        ContestConfig.DEFAULT_TREAT_BLANK_AS_UNDECLARED_WRITE_IN);

    textFieldNumberOfWinners.setText(String.valueOf(ContestConfig.DEFAULT_NUMBER_OF_WINNERS));
    textFieldDecimalPlacesForVoteArithmetic.setText(
        String.valueOf(ContestConfig.DEFAULT_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC));
    textFieldMinimumVoteThreshold.setText(
        String.valueOf(ContestConfig.DEFAULT_MINIMUM_VOTE_THRESHOLD));
  }

  private void clearConfig() {
    textFieldContestName.clear();
    textFieldOutputDirectory.clear();
    datePickerContestDate.setValue(null);
    textFieldContestJurisdiction.clear();
    textFieldContestOffice.clear();
    checkBoxTabulateByPrecinct.setSelected(false);

    textFieldCvrFilePath.clear();
    textFieldCvrFirstVoteCol.clear();
    textFieldCvrIdCol.clear();
    textFieldCvrPrecinctCol.clear();
    textFieldCvrProvider.clear();
    tableViewCvrFiles.getItems().clear();

    textFieldCandidateName.clear();
    textFieldCandidateCode.clear();
    tableViewCandidates.getItems().clear();

    choiceTiebreakMode.setValue(null);
    choiceOvervoteRule.setValue(null);
    textFieldMaxRankingsAllowed.clear();
    textFieldMaxSkippedRanksAllowed.clear();
    textFieldNumberOfWinners.clear();
    textFieldDecimalPlacesForVoteArithmetic.clear();
    textFieldMinimumVoteThreshold.clear();
    textFieldOvervoteLabel.clear();
    textFieldUndervoteLabel.clear();
    textFieldUndeclaredWriteInLabel.clear();
    textFieldRulesDescription.clear();
    checkBoxBatchElimination.setSelected(false);
    checkBoxContinueUntilTwoCandidatesRemain.setSelected(false);
    checkBoxExhaustOnDuplicateCandidate.setSelected(false);
    checkBoxTreatBlankAsUndeclaredWriteIn.setSelected(false);

    setDefaultValues();
  }

  private boolean checkIfNeedsSaving() {
    boolean needsSaving = true;
    try {
      String currentConfigString =
          new ObjectMapper()
              .writer()
              .withDefaultPrettyPrinter()
              .writeValueAsString(createRawContestConfig());
      if (selectedFile == null && currentConfigString.equals(emptyConfigString)) {
        // All fields are currently empty / default values so no point in asking to save
        needsSaving = false;
      } else if (GuiContext.getInstance().getConfig() != null) {
        String savedConfigString =
            new ObjectMapper()
                .writer()
                .withDefaultPrettyPrinter()
                .writeValueAsString(GuiContext.getInstance().getConfig().rawConfig);
        needsSaving = !currentConfigString.equals(savedConfigString);
      }
    } catch (JsonProcessingException exception) {
      Logger.log(
          Level.WARNING,
          "Unable tell if saving is necessary, but everything should work fine anyway! Prompting for save just in case...\n%s",
          exception.toString());
    }
    return needsSaving;
  }

  private boolean checkForSaveAndContinue() {
    boolean willContinue = false;
    if (checkIfNeedsSaving()) {
      ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
      ButtonType doNotSaveButton = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
      Alert alert =
          new Alert(
              AlertType.CONFIRMATION,
              "Do you want to save your changes before continuing?",
              saveButton,
              doNotSaveButton,
              ButtonType.CANCEL);
      alert.setHeaderText(null);
      Optional<ButtonType> result = alert.showAndWait();
      if (result.isPresent() && result.get() == saveButton) {
        File fileToSave = getSaveFile();
        if (fileToSave != null) {
          saveFile(fileToSave);
          willContinue = true;
        }
      } else if (result.isPresent() && result.get() == doNotSaveButton) {
        willContinue = true;
      }
    } else {
      willContinue = true;
    }
    return willContinue;
  }

  private boolean checkForSaveAndTabulate() {
    boolean willContinue = false;
    if (checkIfNeedsSaving()) {
      ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
      Alert alert =
          new Alert(
              AlertType.WARNING,
              "You must either save your changes before continuing or load a new config!",
              saveButton,
              ButtonType.CANCEL);
      alert.setHeaderText(null);
      Optional<ButtonType> result = alert.showAndWait();
      if (result.isPresent() && result.get() == saveButton) {
        File fileToSave = getSaveFile();
        if (fileToSave != null) {
          saveFile(fileToSave);
          willContinue = true;
        }
      }
    } else {
      willContinue = true;
    }
    return willContinue;
  }

  private void loadConfig(ContestConfig config) {
    clearConfig();

    textFieldContestName.setText(config.getContestName());
    textFieldOutputDirectory.setText(config.getOutputDirectory());
    if (config.getContestDate() != null && !config.getContestDate().isEmpty()) {
      datePickerContestDate.setValue(LocalDate.parse(config.getContestDate(), DATE_TIME_FORMATTER));
    }
    textFieldContestJurisdiction.setText(config.getContestJurisdiction());
    textFieldContestOffice.setText(config.getContestOffice());
    checkBoxTabulateByPrecinct.setSelected(config.isTabulateByPrecinctEnabled());

    if (config.rawConfig.cvrFileSources != null) {
      tableViewCvrFiles.setItems(
          FXCollections.observableArrayList(config.rawConfig.cvrFileSources));
    }

    if (config.rawConfig.candidates != null) {
      tableViewCandidates.setItems(FXCollections.observableArrayList(config.rawConfig.candidates));
    }

    choiceTiebreakMode.setValue(config.getTiebreakMode());
    choiceOvervoteRule.setValue(config.getOvervoteRule());
    setTextFieldToInteger(textFieldMaxRankingsAllowed, config.getMaxRankingsAllowed());
    setTextFieldToInteger(textFieldMaxSkippedRanksAllowed, config.getMaxSkippedRanksAllowed());
    setTextFieldToInteger(textFieldNumberOfWinners, config.getNumberOfWinners());
    setTextFieldToInteger(
        textFieldDecimalPlacesForVoteArithmetic, config.getDecimalPlacesForVoteArithmetic());
    setTextFieldToInteger(
        textFieldMinimumVoteThreshold, config.getMinimumVoteThreshold().intValue());
    textFieldOvervoteLabel.setText(config.getOvervoteLabel());
    textFieldUndervoteLabel.setText(config.getUndervoteLabel());
    textFieldUndeclaredWriteInLabel.setText(config.getUndeclaredWriteInLabel());
    textFieldRulesDescription.setText(config.getRulesDescription());
    checkBoxBatchElimination.setSelected(config.isBatchEliminationEnabled());
    checkBoxContinueUntilTwoCandidatesRemain.setSelected(
        config.willContinueUntilTwoCandidatesRemain());
    checkBoxExhaustOnDuplicateCandidate.setSelected(config.isExhaustOnDuplicateCandidateEnabled());
    checkBoxTreatBlankAsUndeclaredWriteIn.setSelected(
        config.isTreatBlankAsUndeclaredWriteInEnabled());
  }

  private Integer getIntValueElse(TextField textField, Integer defaultValue) {
    Integer returnValue;
    try {
      if (textField.getText().isEmpty()) {
        throw new IllegalArgumentException();
      }
      returnValue = Integer.valueOf(textField.getText());
    } catch (Exception exception) {
      if (!(textField.getText().isEmpty() && defaultValue == null)) {
        Logger.log(
            Level.WARNING,
            "Integer required! Illegal value '%s' was replaced by '%s'",
            textField.getText(),
            defaultValue);
      }
      returnValue = defaultValue;
    }
    return returnValue;
  }

  private String getChoiceElse(ChoiceBox choiceBox, Enum defaultValue) {
    return choiceBox.getValue() != null ? choiceBox.getValue().toString() : defaultValue.toString();
  }

  private RawContestConfig createRawContestConfig() {
    RawContestConfig config = new RawContestConfig();

    OutputSettings outputSettings = new OutputSettings();
    outputSettings.contestName = textFieldContestName.getText();
    outputSettings.outputDirectory = textFieldOutputDirectory.getText();
    outputSettings.contestDate =
        datePickerContestDate.getValue() != null ? datePickerContestDate.getValue().toString() : "";
    outputSettings.contestJurisdiction = textFieldContestJurisdiction.getText();
    outputSettings.contestOffice = textFieldContestOffice.getText();
    outputSettings.tabulateByPrecinct = checkBoxTabulateByPrecinct.isSelected();
    config.outputSettings = outputSettings;

    config.cvrFileSources = new ArrayList<>(tableViewCvrFiles.getItems());

    config.candidates = new ArrayList<>(tableViewCandidates.getItems());

    ContestRules rules = new ContestRules();
    rules.tiebreakMode = getChoiceElse(choiceTiebreakMode, Tabulator.TieBreakMode.MODE_UNKNOWN);
    rules.overvoteRule = getChoiceElse(choiceOvervoteRule, Tabulator.OvervoteRule.RULE_UNKNOWN);
    rules.maxRankingsAllowed = getIntValueElse(textFieldMaxRankingsAllowed, null);
    rules.maxSkippedRanksAllowed = getIntValueElse(textFieldMaxSkippedRanksAllowed, null);
    rules.numberOfWinners =
        getIntValueElse(textFieldNumberOfWinners, ContestConfig.DEFAULT_NUMBER_OF_WINNERS);
    rules.decimalPlacesForVoteArithmetic =
        getIntValueElse(
            textFieldDecimalPlacesForVoteArithmetic,
            ContestConfig.DEFAULT_DECIMAL_PLACES_FOR_VOTE_ARITHMETIC);
    rules.minimumVoteThreshold =
        getIntValueElse(
            textFieldMinimumVoteThreshold, ContestConfig.DEFAULT_MINIMUM_VOTE_THRESHOLD.intValue());
    rules.batchElimination = checkBoxBatchElimination.isSelected();
    rules.continueUntilTwoCandidatesRemain = checkBoxContinueUntilTwoCandidatesRemain.isSelected();
    rules.exhaustOnDuplicateCandidate = checkBoxExhaustOnDuplicateCandidate.isSelected();
    rules.treatBlankAsUndeclaredWriteIn = checkBoxTreatBlankAsUndeclaredWriteIn.isSelected();
    rules.overvoteLabel = textFieldOvervoteLabel.getText();
    rules.undervoteLabel = textFieldUndervoteLabel.getText();
    rules.undeclaredWriteInLabel = textFieldUndeclaredWriteInLabel.getText();
    rules.rulesDescription = textFieldRulesDescription.getText();
    config.rules = rules;

    return config;
  }

  private static class ValidatorService extends Service<Void> {

    private final RawContestConfig rawContestConfig;

    ValidatorService(RawContestConfig rawContestConfig) {
      this.rawContestConfig = rawContestConfig;
    }

    @Override
    protected Task<Void> createTask() {
      return new Task<>() {
        @Override
        protected Void call() {
          new ContestConfig(rawContestConfig).validate();
          return null;
        }
      };
    }
  }

  private static class TabulatorService extends Service<Void> {

    @Override
    protected Task<Void> createTask() {
      return new Task<>() {
        @Override
        protected Void call() {
          Main.executeTabulation(GuiContext.getInstance().getConfig());
          return null;
        }
      };
    }
  }

  private class TextFieldListenerNonNegInt implements ChangeListener<String> {
    // Restricts text fields to non-negative integers

    private final TextField textField;

    TextFieldListenerNonNegInt(TextField textField) {
      this.textField = textField;
    }

    @Override
    public void changed(
        ObservableValue<? extends String> observable, String oldValue, String newValue) {
      if (!newValue.matches("\\d*")) {
        textField.setText(oldValue);
      }
    }
  }
}