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
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static co.templex.banbot.Util.readPathAsString;

/**
 * HTTP Server implementation for hosting JUST the banlist. This server will return nothing but the banlist or, in the
 * case where the banlist is not readable, error code 500 and an appropriate message.
 */
public class HTTPServer extends NanoHTTPD {

    /**
     * The logger instance for all instances of HTTPServer. This serves solely for debug purposes.
     */
    private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

    /**
     * Shutdown latch for the Bot instance.
     */
    private final CountDownLatch shutdownLatch;

    /**
     * A boolean representing the shutdown state of the HTTP server.
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Main constructor for the HTTP Server class. Accepts a properties instance and a countdown latch instance. Note
     * that the properties instance passed may have no entries; the defaults for the HTTP Server is hosting at
     * 0.0.0.0:8080, which is target functionality for most uses.
     * <p>
     * Users may optionally specify a host with the "host" property and specify a port with the "port" property within
     * the properties file. The properties file may optionally not exist, but should be named "http.properties" if
     * custom specification is required.
     *
     * @param properties    The properties associated with this HTTP Server instance. This may be empty.
     * @param shutdownLatch The shutdown latch associated with this HTTP Server instance.
     */
    public HTTPServer(@NonNull Properties properties, @NonNull CountDownLatch shutdownLatch) {
        super(properties.getProperty("host", "0.0.0.0"), Integer.parseInt(properties.getProperty("port", "8080")));
        this.shutdownLatch = shutdownLatch;
    }

    /**
     * Establishes and runs the HTTP Server. The server will begin to listen for connections as specified in the
     * constructor.
     *
     * @throws IOException Iff the port is already used on the specified host.
     */
    public void start() throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop)); // trap for shutdown
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        logger.info("HTTP Server initialized and started.");
    }

    /**
     * Shutdown method for the HTTP server. This is only overridden to ensure that the countdown latch is triggered.
     */
    @Override
    public void stop() {
        if (!shutdown.getAndSet(true)) {
            super.stop();
            shutdownLatch.countDown();
        }
    }

    /**
     * Serve method for this HTTP server, which either serves the banlist or an error code 500 with an appropriate
     * message. This implements NanoHTTPD#serve.
     *
     * @param session The HTTP session. This is unused by this implementation.
     * @return response The response sent to the client.
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
