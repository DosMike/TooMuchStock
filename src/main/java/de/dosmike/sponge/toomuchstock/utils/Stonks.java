package de.dosmike.sponge.toomuchstock.utils;

import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Stonks {

    double[] history;
    double[] deltas;

    public Stonks(int length) {
        history = new double[length];
        Arrays.fill(history, 1.0); //default price multiplier is 1
        deltas = new double[length];
    }

    //update value -0
    public void update(double value) {
        history[0] = 1+value; //pushed value is discrepancy, viewer is probably interested in multiplier
        deltas[0] = history[0]-history[1];
    }
    //push all values further down, -0 will remain
    public void push() {
        for (int i = history.length-1; i>0; i--) {
            deltas[i] = deltas[i-1];
            history[i] = history[i-1];
        }
        deltas[0] = history[0]-history[1];
    }

    /** @return true if history is all boring 1s */
    public boolean isIdle() {
        for (double d : history) if (Math.abs(d-1.0) > Math.ulp(1.0)) return false;
        return true;
    }

    private double getMax() {
        double m = Double.MIN_VALUE;
        for (double d : history)
            if (d > m) m = d;
        return m;
    }
    private double getMin() {
        double m = Double.MAX_VALUE;
        for (double d : history)
            if (d < m) m = d;
        return m;
    }

    private final DecimalFormat valueFormat = new DecimalFormat("0.###");
    public List<Text> linify(int lines, TextColor color, boolean fixWidths) {
        ArrayList<Text> l = new ArrayList<>(lines);
        char[] steps = new char[]{ '\u2588','\u2581','\u2582','\u2583','\u2584','\u2585','\u2586','\u2587' };
        String empty;
        if (fixWidths) empty = "` ,";
        else empty = " "; //displayed in console/terminal
        int[] values = new int[history.length];
        int maxValue = lines*8;
        double min = getMin(), max = getMax(), span = max-min;

        if (span <= Math.ulp(max)) { //"
            // float around 50% if nothing is happening
            min = Math.max((max+min)/2-0.5, 0);
            max = min + 1;
            span = 1f; // prevent div0
        }
        span /= maxValue; // 0 - maxValue range when dividing below
        for (int i = 0; i < history.length; i++)
            values[i] = (int)Math.round((history[i]-min)/span);

        for (int i = lines-1; i>=0; i--) { // high value printed first (top)
            Text.Builder builder = Text.builder();
            for (int c = history.length-1; c >= 0 ; c--) { //now to the right, past to the left
                if (values[c] == 0 || values[c] <= i*8)
                    builder.append(Text.builder(empty).color(TextColors.DARK_GRAY).onHover(TextActions.showText(Text.of("Value: ",valueFormat.format(history[c]),"\nDelta: ",valueFormat.format(deltas[c])))).build());
                else if (values[c] >= (i+1)*8)
                    builder.append(Text.builder(steps[0]).color(color).onHover(TextActions.showText(Text.of("Value: ",valueFormat.format(history[c]),"\nDelta: ",valueFormat.format(deltas[c])))).build());
                else
                    builder.append(Text.builder(steps[values[c]%8]).color(color).onHover(TextActions.showText(Text.of("Value: ",valueFormat.format(history[c]),"\nDelta: ",valueFormat.format(deltas[c])))).build());
            }
            if (i == 0)
                builder.append(Text.of(" _ ", valueFormat.format(min)));
            else if (i == lines-1)
                builder.append(Text.of(" ￣ ", valueFormat.format(max)));
            l.add(builder.build());
        }
        String spacer = "--------------------";
        l.add(Text.of(history.length, "min ago   <"+spacer+">   now"));
        return l;
    }

    public void print(CommandSource src) {
        linify(4, TextColors.RED, src instanceof Player).forEach(src::sendMessage);
    }
}
