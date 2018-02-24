/*
 * BanBot: A Discord bot and an HTTP server that manages the Templex banlist.
 * Copyright (C) 2018  vtcakavsmoace
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package co.templex.banbot.discord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TODO Document
 */
public class LogWatcher implements Runnable {

    /**
     * TODO Document
     */
    private static final Logger logger = LoggerFactory.getLogger(LogWatcher.class);

    /**
     * TODO Document
     */
    private final Bot bot;

    /**
     * TODO Document
     */
    private final ExecutorService watchServiceExecutor;

    /**
     * TODO Document
     */
    LogWatcher(Bot bot) {
        this.bot = bot;
        this.watchServiceExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * TODO Document
     */
    public void shutdown() {
        watchServiceExecutor.shutdownNow();
    }

    /**
     * TODO Document
     */
    public void watch() {
        watchServiceExecutor.submit(this);
    }

    /**
     * TODO Document
     */
    @Override
    public void run() {
        Path path = Paths.get(System.getProperty("user.dir"), "logs");
        File latestLog = new File(path.toFile(), "latest.log");
        BufferedReader reader = null;
        try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
            if (latestLog.exists()) {
                reader = new BufferedReader(new FileReader(latestLog));
                //noinspection StatementWithEmptyBody
                while (reader.readLine() != null) ;
            }
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            WatchKey wk;
            do {
                wk = watchService.take();
                for (WatchEvent<?> event : wk.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    if (((Path) event.context()).endsWith("latest.log")) {
                        if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            if (reader != null) {
                                reader.close();
                                reader = null;
                            }
                            continue;
                        }
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            reader = new BufferedReader(new FileReader(latestLog));
                        }
                        if (reader != null) {
                            for (String line; (line = reader.readLine()) != null; ) {
                                checkLineForBanlistModification(line);
                            }
                        }
                    }
                }

                if (!wk.reset()) {
                    logger.warn("Watch key was unregistered.");
                }
            } while (!watchServiceExecutor.isShutdown());
            if (reader != null) {
                reader.close();
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Broke out of file update loop.", e);
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e1) {
                    logger.error("Failed to close buffered file reader.", e);
                }
            }
        }
    }

    /**
     * Checks the line passed for a banlist modification. If the line matches the result of a /ban or /pardon
     * execution, it will report so to the appropriate location.
     *
     * @param line The line to check against.
     */
    private void checkLineForBanlistModification(String line) {
        if (line.length() > 33) { // ex. "[03:05:13] [Server thread/INFO]: "
            line = line.substring(33);
            String[] splitLine = line.split(": ");
            if (line.startsWith("Banned")) {
                bot.reportBan(splitLine[0].substring(7), "Server", splitLine[1]);
            } else if (line.startsWith("Unbanned")) {
                bot.reportPardon(splitLine[0].substring(9), "Server");
            } else if (line.matches("\\[.*: Banned .*:.*]")) {
                bot.reportBan(splitLine[1].substring(7), splitLine[0].substring(1), splitLine[2].substring(0, splitLine[2].length() - 1));
            } else if (line.matches("\\[.*: Unbanned .*]")) {
                bot.reportPardon(splitLine[1].substring(9, splitLine[1].length() - 1), splitLine[0].substring(1));
            }
        }
    }

}
