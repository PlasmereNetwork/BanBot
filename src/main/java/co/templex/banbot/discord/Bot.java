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

import com.google.common.util.concurrent.FutureCallback;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static co.templex.banbot.Util.generateEmbedBuilder;

/**
 * Bot class, which is instantiated to create a controller for the Discord bot located in the Templex Discord.
 */
public class Bot {

    /**
     * The logger instance for all instances of Bot. This serves solely for debug purposes.
     */
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    /**
     * The DiscordAPI instance for this bot. Bots need one API instance per Bot instance.
     */
    private final DiscordAPI api;

    /**
     * This is the Server reference for the Templex Discord server. Note that this is held within an atomic reference
     * for thread safety issues that may crop up later.
     */
    private final AtomicReference<Server> templexDiscord = new AtomicReference<>();

    /**
     * This is the channel which represents the "all staff" channel in Templex. Note that this is held within an atomic
     * reference for thread safety issues that may crop up later.
     */
    private final AtomicReference<Channel> allstaffChannel = new AtomicReference<>(null);

    /**
     * This is the executor service which handles all IO from Discord. Note that this is held within an atomic reference
     * for thread safety issues that may crop up later.
     */
    private final AtomicReference<ExecutorService> exec = new AtomicReference<>();

    /**
     * This is the executor service which handles the watch service checking the logs directory of the minecraft server.
     * Note that this is held within an atomic reference for thread safety issues that may crop up later.
     */
    private final AtomicReference<ExecutorService> watchServiceExecutor = new AtomicReference<>();

    /**
     * This is the calendar reference which marks the start time of the bot. Note that this is held within an atomic
     * reference for thread safety issues that may crop up later.
     */
    private final AtomicReference<Calendar> startTime = new AtomicReference<>();

    /**
     * A boolean representing the shutdown state of the bot.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Shutdown latch for the Bot instance.
     */
    private final CountDownLatch shutdownLatch;

    /**
     * Main constructor for the Bot class. A properties instance containing a valid Discord API token called "token".
     *
     * @param botProperties The properties for this Bot.
     * @param shutdownLatch The shutdown latch associated with this bot.
     */
    public Bot(@NonNull Properties botProperties, @NonNull CountDownLatch shutdownLatch) {
        this.api = Javacord.getApi(botProperties.getProperty("token"), true);
        this.shutdownLatch = shutdownLatch;
    }

    /**
     * Shutdown method for the bot. This should always be called upon shutdown.
     */
    private void destroy() {
        if (!shutdown.getAndSet(true)) {
            logger.info("Shutting down...");
            api.disconnect();
            allstaffChannel.set(null);
            exec.get().shutdownNow();
            watchServiceExecutor.get().shutdownNow();
            shutdownLatch.countDown();
        }
    }

    /**
     * Returns the ready state of the bot. This isn't used by our Main, but may be by others.
     *
     * @return ready The ready state of the bot.
     */
    @SuppressWarnings("unused")
    public boolean isReady() {
        return !shutdown.get() && exec.get() != null && api != null && allstaffChannel.get() != null;
    }

    /**
     * Establishes and runs the bot, adding hooks into appropriate listeners. Note that there is quite a lot of
     * background threading that occurs when the API is established successfully.
     */
    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::destroy)); // trap for shutdown
        exec.set(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        watchServiceExecutor.set(Executors.newSingleThreadExecutor());
        startTime.set(Calendar.getInstance());
        api.connect(new FutureCallback<DiscordAPI>() {
            @Override
            public void onSuccess(DiscordAPI result) {
                templexDiscord.set(api.getServerById("162683952295837696"));
                allstaffChannel.set(api.getChannelById("162685956913233921"));
                String version = Bot.this.getClass().getPackage().getImplementationVersion();
                EmbedBuilder emb = generateEmbedBuilder("Templex Ban Bot",
                        "Templex Ban Bot version " + version + " initialized.", null, null, null, Color.GREEN);
                allstaffChannel.get().sendMessage("", emb);
                api.registerListener(new GeneralizedListener());
                api.setGame("with the fates of users.");
                watchServiceExecutor.get().submit(() -> {
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
                        } while (!watchServiceExecutor.get().isShutdown());
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
                });
                logger.info("Templex Ban Bot version " + version + " initialized.");
            }

            @Override
            public void onFailure(Throwable t) {
                logger.error("Failed to start the bot!", t);
                destroy();
            }

        });
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
                reportBan(splitLine[0].substring(7), "Server", splitLine[1]);
            } else if (line.startsWith("Unbanned")) {
                reportPardon(splitLine[0].substring(9), "Server");
            } else if (line.matches("\\[.*: Banned .*:.*]")) {
                reportBan(splitLine[1].substring(7), splitLine[0].substring(1), splitLine[2].substring(0, splitLine[2].length() - 1));
            } else if (line.matches("\\[.*: Unbanned .*]")) {
                reportPardon(splitLine[1].substring(9, splitLine[1].length() - 1), splitLine[0].substring(1));
            }
        }
    }

    /**
     * Reports a ban to the target server.
     * <p>
     * Note that this will very likely change locations during the refactoring process.
     *
     * @param banned The banned user.
     * @param banner The banning user.
     * @param reason The reason for the ban.
     */
    private void reportBan(String banned, String banner, String reason) {
        allstaffChannel.get().sendMessage("", generateEmbedBuilder(
                "Ban Report",
                String.format(
                        "User %s was banned on %s with reason \"%s\".",
                        banned,
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        reason
                ),
                String.format(
                        "Ban issued by %s",
                        banner
                ),
                null,
                null,
                Color.RED
        ));
        logger.info(String.format("Reported ban of user %s", banned));
    }

    /**
     * Reports a pardon to the target server.
     * <p>
     * Note that this will very likely change locations during the refactoring process.
     *
     * @param pardoned The pardoned user.
     * @param pardoner The pardoning user.
     */
    private void reportPardon(String pardoned, String pardoner) {
        allstaffChannel.get().sendMessage("", generateEmbedBuilder(
                "Pardon Report",
                String.format(
                        "User %s was pardoned on %s.",
                        pardoned,
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
                ),
                String.format(
                        "Pardon issued by %s",
                        pardoner
                ),
                null,
                null,
                Color.YELLOW
        ));
        logger.info(String.format("Reported pardon of user %s", pardoned));
    }

    /**
     * Listener implementation used by the Discord bot. This hooks into the MessageCreateListener for the Discord API.
     * <p>
     * This particular implementation listens for the .ban or .pardon commands in the channel designated for in-discord
     * banning.
     * <p>
     * Note that this will very likely change locations during the refactoring process.
     */
    private class GeneralizedListener implements MessageCreateListener {

        /**
         * Implementation of the MessageCreateListener#onMessageCreate method. This particular implementation listens
         * for the .ban or .pardon commands in the channel designated for in-discord banning.
         *
         * @param discordAPI The Discord API instance associated with this event.
         * @param message    The message associated with this event.
         */
        @Override
        public void onMessageCreate(DiscordAPI discordAPI, Message message) {
            if (message.getChannelReceiver().getId().equals(allstaffChannel.get().getId()) && (message.getContent().startsWith(".ban ") || message.getContent().startsWith(".pardon "))) {
                String command = message.getContent().substring(1).replaceAll("\'", "\"'\"'\"");
                String[] commandSplit = command.split(" ");
                boolean commandType = commandSplit[0].equals("ban");
                if (commandSplit.length < 2) { // shouldn't happen
                    message.getChannelReceiver().sendMessage("", generateEmbedBuilder(
                            String.format("Minecraft %s Error", commandType ? "Ban" : "Pardon"),
                            "Insufficient arguments.",
                            null,
                            null,
                            null,
                            Color.RED
                    ));
                    return;
                }
                String player = commandSplit[1];
                try {
                    String executedCommand = String.format("./write_to_server %s\\n", command);
                    logger.info(String.format("Executing raw command \"%s\"", executedCommand));
                    Runtime.getRuntime().exec(executedCommand);
                } catch (IOException e) {
                    logger.error("Unable to execute ban command.", e);
                    String exception;
                    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                        try (PrintStream stream = new PrintStream(outputStream)) {
                            e.printStackTrace(stream);
                            exception = outputStream.toString();
                        }
                    } catch (IOException e1) {
                        e1.printStackTrace();
                        exception = null;
                    }
                    message.getChannelReceiver().sendMessage("", generateEmbedBuilder(
                            String.format("Minecraft %s Error", commandType ? "Ban" : "Pardon"),
                            String.format("Was not able to %s %s due to process exception:\n%s", commandType ? "ban" : "pardon", player, exception),
                            null,
                            null,
                            null,
                            Color.RED
                    ));
                    return;
                }
                message.getChannelReceiver().sendMessage("", generateEmbedBuilder(
                        String.format("Minecraft %s", commandType ? "Ban" : "Pardon"),
                        String.format("Successfully %s %s.", commandType ? "banned" : "pardoned", player),
                        null,
                        null,
                        null,
                        Color.GREEN
                ));
            }
        }

    }
}
