package dicerolling;

import discord.TwoDee;
import doom.DoomWriter;
import logic.RandomColor;
import org.javacord.api.entity.message.MessageAuthor;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import sheets.PPManager;
import statistics.resultvisitors.DifficultyVisitor;

import java.util.*;

public class DiceRoller {

    private ArrayList<Integer> regDice = new ArrayList<>();
    private ArrayList<Integer> plotDice = new ArrayList<>();
    private ArrayList<Integer> flat = new ArrayList<>();
    private ArrayList<Integer> keptDice = new ArrayList<>();
    //The amount of doom generated by this dice roll
    private int doom;

    public DiceRoller(String content) {
        //Split up content
        ArrayList<String> args = new ArrayList<>(Arrays.asList(content.split(" ")));
        args.remove("~r");
        //Split dice into regular dice and plot dice
        DiceParameterHandler diceParameterHandler = new DiceParameterHandler(args, regDice, plotDice, flat, keptDice);
        diceParameterHandler.addDiceToPools();
    }

    public int getDoom() {
        return doom;
    }

    public EmbedBuilder generateResults(MessageAuthor author) {
        ArrayList<Integer> diceResults = new ArrayList<>();
        ArrayList<Integer> pdResults = new ArrayList<>();
        ArrayList<Integer> keptResults = new ArrayList<>();
        Random random = new Random();
        //Roll the dice
        rollDice(diceResults, pdResults, keptResults, random);

        //Get top two and dropped dice
        ArrayList<Integer> topTwo = new ArrayList<>();
        ArrayList<Integer> dropped = new ArrayList<>();
        getTopTwo(diceResults, topTwo, dropped);
        int plotResult = getPlotResult(pdResults);
        int flatResult = getFlatResults(flat);
        int keptResult = getKeptResult(keptResults);
        //Sum up total
        int total = getTotal(topTwo, plotResult, flatResult, keptResult);
        //Build embed
        return buildResultEmbed(author, diceResults, pdResults, topTwo, dropped, total, flatResult, keptResults);
    }

    private int getKeptResult(ArrayList<Integer> keptResults) {
        int sum = 0;
        if (!keptResults.isEmpty()) {
            for (int keptVal : keptResults) {
                sum += keptVal;
            }
        }
        return sum;
    }

    private int getFlatResults(ArrayList<Integer> flat) {
        int sum = 0;
        if (!flat.isEmpty()) {
            for (Integer bonus : flat) {
                sum += bonus;
            }
        }

        return sum;
    }

    private int getPlotResult(ArrayList<Integer> pdResults) {
        int plotResult = 0;
        if (!pdResults.isEmpty()) {
            for (int pDice : pdResults) {
                plotResult += pDice;
            }
        }
        return plotResult;
    }

    private EmbedBuilder buildResultEmbed(MessageAuthor author, ArrayList<Integer> diceResults, ArrayList<Integer> pdResults, ArrayList<Integer> topTwo, ArrayList<Integer> dropped, int total, int flatResult, ArrayList<Integer> keptResult) {
        return new EmbedBuilder()
                .setTitle(TwoDee.getRollTitleMessage())
                .setAuthor(author)
                .setColor(RandomColor.getRandomColor())
                .addField("Regular dice", formatResults(diceResults), true)
                .addField("Picked", replaceBrackets(topTwo.toString()), true)
                .addField("Dropped", replaceBrackets(dropped.toString()), true)
                .addField("Plot dice", replaceBrackets(pdResults.toString()), true)
                .addField("Kept dice", replaceBrackets(keptResult.toString()), true)
                .addField("Flat bonuses", Integer.toString(flatResult), true)
                .addField("Total", String.valueOf(total), true)
                .addField("Tier Hit", tiersHit(total));
    }

    private String tiersHit(int total) {
        DifficultyVisitor difficultyVisitor = new DifficultyVisitor();
        if (total < 3) {
            return "None";
        }
        StringBuilder output = new StringBuilder();
        for (Map.Entry<Integer, String> diffEntry : difficultyVisitor.getDifficultyMap().entrySet()) {
            if (difficultyVisitor.generateStageDifficulty(diffEntry.getKey() + 1) > total) {
                output.append(diffEntry.getValue());
                break;
            }
        }
        if (total < 10) {
            return String.valueOf(output);
        }
        for (Map.Entry<Integer, String> diffEntry : difficultyVisitor.getDifficultyMap().entrySet()) {
            if (difficultyVisitor.generateStageExtraordinaryDifficulty(diffEntry.getKey() + 1) > total) {
                output.append(", Extraordinary ").append(diffEntry.getValue());
                break;
            }
        }
        return String.valueOf(output);
    }

    //Bold 1s to show total doom generated. Runs doom increasing method afterwards.
    private String formatResults(ArrayList<Integer> s) {
        StringBuilder resultString = new StringBuilder();
        if (s.size() > 1) {
            for (int i = 0; i < s.size() - 1; i++) {
                if (s.get(i) == 1) {
                    resultString.append("**1**, ");
                } else {
                    resultString.append(s.get(i)).append(", ");
                }
            }
            if (s.get(s.size() - 1) == 1) {
                resultString.append("**1**");
            } else {
                resultString.append(s.get(s.size() - 1));
            }
        } else if (s.size() == 1) {
            if (s.get(0) == 1) {
                resultString.append("**1**");
            } else {
                resultString.append(s.get(0));
            }
        } else {
            return "*none*";
        }
        return resultString.toString();
    }

    // Username is stored as <@!140973544891744256>
    public EmbedBuilder addPlotPoints(MessageAuthor author) {
        if (doom != 0) {
            PPManager manager = new PPManager();
            String userID = author.getIdAsString();
            int oldPP = manager.getPlotPoints(userID);
            int newPP = manager.setPlotPoints(userID, oldPP + 1);
            return new EmbedBuilder()
                    .setAuthor(author)
                    .setDescription(oldPP + " → " + newPP);
        } else {
            return null;
        }
    }

    public EmbedBuilder addDoom(int doomVal) {
        DoomWriter doomWriter = new DoomWriter();
        return doomWriter.addDoom(doomVal);
    }

    private int getTotal(ArrayList<Integer> topTwo, int plotResult, int flatResult, int keptResult) {
        return topTwo.stream().mapToInt(Integer::intValue).sum() + plotResult + flatResult + keptResult;
    }

    private void getTopTwo(ArrayList<Integer> diceResults, ArrayList<Integer> topTwo, ArrayList<Integer> dropped) {
        if (diceResults.size() == 1) {
            topTwo.add(diceResults.get(0));
        } else {
            //Sort ArrayList in descending order
            ArrayList<Integer> sortedResults = new ArrayList<>(diceResults);
            Collections.sort(sortedResults);
            Collections.reverse(sortedResults);
            for (int i = 0; i < sortedResults.size(); i++) {
                if (i < 2) {
                    topTwo.add(sortedResults.get(i));
                } else {
                    dropped.add(sortedResults.get(i));
                }
            }
        }
    }

    //Roll all of the dice. Plot dice have a minimum value of its maximum roll/2
    private void rollDice(ArrayList<Integer> diceResults, ArrayList<Integer> pdResults, ArrayList<Integer> keptResults, Random random) {
        //Roll dice
        rollDie(diceResults, random, regDice);
        //A plot die's minimum value is its number of faces / 2
        for (Integer pDice : plotDice) {
            int pValue = random.nextInt(pDice) + 1;
            if (pValue < pDice / 2) {
                pValue = pDice / 2;
            }
            pdResults.add(pValue);
        }

        //All kept dice are kept with no minimum value
        rollDie(keptResults, random, keptDice);
    }

    private void rollDie(ArrayList<Integer> keptResults, Random random, ArrayList<Integer> keptDice) {
        for (Integer kDice : keptDice) {
            int keptVal = random.nextInt(kDice) + 1;
            keptResults.add(keptVal);
            if (keptVal == 1) {
                doom++;
            }
        }
    }

    //Replaces brackets in the string. If the string is blank, returns "none" in italics
    private String replaceBrackets(String s) {
        String newStr = s.replaceAll("\\[", "").replaceAll("]", "");
        if (newStr.equals("")) {
            return "*none*";
        }
        return newStr;
    }

}
