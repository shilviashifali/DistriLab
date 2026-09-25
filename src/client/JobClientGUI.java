package client;

import common.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JobClientGUI is the Client's front door.
 *
 * Flow for every submission:
 *   1. Look up the Bootstrap node in the RMI registry.
 *   2. Ask it who the current coordinator is (bootstrap.getCoordinator()).
 *   3. Call submitJob(request) directly on that coordinator's stub.
 *   4. The coordinator splits the job across active workers, combines the
 *      results, and sends back a single JobResult.
 */
public class JobClientGUI extends JFrame {

    private JComboBox<String> jobTypeCombo;
    private JTextField numbersField;   // used for MAX and PRIMECOUNT (comma-separated)
    private JTextField startField;     // used for PRIMESUM
    private JTextField endField;       // used for PRIMESUM
    private JTextField bootstrapHostField; // which machine Bootstrap is running on
    private JTextArea resultsArea;
    private JPanel rangePanel;
    private JPanel numbersPanel;

    public JobClientGUI() {
        setTitle("Distributed Job Client - Person 3");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(560, 520);
        setLayout(new BorderLayout(10, 10));

        add(buildInputPanel(), BorderLayout.NORTH);
        add(buildResultsPanel(), BorderLayout.CENTER);

        updateInputVisibility();
    }

    private JPanel buildInputPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ---- Bootstrap host (so this client can point at a different machine) ----
        JPanel hostPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        hostPanel.add(new JLabel("Bootstrap host:"));
        bootstrapHostField = new JTextField(Config.DEFAULT_BOOTSTRAP_HOST, 15);
        hostPanel.add(bootstrapHostField);
        panel.add(hostPanel);

        // ---- Job type selector ----
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel("Job type:"));
        jobTypeCombo = new JComboBox<>(new String[]{"MAX", "PRIMESUM", "PRIMECOUNT"});
        jobTypeCombo.addActionListener(e -> updateInputVisibility());
        typePanel.add(jobTypeCombo);
        panel.add(typePanel);

        // ---- Numbers input (MAX / PRIMECOUNT) ----
        numbersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        numbersPanel.add(new JLabel("Numbers (comma-separated):"));
        numbersField = new JTextField(25);
        numbersPanel.add(numbersField);
        panel.add(numbersPanel);

        // ---- Range input (PRIMESUM) ----
        rangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rangePanel.add(new JLabel("Start:"));
        startField = new JTextField(8);
        rangePanel.add(startField);
        rangePanel.add(new JLabel("End:"));
        endField = new JTextField(8);
        rangePanel.add(endField);
        panel.add(rangePanel);

        // ---- Buttons ----
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadCsvButton = new JButton("Load CSV");
        loadCsvButton.addActionListener(this::onLoadCsv);
        buttonPanel.add(loadCsvButton);

        JButton submitButton = new JButton("Submit Job");
        submitButton.addActionListener(this::onSubmit);
        buttonPanel.add(submitButton);
        panel.add(buttonPanel);

        return panel;
    }

    private JPanel buildResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Results Log"));
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        panel.add(new JScrollPane(resultsArea), BorderLayout.CENTER);
        return panel;
    }

    private void updateInputVisibility() {
        String selected = (String) jobTypeCombo.getSelectedItem();
        boolean isRange = "PRIMESUM".equals(selected);
        rangePanel.setVisible(isRange);
        numbersPanel.setVisible(!isRange);
        revalidate();
    }

    private void onLoadCsv(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        String path = fileChooser.getSelectedFile().getAbsolutePath();
        try {
            List<Integer> numbers = CsvLoader.loadNumbersFromCsv(path);
            String joined = numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
            numbersField.setText(joined);
            log("Loaded " + numbers.size() + " numbers from CSV: " + path);
        } catch (IOException ex) {
            log("Failed to load CSV: " + ex.getMessage());
        }
    }

    // Builds a JobRequest from whatever's currently in the input fields,
    // based on the selected job type.
    private JobRequest buildRequest() {
        String jobType = (String) jobTypeCombo.getSelectedItem();
        switch (jobType) {
            case "MAX":
                return new JobRequest(JobRequest.JobType.MAX, parseNumbers(numbersField.getText()));
            case "PRIMECOUNT":
                return new JobRequest(JobRequest.JobType.PRIMECOUNT, parseNumbers(numbersField.getText()));
            case "PRIMESUM":
                int start = Integer.parseInt(startField.getText().trim());
                int end = Integer.parseInt(endField.getText().trim());
                return new JobRequest(JobRequest.JobType.PRIMESUM, start, end);
            default:
                throw new IllegalStateException("Unknown job type: " + jobType);
        }
    }

    // Submits the job over RMI, without freezing the GUI while waiting -
    // the RMI call happens on a background thread via SwingWorker, and
    // the result is displayed once it comes back.
    private void onSubmit(ActionEvent e) {
        final JobRequest request;
        try {
            request = buildRequest();
        } catch (NumberFormatException ex) {
            log("Invalid input - please check your numbers/range and try again.");
            return;
        }

        log("Submitting " + request.getType() + " job to coordinator...");

        new SwingWorker<JobResult, Void>() {
            @Override
            protected JobResult doInBackground() throws Exception {
                String host = bootstrapHostField.getText().trim();
                Registry registry = LocateRegistry.getRegistry(host, Config.DEFAULT_BOOTSTRAP_PORT);
                BootstrapService bootstrap = (BootstrapService) registry.lookup(Config.BOOTSTRAP_NAME);

                WorkerInfo coordinatorInfo = bootstrap.getCoordinator();
                if (coordinatorInfo == null) {
                    throw new IllegalStateException(
                            "No coordinator elected yet - trigger an election on a worker first.");
                }

                WorkerService coordinator = coordinatorInfo.getStub();
                return coordinator.submitJob(request);
            }

            @Override
            protected void done() {
                try {
                    JobResult result = get();
                    log(result.toString());
                } catch (Exception ex) {
                    String message = (ex.getCause() != null) ? ex.getCause().getMessage() : ex.getMessage();
                    log("Job failed: " + message);
                }
            }
        }.execute();
    }

    private List<Integer> parseNumbers(String text) {
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    private void log(String message) {
        resultsArea.append(message + "\n");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new JobClientGUI().setVisible(true));
    }
}