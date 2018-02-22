package co.templex.banbot.discord;

import co.templex.banbot.minecraft.BanList;
import co.templex.banbot.minecraft.UserCache;
import com.google.common.util.concurrent.FutureCallback;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static co.templex.banbot.discord.Util.generateEmbedBuilder;
import static co.templex.banbot.minecraft.Util.readBanList;
import static co.templex.banbot.minecraft.Util.readUserCache;

public class Bot {

    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private final Properties botProperties;
    private final DiscordAPI api;
    private final AtomicReference<Channel> allstaffChannel = new AtomicReference<>(null);
    private final AtomicReference<ExecutorService> exec = new AtomicReference<>();
    private final AtomicReference<ExecutorService> watchServiceExecutor = new AtomicReference<>();
    private final AtomicReference<Calendar> startTime = new AtomicReference<>();
    private final AtomicReference<Server> templexDiscord = new AtomicReference<>();


    public Bot(Properties botProperties) throws IOException {
        this.botProperties = botProperties;
        this.api = Javacord.getApi(botProperties.getProperty("token"), true);
    }

    private void destroy() {
        api.disconnect();
        allstaffChannel.set(null);
        exec.get().shutdownNow();
        watchServiceExecutor.get().shutdownNow();
        if (Boolean.getBoolean(botProperties.getProperty("exitOnDisconnect"))) {
            System.exit(0);
        }
    }

    @SuppressWarnings("unused")
    public boolean isReady() {
        return exec.get() != null && api != null && allstaffChannel.get() != null;
    }

    public void setup() {
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
                    Path path = Paths.get(System.getProperty("user.dir"));
                    BanList current, newest;
                    try {
                        current = readBanList(Paths.get(path.toString(), "banned-players.json"));
                    } catch (IOException e) {
                        logger.error("Unable to read banlist; ban functionality will not be usable.", e);
                        return;
                    }
                    try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
                        path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
                        for (WatchKey wk; !watchServiceExecutor.get().isShutdown(); ) {
                            wk = watchService.take();
                            for (WatchEvent<?> event : wk.pollEvents()) {
                                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                                    continue;
                                }
                                if (((Path) event.context()).endsWith("banned-players.json")) {
                                    newest = readBanList((Path) event.context());
                                    if (newest.size() > current.size()) { // Someone was banned
                                        for (BanList.BanListEntry entry : newest) {
                                            if (!current.contains(entry)) {
                                                reportBan(entry);
                                            }
                                        }
                                    }
                                    if (current.size() == newest.size()) { // Someone was pardoned
                                        reportPardon(readUserCache(Paths.get(path.toString(), "usercache.json")).get(0));
                                    }
                                }
                            }

                            if (!wk.reset()) {
                                logger.warn("Watch key was unregistered.");
                                break;
                            } else {
                                Thread.sleep(2000);
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        logger.error("Broke out of file update loop.", e);
                    }
                });
                logger.info("Templex Ban Bot version " + version + " initialized.");
            }

            @Override
            public void onFailure(Throwable t) {
                logger.error("Failed to setup the bot!", t);
                destroy();
            }

        });
    }

    private void reportBan(BanList.BanListEntry entry) {
        allstaffChannel.get().sendMessage("", generateEmbedBuilder(
                "Ban Report",
                String.format(
                        "User %s (%s) was banned on %s with reason \"%s\".",
                        entry.getName(),
                        entry.getUuid(),
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
                        entry.getReason()
                ),
                String.format(
                        "Ban issued by %s",
                        entry.getSource()
                ),
                null,
                null,
                Color.RED
        ));
        logger.info(String.format("Reported ban of user %s", entry.getName()));
    }

    private void reportPardon(UserCache.UserCacheEntry entry) {
        allstaffChannel.get().sendMessage("", generateEmbedBuilder(
                "Pardon Report",
                String.format(
                        "User %s (%s) was pardoned on %s.",
                        entry.getName(),
                        entry.getUuid(),
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
                ),
                null,
                null,
                null,
                Color.YELLOW
        ));
        logger.info(String.format("Reported pardon of user %s", entry.getName()));
    }

    private class GeneralizedListener implements MessageCreateListener {

        @Override
        public void onMessageCreate(DiscordAPI discordAPI, Message message) {
            // TODO
        }

    }
}
