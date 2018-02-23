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

package co.templex.banbot;

import co.templex.banbot.discord.Bot;
import co.templex.banbot.http.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Main class for this library. This may optionally be avoided if custom uses for the HTTP Server/Discord bot are
 * necessary, but this library will likely be used solely as an application.
 */
@SuppressWarnings("WeakerAccess")
public class Main {

    /**
     * Logger for the Main class. This is used solely for startup errors, such as missing properties files.
     */
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Hidden constructor. Instantiation of this class is not permitted.
     */
    private Main() {
        throw new UnsupportedOperationException("Instantiation not permitted.");
    }

    /**
     * Main method for this application. This reads both of the properties files (should they exist) and passes the
     * appropriate properties instances to the Bot and HTTP Server instantiated within this method.
     * <p>
     * Note that this will await the shutdown of both the bot and the http server before shutting down the JVM.
     *
     * @param args The command line arguments. These will be ignored.
     * @throws IOException          If the properties files exist but are unreadable.
     * @throws InterruptedException If the latch is interrupted at any point.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        Properties botProperties = new Properties(), httpProperties = new Properties();
        try (FileInputStream bot = new FileInputStream("bot.properties")) {
            botProperties.load(bot);
        }
        try (FileInputStream http = new FileInputStream("http.properties")) {
            httpProperties.load(http);
        } catch (FileNotFoundException e) {
            logger.warn("Couldn't find http.properties, using defaults.", e);
        }
        CountDownLatch shutdownLatch = new CountDownLatch(2);
        Bot bot = new Bot(botProperties, shutdownLatch);
        bot.start();
        HTTPServer httpServer = new HTTPServer(httpProperties, shutdownLatch);
        httpServer.start();
        shutdownLatch.await();
        System.exit(0);
    }

}
