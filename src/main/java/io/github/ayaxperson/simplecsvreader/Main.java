package io.github.ayaxperson.simplecsvreader;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(final String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());
        } catch (final Exception e) {
            System.err.println("Failed to show UI");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void createAndShowGUI() {
        final JFrame frame = new JFrame("Select CSV");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 130);
        frame.setLayout(new GridBagLayout());

        final GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        final JPanel pathPanel = new JPanel(new BorderLayout(4, 0));
        final JTextField pathField = new JTextField();
        final Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
        final JButton browseButton = new JButton(folderIcon != null ? folderIcon : new JLabel("...").getIcon());
        browseButton.setToolTipText("Browse for file or folder");
        pathPanel.add(pathField, BorderLayout.CENTER);
        pathPanel.add(browseButton, BorderLayout.EAST);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(pathPanel, gbc);

        final JCheckBox checkBox = new JCheckBox("Interpret first record as header data");
        gbc.gridy = 1;
        frame.add(checkBox, gbc);

        final JButton okButton = new JButton("OK");
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        frame.add(okButton, gbc);

        browseButton.addActionListener(e -> {
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

            final int result = fileChooser.showOpenDialog(frame);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                pathField.setText(selectedFile.getAbsolutePath());
            }
        });

        okButton.addActionListener(e -> {
            final String path = pathField.getText();

            final boolean isChecked = checkBox.isSelected();

            frame.setVisible(false);

            final CSV csv = readCSV(Path.of(path), isChecked);

            if (csv == null) {
                JOptionPane.showMessageDialog(null, "Failed to parse CSV records. See console for information");
                return;
            }

            createAndShowMainGUI(csv);
        });

        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setVisible(true);
    }

    private void createAndShowMainGUI(final CSV csv) {
        final int columns = csv.headers.length;

        final JFrame frame = new JFrame("CSV Viewer");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        final JPanel columnContainer = new JPanel();
        columnContainer.setLayout(new BoxLayout(columnContainer, BoxLayout.X_AXIS));
        columnContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (int i = 0; i < csv.headers.length; i++) {
            final JPanel columnPanel = new JPanel();
            columnPanel.setLayout(new BoxLayout(columnPanel, BoxLayout.Y_AXIS));
            columnPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

            final JLabel headerLabel = new JLabel(String.format("<html><b>%s</b></html>", csv.headers[i]), SwingConstants.CENTER);
            headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            columnPanel.add(headerLabel);
            columnPanel.add(Box.createVerticalStrut(5));

            for (String entry : csv.records[i]) {
                final String text = entry == null ? "<html><i>missing</i></html>" : entry;

                final JLabel entryLabel = new JLabel(text, SwingConstants.CENTER);

                entryLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                columnPanel.add(entryLabel);
            }

            columnContainer.add(columnPanel);
        }

        final JScrollPane scrollPane = new JScrollPane(columnContainer,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(Math.max(600, columns * 100), 400));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private CSV readCSV(final Path path, final boolean interpretFirstLineAsHeaderNames) {
        String[] csvContent;

        try {
            csvContent = Files.readAllLines(path).toArray(new String[0]);
        } catch (final Exception e) {
            System.err.println("Failed reading dummy file");
            System.err.printf("%s : %s%n", e.getClass().getSimpleName(), e.getMessage());

            return null;
        }

        return CSV.parse(csvContent, interpretFirstLineAsHeaderNames);
    }

    public record CSV(String[] headers, String[][] records) {

        public static CSV parse(final String[] lines, final boolean interpretFirstLineAsHeaderNames) {
            final List<List<String>> raw = new ArrayList<>(new ArrayList<>());

            for (final String line : lines) {
                if (line.isBlank())
                    continue;

                final String[] split = splitCsvLine(line);
                raw.add(new ArrayList<>(Arrays.asList(split)));
            }

            int maxRows = 0;

            for (final List<String> columns : raw) {
                maxRows = Math.max(columns.size(), maxRows);
            }

            for (final List<String> rows : raw) {
                while (rows.size() < maxRows) {
                    rows.add(null);
                }
            }

            int headersAmount = 1;

            for (final List<String> entries : raw) {
                headersAmount = Math.max(headersAmount, entries.size());
            }

            final String[] headers = new String[headersAmount];

            if (interpretFirstLineAsHeaderNames) {
                final List<String> headersList = raw.getFirst();

                for (int i = 0; i < headers.length; i++) {
                    if (i < headersList.size()) {
                        headers[i] = headersList.get(i);
                    } else {
                        headers[i] = Integer.toString(1 + i);
                    }
                }
            } else {
                for (int i = 0; i < headers.length; i++) {
                    headers[i] = Integer.toString(1 + i);
                }
            }

            final int rows = interpretFirstLineAsHeaderNames ? raw.size() - 1 : raw.size();
            final String[][] records = new String[headers.length][rows];

            for (int i = 0; i < rows; i++) {
                final List<String> commaSeparatedValues = raw.get(interpretFirstLineAsHeaderNames ? i + 1 : i);

                for (int j = 0; j < commaSeparatedValues.size(); j++) {
                    final String value = commaSeparatedValues.get(j);
                    records[j][i] = value;
                }
            }

            return new CSV(headers, records);
        }

    }

    public static String[] splitCsvLine(String line) {
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();

        boolean inQuotes = false;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }

            i++;
        }

        result.add(current.toString());

        return result.toArray(new String[0]);
    }

}
