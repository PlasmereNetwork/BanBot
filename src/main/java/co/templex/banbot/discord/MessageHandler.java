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

import de.btobastian.javacord.entities.Channel;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.entities.message.embed.EmbedBuilder;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * MessageHandlers provide scheduled message deletion methods.
 */
public class MessageHandler {

    /**
     * A single-threaded scheduler for message deletion.
     */
    private final ScheduledExecutorService selfDeletionExecutorService;

    /**
     * Main constructor for MessageHandler. This serves to instantiate the executor service.
     */
    public MessageHandler() {
        selfDeletionExecutorService = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Sends a self-deleting message to the specified channel.
     *
     * @param channel The channel to send the message to.
     * @param emb     The embed to write to the channel.
     * @param delay   The delay before message deletion.
     * @param unit    The time unit for the specified delay.
     */
    public void sendSelfDeletingMessage(Channel channel, EmbedBuilder emb, int delay, TimeUnit unit) {
        deleteMessage(channel.sendMessage("", emb), delay, unit);
    }

    /**
     * Deletes a specified message after the specified delay.
     *
     * @param message The message to delete.
     * @param delay   The delay before message deletion.
     * @param unit    The time unit for the specified delay.
     */
    public void deleteMessage(Message message, int delay, TimeUnit unit) {
        selfDeletionExecutorService.schedule(message::delete, delay, unit);
    }

    /**
     * Deletes a specified message after the specified delay.
     *
     * @param messageFuture The message future to retrieve, then delete.
     * @param delay         The delay before message deletion.
     * @param unit          The time unit for the specified delay.
     */
    public void deleteMessage(Future<Message> messageFuture, int delay, TimeUnit unit) {
        selfDeletionExecutorService.schedule(() -> messageFuture.get().delete(), delay, unit);
    }
}
