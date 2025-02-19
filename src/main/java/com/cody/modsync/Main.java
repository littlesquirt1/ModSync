package com.cody.modsync;

import org.openjdk.nashorn.internal.scripts.JO;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Main extends JFrame {

    public Main() {
        super("ModSync");
        JOptionPane.showMessageDialog(null, "Modpack update found! Please press OK, and do not close the window that appears!", "ModSync", JOptionPane.INFORMATION_MESSAGE);
//======================================================================================================================
        // Get update url from config file
        File configFile = new File(System.getProperty("user.dir"), "config" + File.separator + "modsync.txt");
        if (!configFile.exists()) {
            createDefaultConfig(configFile);
        }

        URL updateURLTemp = null;
        try {
            updateURLTemp = toUrl(Files.readString(configFile.toPath()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error while reading config file: " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        if (updateURLTemp == null) {
            JOptionPane.showMessageDialog(null, "Could not read config file!", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Lambda does not like non-final variables
        final URL updateURL = updateURLTemp;
//======================================================================================================================
        // Get running jar file
        File runningJarTemp = null;
        try {
            runningJarTemp = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to get running JAR file: " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Because .equals complains about not being final
        final File runningJar = runningJarTemp;
//======================================================================================================================
        // Check for modsync updates
        String serverModSyncClientName = getFromUrl(resolve(updateURL, "mod_sync_jar_name"));
        if (serverModSyncClientName != null && !serverModSyncClientName.equals(runningJar.getName())) {
            JOptionPane.showMessageDialog(null, "ModSync update found! ModSync will now update itself!", "ModSync", JOptionPane.INFORMATION_MESSAGE);
            File updater;
            try {
                updater = downloadFile(new URL("https://github.com/littlesquirt1/JarUpdater/releases/download/1.2/JarUpdater-1.2.jar"), new File(System.getProperty("java.io.tmpdir")));
            } catch (MalformedURLException e) {
                JOptionPane.showMessageDialog(null, "Failed to download updater: " + e, "Error", JOptionPane.ERROR_MESSAGE);
                throw new RuntimeException("Unexpected error", e);
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                        "-jar",
                        updater.getAbsolutePath(),
                        // Args for updater
                        runningJar.getAbsolutePath(),
                        resolve(updateURL, "mod_sync_jar").toString()

                );
                pb.start();
                System.exit(0);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Failed to run updater: " + e, "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }
//======================================================================================================================
        // Get server mods list
        Set<String> remoteMods = Arrays.stream(getFromUrl(resolve(updateURL, "modlist")).split("/"))
                .collect(Collectors.toSet());
//======================================================================================================================
        // Get mods directory
        File modsDir = new File(System.getProperty("user.dir"), "mods");
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            if (!modsDir.mkdirs()) {
                JOptionPane.showMessageDialog(null, "Mods directory doesn't exist!", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }

        File[] modDirList = modsDir.listFiles();
        if (modDirList == null) {
            JOptionPane.showMessageDialog(null, "Failed to list mods directory!", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
//======================================================================================================================
        // Diff check
        Set<File> localMods = Arrays.stream(modDirList)
                .filter(file -> !file.getAbsolutePath().equals(runningJar.getAbsolutePath()))
                .collect(Collectors.toSet());

        Set<File> toRemove = new HashSet<>(localMods);
        toRemove.removeIf(file -> remoteMods.contains(file.getName()));

        Set<String> toDownload = new HashSet<>(remoteMods);
        toDownload.removeIf(name -> localMods.stream().anyMatch(file -> file.getName().equals(name)));
// =====================================================================================================================
        // Create gui
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 100);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(3, 1));
        setVisible(true);
        add(new Label("Modpack update found!", Label.CENTER));

        JLabel statusLabel = new JLabel();
        add(statusLabel);

        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        add(progressBar);
//======================================================================================================================
        // Delete mods
        // TODO: Still deletes mods that don't need to be.
        CompletableFuture.runAsync(() -> {
            int totalFiles = toRemove.size();
            int i = 0;
            for (File file : toRemove) {
                i++;
                statusLabel.setText("Deleting " + file.getName() + " (" + i + "/" + totalFiles + ")");
                if (!file.delete()) {
                    JOptionPane.showMessageDialog(null, "Failed to delete file " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
                progressBar.setValue((int) ((i / (double) toRemove.size()) * 100));
            }
        }).thenRun(() -> CompletableFuture.runAsync(() -> {
//======================================================================================================================
            // Download new mods
            URL modsURL = resolve(updateURL, "mods");
            progressBar.setValue(0);
            int i = 0;
            for (String mod : toDownload) {
                i++;
                statusLabel.setText("Downloading " + mod + " (" + i + "/" + toDownload.size() + ")");
                downloadFile(resolve(modsURL, "mods/" + mod), modsDir);
                progressBar.setValue((int) ((i / (double) toDownload.size()) * 100));
            }
        }).thenRun(() -> {
//======================================================================================================================
            // Verify the mod lists match
            String[] newMods = modsDir.list();
            if (newMods == null) {
                JOptionPane.showMessageDialog(null, "Failed to list mods directory after download! The modpack may be wrong. Please relaunch the game!", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            Set<String> newModsList = new HashSet<>();
            Arrays.stream(newMods)
                    .filter(fileName -> !fileName.equals(runningJar.getName()))
                    .forEach(newModsList::add);

            if (!newModsList.equals(remoteMods)) {
                JOptionPane.showMessageDialog(null, "Mod list does not match server after download! The modpack may be wrong. Please relaunch the game!", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
//======================================================================================================================
            // Success
            JOptionPane.showMessageDialog(null, "Modpack updated! You may now relaunch Minecraft!", "ModSync", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Main();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error: " + e, "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static URL toUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "URL is empty!", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        String trimmedUrl = urlString.trim();
        // If the URL doesn't start with a protocol, add "http://"
        if (!trimmedUrl.matches("^(?i)https?://.*")) {
            trimmedUrl = "http://" + trimmedUrl;
        }

        // The URL constructor will throw MalformedURLException if the URL is invalid.
        try {
            return new URL(trimmedUrl);
        } catch (MalformedURLException e) {
            JOptionPane.showMessageDialog(null, "Invalid URL: " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return null;
        }
    }

    private static URL resolve(URL url, String path) {
        try {
            return new URL(url, path);
        } catch (MalformedURLException e) {
            JOptionPane.showMessageDialog(null, "Failed to resolve URL: " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return null;
        }
    }

    private static void createDefaultConfig(File file) {
        File configDir = file.getParentFile();
        if (!configDir.exists() || !configDir.isDirectory()) {
            // Create directory, and run the following code if it fails
            if (!configDir.mkdirs()) {
                JOptionPane.showMessageDialog(null, "Error while creating config file directory!", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }

        String url;
        while (true) {
            Object input = JOptionPane.showInputDialog(null, "Please enter the update URL. If you are unsure, ask the server admin.", "ModSync", JOptionPane.PLAIN_MESSAGE);
            if (input == null) {
                System.exit(0);
            }
            if (input instanceof String inputString && !inputString.isEmpty()) {
                url = inputString;
                break;
            }
        }
        url = toUrl(url).toString();
        try {
            Files.writeString(file.toPath(), url);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Error while writing to config file: " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        JOptionPane.showMessageDialog(null, "Config file created successfully!", "ModSync", JOptionPane.INFORMATION_MESSAGE);
        System.exit(0);

    }
    private static String getFromUrl(URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                JOptionPane.showMessageDialog(null, "Received error code " + responseCode + " from " + url, "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }

            // Read response body
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to get data from " + url + ": " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // Assumes that the directory exists and is a directory, not a file
    private static File downloadFile(URL url, File directory) {

        String fileName = new File(url.getPath()).getName();
        File outputFile = new File(directory, fileName);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            InputStream in = connection.getInputStream();
            Files.copy(in, outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return outputFile;
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Failed to download file " + fileName + ": " + e, "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
/*
Server endpoints:

version
    returns the latest version integer

modlist
    returns a list of files in the folder, separated by \n
 */