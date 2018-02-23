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

package co.templex.banbot.http;

import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import static co.templex.banbot.Util.readPathAsString;

/**
 * TODO Prepare for documentation
 */
public class HTTPServer extends NanoHTTPD {

    /**
     * TODO Prepare for documentation
     */
    private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

    /**
     * TODO Prepare for documentation
     *
     * @param properties
     */
    public HTTPServer(Properties properties) {
        super(properties.getProperty("host", "0.0.0.0"), Integer.parseInt(properties.getProperty("port", "8080")));
    }

    /**
     * TODO Prepare for documentation
     *
     * @throws IOException
     */
    public void start() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        logger.info("HTTP Server initialized and started.");
    }

    /**
     * TODO Prepare for documentation
     *
     * @param session
     * @return
     */
    @Override
    public Response serve(IHTTPSession session) {
        Response response;
        try {
            response = newFixedLengthResponse(readPathAsString(Paths.get(System.getProperty("user.dir"), "banned-players.json")));
            response.setStatus(new Response.IStatus() {
                @Override
                public String getDescription() {
                    return "OK";
                }

                @Override
                public int getRequestStatus() {
                    return 200;
                }
            });
            response.addHeader("Access-Control-Allow-Origin:", "*");
        } catch (IOException e) {
            logger.warn("Unable to read banned-players.json", e);
            response = newFixedLengthResponse("Unable to fetch banned players list.");
            response.setStatus(new Response.IStatus() {
                @Override
                public String getDescription() {
                    return "Internal Server Error";
                }

                @Override
                public int getRequestStatus() {
                    return 500;
                }
            });
        }
        return response;
    }
}
