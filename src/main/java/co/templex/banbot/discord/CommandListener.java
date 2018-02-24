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

import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static co.templex.banbot.Util.generateEmbedBuilder;

/**
 * Listener implementation used by the Discord bot. This hooks into the Discord API instance as a MessageCreateListener.
 * <p>
 * This particular implementation listens for the .ban or .pardon commands in the channel designated for in-discord
 * banning.
 * <p>
 * Note that this will very likely change locations during the refactoring process.
 */
class CommandListener implements MessageCreateListener {

    /**
     * The logger instance for all instances of CommandListener. This serves solely for debug purposes.
     */
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);

    /**
     * The channel which we are reading from.
     */
    private final Channel channel;

    CommandListener(Channel channel) {
        this.channel = channel;
    }

    /**
     * Implementation of the MessageCreateListener#onMessageCreate method. This particular implementation listens
     * for the .ban or .pardon commands in the channel designated for in-discord banning.
     *
     * @param discordAPI The Discord API instance associated with this event.
     * @param message    The message associated with this event.
     */
    @Override
    public void onMessageCreate(DiscordAPI discordAPI, Message message) {
        if (message.getChannelReceiver().getId().equals(channel.getId()) && (message.getContent().startsWith(".ban ") || message.getContent().startsWith(".pardon "))) {
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
