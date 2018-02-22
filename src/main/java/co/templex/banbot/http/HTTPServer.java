package co.templex.banbot.http;

import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;

import static co.templex.banbot.minecraft.Util.readPathAsString;

public class HTTPServer extends NanoHTTPD {

    private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

    public HTTPServer(Properties properties) {
        super(properties.getProperty("host", "0.0.0.0"), Integer.parseInt(properties.getProperty("port", "8080")));
    }

    public void start() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        logger.info("HTTP Server initialized and started.");
    }

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
