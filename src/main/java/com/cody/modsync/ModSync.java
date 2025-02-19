package com.cody.modsync;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("modsync")
public class ModSync
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public ModSync() {
        // Client only
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
//======================================================================================================================
            // Get update url from config file
            File configFile = new File(System.getProperty("user.dir"), "config" + File.separator + "modsync.txt");

            // If there is a config, check for updates. Otherwise, just launch the GUI for initial setup.
            // If there are no updates, return.
            if (configFile.exists()) {
                URL updateUrl;
                try {
                    updateUrl = toUrl(Files.readString(configFile.toPath()).trim());
                } catch (IOException e) {
                    // TODO: Add message on game startup if the config file is invalid. Don't crash game.
                    LOGGER.error("Failed to read config file.", e);
                    throw new RuntimeException("Failed to read config file.", e);
                }
                //======================================================================================================================
                // Get server mod array
                Set<String> serverModList = new HashSet<>(Arrays.asList(getFromUrl(new URL(updateUrl, "modlist")).split("/")));
                serverModList.add(getFromUrl(new URL(updateUrl, "mod_sync_jar_name")));


                // Get local mod file name array
                File modsDir = new File(System.getProperty("user.dir"), "mods");
                // If modsDir is valid. Otherwise, let the GUI handle it.
                if (modsDir.exists() && modsDir.isDirectory()) {
                    // If mods match server, return. Otherwise, run the updater GUI.
                    String[] localModList = modsDir.list();
                    if (localModList != null && serverModList.equals(new HashSet<>(Arrays.asList(localModList)))) {
                        return;
                    }
                }

            }
            //======================================================================================================================
            // Get the running jar file
            File jarFile = new File(ModList
                    .get()
                    .getModContainerById("modsync")
                    .orElseThrow()
                    .getModInfo()
                    .getOwningFile()
                    .getFile()
                    .getFilePath()
                    .toString()
            );
            //======================================================================================================================
            // Launch the GUI

            if (jarFile.exists()) {
                try {
                    // Get java executable path
                    String javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                    if (System.getProperty("os.name").toLowerCase().contains("win")) {
                        javaPath += ".exe";
                    }
                    // Run the external JAR
                    Runtime.getRuntime().exec(new String[]{javaPath, "-jar", jarFile.getAbsolutePath()});

                    // Gracefully stop the game
                    System.exit(0);
                } catch (IOException e) {
                    // TODO: Add message on game startup if the GUI fails to launch. Don't crash game.
                    LOGGER.error("Failed to launch external GUI.", e);
                    throw new RuntimeException("Failed to launch external GUI.", e);
                }
            } else {
                // TODO: Add message on game startup if the JAR file is missing. Don't crash game.
                LOGGER.error("JAR file not found: " + jarFile.getAbsolutePath());
                throw new RuntimeException("JAR file not found: " + jarFile.getAbsolutePath());
            }
        } catch (Exception ignored) {}
    }

    private static URL toUrl(String urlString) {
        if (urlString == null || urlString.trim().isEmpty()) {
            // TODO: Add message on game startup if the URL is empty. Don't crash game.
            LOGGER.error("URL is empty.");
            throw new RuntimeException("URL is empty.");
        }

        String trimmedUrl = urlString.trim();
        // If the URL doesn't start with a protocol, add "http://"
        if (!trimmedUrl.matches("^(?i)https?://.*")) {
            trimmedUrl = "http://" + trimmedUrl;
        }

        try {
            return new URL(trimmedUrl);
        } catch (MalformedURLException e) {
            // TODO: Add message on game startup if the URL is invalid. Don't crash game.
            LOGGER.error("Invalid URL: " + urlString, e);
            throw new RuntimeException("Invalid URL: " + urlString, e);
        }
    }

    public static String getFromUrl(URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                // TODO: Add message on game startup if the request is bad. Don't crash game.
                LOGGER.error("Failed to get data from " + url + ". Response code: " + responseCode);
                throw new RuntimeException("Failed to get data from " + url + ". Response code: " + responseCode);
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
            // TODO: Add message on game startup if the URL is invalid. Don't crash game.
            LOGGER.error("Failed to get data from " + url, e);
            throw new RuntimeException("Failed to get data from " + url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }
}
