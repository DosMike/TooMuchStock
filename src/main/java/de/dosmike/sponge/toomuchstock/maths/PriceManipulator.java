package de.dosmike.sponge.toomuchstock.maths;

import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.utils.ApplicabilityFilters;
import de.dosmike.sponge.toomuchstock.utils.ItemDefinitions;
import de.dosmike.sponge.toomuchstock.utils.ItemTypeEx;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.entity.Item;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class wraps the configuration and current prices
 * to track prices and present the correct ones according to
 * the config.
 */
public class PriceManipulator {

    /**
     * Stores the config value for reset time as interval in minutes.
     * Will be null if the reset time is a time of day.
     */
    private Integer resetTimeInterval;

    /**
     * Stores the config value for reset time as time of day.
     * Will be null if the reset time is a interval.
     */
    private Date resetTimePoint;

    /**
     * To quickly check if timely resets are event enabled
     */
    private boolean hasResetTime;

    /**
     * Stores the next, pre-calculated time to reset the trackers.
     */
    private Long nextResetTime = 0L;

    /**
     * Each item will change value individually within this
     * manipulators influence.
     * Note: This list needs to keep order, as some predicated have a higher
     * priority than others.
     * //TODO find a nifty way to quickly find the tracker from an ItemStack
     */
    private List<ItemTracker> trackers = new LinkedList<>();
    /**
     * Stores the default configuration
     */
    private ItemTracker defaultTrackerConfiguration;

    /**
     * Get or create the ItemTracker for the specified item and return it
     * @param item the item to search a tracker for
     * @return the corresponding ItemTracker instance
     */
    ItemTracker getTrackerFor(ItemStackSnapshot item) {
        for (ItemTracker tracker : trackers) {
            if (tracker.getApplicabilityFilter().test(item))
                return tracker;
        }
        ItemTracker t = defaultTrackerConfiguration.newTracker( ApplicabilityFilters.generateItemTypeEquals(item.getType()) );
        t.derived = true;
        trackers.add(t);
        return t;
    }

    /**
     * To be called once a minute. Will reset the Trackers when the
     * reset point is reached and decay price discrepancy */
    public void think() {
        if (hasResetTime && System.currentTimeMillis() >= nextResetTime) {
            if (nextResetTime != 0L) {
                for (ItemTracker t : trackers) t.reset();
            }
            //calculate next reset in ms
            if (resetTimeInterval != null) {
                nextResetTime = System.currentTimeMillis()+60_000L*resetTimeInterval;
            } else if (resetTimePoint != null) {
                Calendar timeCalendar = new Calendar.Builder().setInstant(resetTimePoint).build();
                Calendar nowCalendar = Calendar.getInstance(timeCalendar.getTimeZone());
                timeCalendar.set(nowCalendar.get(Calendar.YEAR), nowCalendar.get(Calendar.MONTH), nowCalendar.get(Calendar.DAY_OF_MONTH));
                if (nowCalendar.after(timeCalendar)) //reset time already passed
                    timeCalendar.add(Calendar.DAY_OF_MONTH, 1);
                nextResetTime = timeCalendar.getTimeInMillis();
            } //else timed resets are disabled
        } else {
            for (ItemTracker tracker : trackers) {
                tracker.decayTick();
            }
        }
    }

    /**
     * Performs the think transformations for the specified amount of time without repeatedly calling think.
     * This will bring all item trackers in this manipulator back to date. This allows unloading data for players
     * that are offline. Shop and global manipulators should not call this!
     * @param from the last calculated time in ms
     */
    public void bigBrainTime(long from) {
        long now = System.currentTimeMillis();
        long minutes = (now-from)/60_000L;
        if (hasResetTime) {
            long previousResetTime = now;
            if (resetTimeInterval != null) {
                previousResetTime = nextResetTime - 60_000L*resetTimeInterval;
            } else if (resetTimePoint != null) {
                previousResetTime = nextResetTime - 24*60*60*1000; // time point is time of day
            }
            if (from < previousResetTime) {
                for (ItemTracker t : trackers) t.reset(); //the trackers were reset since this player went
                return;
            }
        }
        for (ItemTracker t : trackers) t.decayTicks(minutes);
    }

    public static PriceManipulator fromConfiguration(ConfigurationNode node) throws ObjectMappingException {
        PriceManipulator manipulator = new PriceManipulator();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.getChildrenMap().entrySet()) {
            String key = entry.getKey().toString();
            if ("reset".equalsIgnoreCase(key)) {
                manipulator.hasResetTime = true;
                String value = entry.getValue().getString();
                try {
                    int interval = Integer.parseInt(value);
                    manipulator.resetTimeInterval = interval;
                } catch (NumberFormatException e) {
                    Pattern time = Pattern.compile("((?:[01]?[0-9])|(?:2[0-4])):([0-5]?[0-9])");
                    Matcher hhmm = time.matcher(value);
                    if (hhmm.matches()) {
                        Calendar calendar = GregorianCalendar.getInstance();
                        int hour = Integer.parseInt(hhmm.group(1));
                        int min = Integer.parseInt(hhmm.group(2));
                        calendar.set(Calendar.HOUR_OF_DAY, hour == 24 ? 0 : hour);
                        calendar.set(Calendar.MINUTE, min);
                        manipulator.resetTimePoint = calendar.getTime();
                    }
                }

            // key can be item type, item type + meta or name for named map of "default"
            } else if ("default".equalsIgnoreCase(key)) {
                manipulator.defaultTrackerConfiguration = ItemTracker.fromConfiguration(ApplicabilityFilters.pass, entry.getValue());
            } else {
                Predicate<ItemStackSnapshot> filter;
                filter = TooMuchStock.getItemDefinitionTable().getOrDefault(key, ApplicabilityFilters.generateItemTypeMetaEquals(new ItemTypeEx(key)));
                ItemTracker tracker = ItemTracker.fromConfiguration(filter,entry.getValue());
                manipulator.trackers.add(tracker);
            }
        }
        return manipulator;
    }

    protected PriceManipulator clone()  {
        PriceManipulator clone = new PriceManipulator();
        clone.resetTimePoint = this.resetTimePoint;
        clone.resetTimeInterval = this.resetTimeInterval;
        clone.hasResetTime = this.hasResetTime;
        clone.nextResetTime = this.nextResetTime;
        clone.defaultTrackerConfiguration = this.defaultTrackerConfiguration.clone();
        clone.trackers = new LinkedList<>();
        for (ItemTracker tracker : this.trackers) {
            clone.trackers.add(tracker.clone());
        }
        return clone;
    }
}
