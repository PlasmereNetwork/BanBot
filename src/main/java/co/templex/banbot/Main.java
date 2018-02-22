package co.templex.banbot;

import co.templex.banbot.discord.Bot;
import co.templex.banbot.http.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private Main() {
        throw new UnsupportedOperationException("Instantiation not permitted.");
    }

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
