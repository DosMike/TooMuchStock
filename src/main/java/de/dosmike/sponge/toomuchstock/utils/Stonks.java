package de.dosmike.sponge.toomuchstock.utils;

import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColor;
import org.spongepowered.api.text.format.TextColors;

import java.util.ArrayList;
import java.util.List;

public class Stonks {

    double[] history;
    double[] deltas;
    double accu;
    int divider;

    public Stonks(int length) {
        history = new double[length];
        deltas = new double[length];
        accu = 0;
        divider = 0;
    }

    public void tick(double value) {
        accu += value;
        divider ++;
    }
    public void push() {
        for (int i = history.length-1; i>0; i--) {
            deltas[i] = deltas[i-1];
            history[i] = history[i-1];
        }
        if (divider > 0) {
            history[0] = accu / divider;
        } else {
            history[0] = 0;
        }
        deltas[0] = history[0]-history[1];
        accu = 0;
        divider = 0;
    }

    private double getMax() {
        double m = Double.NEGATIVE_INFINITY;
        for (double d : history)
            if (d > m) m = d;
        return m;
    }
    private double getMin() {
        double m = Double.POSITIVE_INFINITY;
        for (double d : history)
            if (d < m) m = d;
        return m;
    }

    public List<Text> linify(int lines, TextColor color) {
        ArrayList<Text> l = new ArrayList<>(lines);
        char[] steps = new char[]{ '\u2588','\u2581','\u2582','\u2583','\u2584','\u2585','\u2586','\u2587' };
        String[] empty = new String[]{" ` ","` `"};
        int[] values = new int[history.length];
        int maxValue = lines*8;
        double min = getMin(), max = getMax(), span = max-min;

        if (span < 1e-24) span = maxValue/2.0; // float around 50% if nothing is happening
        else span /= maxValue; // 0 - maxValue range when dividing below
        for (int i = 0; i < history.length; i++)
            values[i] = (int)Math.round((history[i]-min)/span);

        for (int i = lines-1; i>=0; i--) { // high value printed first (top)
            Text.Builder builder = Text.builder();
            for (int c = history.length-1; c >= 0 ; c--) { //now to the right, past to the left
                if (values[c] == 0 || values[c] <= i*8)
                    builder.append(Text.builder(empty[(i+c)%2]).color(TextColors.DARK_GRAY).onHover(TextActions.showText(Text.of(String.format("Value: %.2f\nDelta: %.2f", history[c], deltas[c])))).build());
                else if (values[c] >= (i+1)*8)
                    builder.append(Text.builder(steps[0]).color(color).onHover(TextActions.showText(Text.of(String.format("Value: %.2f\nDelta: %.2f", history[c], deltas[c])))).build());
                else
                    builder.append(Text.builder(steps[values[c]%8]).color(color).onHover(TextActions.showText(Text.of(String.format("Value: %.2f\nDelta: %.2f", history[c], deltas[c])))).build());
            }
            if (i == 0)
                builder.append(Text.of(String.format("- %.2f", min)));
            else if (i == lines-1)
                builder.append(Text.of(String.format("- %.2f", max)));
            l.add(builder.build());
        }
        l.add(Text.of(history.length, "min ago   <----->   now"));
        return l;
    }

    public void print(CommandSource src) {
        linify(4, TextColors.RED).forEach(src::sendMessage);
    }
}
