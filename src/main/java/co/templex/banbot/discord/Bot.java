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
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Objects;
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
    private final AtomicReference<Server> targetServer = new AtomicReference<>();

    /**
     * This is the channel which represents the "all staff" channel in Templex. Note that this is held within an atomic
     * reference for thread safety issues that may crop up later.
     */
    private final AtomicReference<Channel> targetChannel = new AtomicReference<>(null);

    /**
     * This is the executor service which handles all IO from Discord. Note that this is held within an atomic reference
     * for thread safety issues that may crop up later.
     */
    private final AtomicReference<ExecutorService> exec = new AtomicReference<>();

    /**
     * TODO Document
     */
    private final String targetServerID;

    /**
     * TODO Document
     */
    private final String targetChannelID;

    /**
     * This is the log watcher instance which watches the specified logfile.
     */
    private LogWatcher logWatcher;

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
        this.shutdownLatch = shutdownLatch;
        this.api = Javacord.getApi(botProperties.getProperty("token"), true);
        this.targetServerID = Objects.requireNonNull(botProperties.getProperty("server-id", null));
        this.targetChannelID = Objects.requireNonNull(botProperties.getProperty("channel-id", null));
    }

    /**
     * Shutdown method for the bot. This should always be called upon shutdown.
     */
    private void destroy() {
        if (!shutdown.getAndSet(true)) {
            logger.info("Shutting down...");
            api.disconnect();
            targetChannel.set(null);
            exec.get().shutdownNow();
            logWatcher.shutdown();
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
        return !shutdown.get() && exec.get() != null && api != null && targetChannel.get() != null;
    }

    /**
     * Establishes and runs the bot, adding hooks into appropriate listeners. Note that there is quite a lot of
     * background threading that occurs when the API is established successfully.
     */
    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::destroy)); // trap for shutdown
        exec.set(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        startTime.set(Calendar.getInstance());
        api.connect(new FutureCallback<DiscordAPI>() {
            @Override
            public void onSuccess(DiscordAPI result) {
                targetServer.set(api.getServerById(targetServerID)); // TODO generify
                targetChannel.set(api.getChannelById(targetChannelID)); // TODO generify
                String version = Bot.this.getClass().getPackage().getImplementationVersion();
                EmbedBuilder emb = generateEmbedBuilder("Templex Ban Bot",
                        "Templex Ban Bot version " + version + " initialized.", null, null, null, Color.GREEN);
                targetChannel.get().sendMessage("", emb);
                api.registerListener(new CommandListener(targetChannel.get()));
                api.setGame("with the fates of users.");
                logWatcher = new LogWatcher(Bot.this);
                logWatcher.watch();
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
     * Reports a ban to the target server.
     * <p>
     * Note that this will very likely change locations during the refactoring process.
     *
     * @param banned The banned user.
     * @param banner The banning user.
     * @param reason The reason for the ban.
     */
    public void reportBan(String banned, String banner, String reason) {
        targetChannel.get().sendMessage("", generateEmbedBuilder(
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
    public void reportPardon(String pardoned, String pardoner) {
        targetChannel.get().sendMessage("", generateEmbedBuilder(
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

}
