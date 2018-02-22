package co.templex.banbot;

import co.templex.banbot.discord.Bot;

import java.io.IOException;

public class Main {

    private Main() {
        throw new UnsupportedOperationException("Instantiation not permitted.");
    }

    public static void main(String[] args) throws IOException {
        String propertiesFile;
        if (args.length == 0) {
            propertiesFile = "bot.properties";
        } else {
            propertiesFile = args[0];
        }
        Bot bot = new Bot(propertiesFile);
        bot.setup();
    }

}
