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

/**
 * TODO Prepare for documentation
 */
@SuppressWarnings("WeakerAccess")
public class Main {

    /**
     * TODO Prepare for documentation
     */
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * TODO Prepare for documentation
     */
    private Main() {
        throw new UnsupportedOperationException("Instantiation not permitted.");
    }

    /**
     * TODO Prepare for documentation
     *
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Properties botProperties = new Properties(), httpProperties = new Properties();
        try (FileInputStream bot = new FileInputStream("bot.properties")) {
            botProperties.load(bot);
        }
        try (FileInputStream http = new FileInputStream("http.properties")) {
            httpProperties.load(http);
        } catch (FileNotFoundException e) {
            logger.warn("Couldn't find http.properties, using defaults.", e);
        }
        Bot bot = new Bot(botProperties);
        bot.setup();
        HTTPServer httpServer = new HTTPServer(httpProperties);
        httpServer.start();
    }

}
