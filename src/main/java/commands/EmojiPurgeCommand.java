package commands;

import de.btobastian.sdcf4j.Command;
import de.btobastian.sdcf4j.CommandExecutor;
import discord.TwoDee;
import logic.PlotPointEnhancementHelper;
import logic.RandomColor;
import org.apache.commons.math3.util.Pair;
import org.javacord.api.DiscordApi;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.Reaction;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EmojiPurgeCommand implements CommandExecutor {

    @Command(async = true, aliases = {"~emojipurge", "~killeverylastoneofthem", "~scorchedearth", "~removeemojis", "~purge"}, description = "Removes all plot point enhancement emojis from this channel", usage = "~removeemojis [all]")
    public void purgeEmojis(String[] params, DiscordApi api, MessageAuthor author, Message message, TextChannel channel, Server server) {
        boolean removeFromAllChannels = params.length == 1 && params[0].equals("all") || message.getContent().equals("~killeverylastoneofthem") || message.getContent().equals("~scorchedearth");
        AtomicInteger totalMessages = new AtomicInteger();
        AtomicInteger totalEmojis = new AtomicInteger();
        if (removeFromAllChannels) {
            //Remove all of the emojis and then send an embed to the channel the command was used in
            EmbedBuilder emojiEmbedBuilder = (new EmbedBuilder()
                    .setColor(RandomColor.getRandomColor())
                    .setAuthor(author)
                    .setTitle(TwoDee.getServerwideEmojiRemovalMessage()));
            for (ServerTextChannel serverTextChannel : server.getTextChannels()) {
                String textChannelName = serverTextChannel.getName();
                removeAllPlotPointEmojisFromChannel(serverTextChannel).thenAccept(emojiInfoPair -> {
                    totalMessages.addAndGet(emojiInfoPair.getKey());
                    totalEmojis.addAndGet(emojiInfoPair.getValue());
                    if (!(emojiInfoPair.getKey() == 0 || emojiInfoPair.getValue() == 0)) {
                        emojiEmbedBuilder
                                .addField(textChannelName, "Removed " + emojiInfoPair.getValue() + " reactions from " + emojiInfoPair.getKey() + " messages");
                    }
                });
            }
            CompletableFuture<Message> emojiRemovalMessage = new MessageBuilder().setEmbed(emojiEmbedBuilder).send(channel);
            emojiRemovalMessage.thenAccept(StatisticsCommand::addCancelReactToMessage);
        } else {
            removeAllPlotPointEmojisFromChannel(channel);
        }
    }

    /**
     * Removes all enhancement emojis in a channel
     *
     * @param channel The channel to remove emojis from
     * @return A pair with the left side as the number of messages and the right side as the number of emojis that is completed when all of the emojis have been removed
     */
    private CompletableFuture<Pair<Integer, Integer>> removeAllPlotPointEmojisFromChannel(TextChannel channel) {
        ArrayList<Message> enhancementEmojiMessageList = getMessagesWithEnhancementEmojis(channel);
        int messagesToClear = enhancementEmojiMessageList.size();
        int emojis = getNumberOfEmojisToRemove(enhancementEmojiMessageList);
        if (!(messagesToClear == 0 || emojis == 0)) {
            CompletableFuture<Message> messageCompletableFuture = new MessageBuilder()
                    .setContent("Removing " + emojis + " emojis from " + messagesToClear + " messages!")
                    .send(channel);
            return messageCompletableFuture.thenCompose(message -> clearReactionsFromChannel(enhancementEmojiMessageList, messagesToClear, emojis, message));
        }
        return CompletableFuture.completedFuture(new Pair<>(messagesToClear, emojis));
    }

    /**
     * Helper function to clear all of the roll enhancement reacts given the channel
     *
     * @param enhancementEmojiMessageList The list of messages to clear reacts from
     * @param messagesToClear             The number of messages to clear reacts from
     * @param emojis                      The number of reacts that will be cleared
     * @param progressMessage             The message to be edited to show progress
     * @return A CompletableFuture<Void> that will be fulfilled when all reacts from the channel have been cleared
     */
    private CompletableFuture<Pair<Integer, Integer>> clearReactionsFromChannel(ArrayList<Message> enhancementEmojiMessageList, int messagesToClear, int emojis, Message progressMessage) {
        AtomicInteger current = new AtomicInteger();
        DecimalFormat df = new DecimalFormat("0.##");
        ArrayList<CompletableFuture<Void>> completedRemovalFutures = new ArrayList<>();
        enhancementEmojiMessageList.forEach(message -> {
            CompletableFuture<Void> voidCompletableFuture = PlotPointEnhancementHelper.removeEnhancementEmojis(message);
            completedRemovalFutures.add(voidCompletableFuture.thenAccept(aVoid -> {
                current.getAndIncrement();
                completedRemovalFutures.add(progressMessage.edit("Removing " + emojis + " emojis from " + messagesToClear + " messages! " + current + "/" + messagesToClear + " (" + df.format((double) current.get() / messagesToClear * 100) + "%)"));
            }));
        });
        return CompletableFuture.allOf(completedRemovalFutures.toArray(new CompletableFuture[0])).thenCompose(aVoid ->
                //Add delete emoji when done
                progressMessage.edit(progressMessage.getContent() + "\nDone!").thenCompose(aVoid1 -> StatisticsCommand.addCancelReactToMessage(progressMessage).thenApply(aVoid2 -> new Pair<>(messagesToClear, emojis))));
    }

    /**
     * Gets the number of enhancement emoji in the list of messages
     *
     * @param messageList The list of messages to search
     */
    private int getNumberOfEmojisToRemove(ArrayList<Message> messageList) {
        PlotPointEnhancementHelper helper = new PlotPointEnhancementHelper();
        AtomicInteger emojisInList = new AtomicInteger();
        messageList.stream()
                .map(Message::getReactions)
                .forEach(reactions -> reactions.stream()
                        .filter(Reaction::containsYou)
                        .filter(reaction -> helper.isEmojiEnhancementEmoji(reaction.getEmoji()))
                        .forEach(reaction -> emojisInList.addAndGet(reaction.getCount())));
        return emojisInList.get();
    }

    /**
     * Gets all of the emojis with enhancement emojis
     *
     * @param channel The channel to search for emojis
     * @return An arraylist of messages with enhancement emojis
     */
    private ArrayList<Message> getMessagesWithEnhancementEmojis(TextChannel channel) {
        PlotPointEnhancementHelper helper = new PlotPointEnhancementHelper();

        Stream<Message> allMessagesInChannel = channel.getMessagesAsStream();
        Stream<Message> filteredStream = allMessagesInChannel
                .filter(message1 -> message1.getReactions()
                        .stream()
                        .map(Reaction::getEmoji)
                        .anyMatch(helper::isEmojiEnhancementEmoji));
        return filteredStream.collect(Collectors.toCollection(ArrayList::new));
    }
}
