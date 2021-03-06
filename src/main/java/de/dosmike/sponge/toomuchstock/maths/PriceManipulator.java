package de.dosmike.sponge.toomuchstock.maths;

import de.dosmike.sponge.toomuchstock.ConfigKeys;
import de.dosmike.sponge.toomuchstock.TooMuchStock;
import de.dosmike.sponge.toomuchstock.utils.ApplicabilityFilters;
import de.dosmike.sponge.toomuchstock.utils.ItemTypeEx;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.intellij.lang.annotations.MagicConstant;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

import java.util.*;
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
    Optional<ItemTracker> getIfCurrentlyTracked(ItemStackSnapshot item) {
        for (ItemTracker tracker : trackers) {
            if (tracker.getApplicabilityFilter().test(item))
                return Optional.of(tracker);
        }
        return Optional.empty();
    }
    /** @return true if the number of tracked items is 0 */
    public boolean isIdle() {
        return trackers.isEmpty();
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
     * that are offline. Shop and global manipulators probably should not call this!
     * @param from the last calculated time in ms
     */
    public void bigBrainTime(long from) {
        long now = System.currentTimeMillis();
        long minutes = (now-from)/60_000L;
        if (hasResetTime) {
            //calculate next reset in ms
            if (resetTimeInterval != null) {
                if (nextResetTime == 0L) //not yet calculated?
                    nextResetTime = System.currentTimeMillis();
                else {
                    long skipped = minutes / resetTimeInterval; // = passed intervals (floored)
                    nextResetTime = nextResetTime + (60_000L * resetTimeInterval * skipped); //speed ahead
                }
            } else if (resetTimePoint != null) {
                Calendar timeCalendar = new Calendar.Builder().setInstant(resetTimePoint).build();
                Calendar nowCalendar = Calendar.getInstance(timeCalendar.getTimeZone());
                timeCalendar.set(nowCalendar.get(Calendar.YEAR), nowCalendar.get(Calendar.MONTH), nowCalendar.get(Calendar.DAY_OF_MONTH));
                if (nowCalendar.after(timeCalendar)) //reset time already passed
                    timeCalendar.add(Calendar.DAY_OF_MONTH, 1);
                nextResetTime = timeCalendar.getTimeInMillis();
            } //else timed resets are disabled

            //calculate previous reset time
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

    /** delete all ItemTrackers that are currently "idle", meaning they have a discrepancy of 0 */
    public void cleanUp() {
        trackers.removeIf(ItemTracker::isIdle);
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

    /** pull values from another instance to minimize abuse on reload */
    public void merge(PriceManipulator other) {
        defaultTrackerConfiguration.merge(other.defaultTrackerConfiguration);
        trackers.retainAll(other.trackers);
        List<ItemTracker> newTrackers = new LinkedList<>(other.trackers);
        newTrackers.removeAll(trackers);
        for (ItemTracker tracker : trackers) {
            int i = other.trackers.indexOf(tracker); //should use custom .equals()
            if (i>=0) tracker.merge(other.trackers.get(i));
        }
        trackers.addAll(newTrackers);
    }

    public static PriceManipulator fromConfiguration(ConfigurationNode node, @MagicConstant(stringValues = {ConfigKeys.KEY_GLOBAL, ConfigKeys.KEY_SHOPS, ConfigKeys.KEY_PLAYERS}) String forType) throws ObjectMappingException {
        PriceManipulator manipulator = new PriceManipulator();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.getChildrenMap().entrySet()) {
            String key = entry.getKey().toString();
            ConfigurationNode valueNode = entry.getValue().getNode(forType);
            if (ConfigKeys.KEY_ITEMS.equalsIgnoreCase(key)) {
                continue; /* not here */
            } else if (ConfigKeys.KEY_RESET.equalsIgnoreCase(key)) {
                manipulator.hasResetTime = true;
                String value = valueNode.getString();
                try {
                    manipulator.resetTimeInterval = Integer.parseInt(value);
                    manipulator.resetTimePoint = null;
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
                        manipulator.resetTimeInterval = null;
                    }
                }

            // key can be item type, item type + meta or name for named map of "default"
            } else if (ConfigKeys.KEY_DEFAULT.equalsIgnoreCase(key)) {
                manipulator.defaultTrackerConfiguration = ItemTracker.fromConfiguration(ConfigKeys.KEY_DEFAULT, ApplicabilityFilters.pass, valueNode);
            } else {
                ApplicabilityFilters<?> filter;
                filter = TooMuchStock.getItemDefinitionTable().computeIfAbsent(key, (k)->ApplicabilityFilters.generateItemTypeMetaEquals(new ItemTypeEx(k)));
                ItemTracker tracker = ItemTracker.fromConfiguration(key, filter, valueNode);
                manipulator.trackers.add(tracker);
            }
        }
        if (manipulator.defaultTrackerConfiguration == null) {
            throw new ObjectMappingException("Missing '"+ ConfigKeys.KEY_DEFAULT+"' configuration value");
        }
        return manipulator;
    }

    public void toConfiguration(CommentedConfigurationNode parent, @MagicConstant(stringValues = {ConfigKeys.KEY_GLOBAL, ConfigKeys.KEY_SHOPS, ConfigKeys.KEY_PLAYERS}) String forType) throws ObjectMappingException {
        for (ItemTracker tracker : trackers) {
            if (!tracker.derived)
                tracker.toConfiguration(parent.getNode(tracker.getApplicabilityFilterName()).getNode(forType));
        }
        CommentedConfigurationNode node = parent.getNode(ConfigKeys.KEY_DEFAULT).getNode(forType);
        if (forType.equals(ConfigKeys.KEY_GLOBAL))
            node.setComment(".setComment(\"how much total volume can be traded on the server per specific items\")");
        else if (forType.equals(ConfigKeys.KEY_SHOPS))
            node.setComment("how much total volume can be traded within a shop per specific items\n"+
                "the values and related stuff for this part will run separate for every shop");
        else if (forType.equals(ConfigKeys.KEY_PLAYERS))
            node.setComment("how much total volume can be traded per player per specific items\n" +
                "the values and related stuff for this part will run separate for every player");
        defaultTrackerConfiguration.toConfiguration(node);
        if (hasResetTime) {
            if (resetTimeInterval != null) {
                parent.getNode(ConfigKeys.KEY_RESET).getNode(forType).setValue(resetTimeInterval);
            } else if (resetTimePoint != null) {
                Calendar cal = new GregorianCalendar();
                cal.setTime(resetTimePoint);
                parent.getNode(ConfigKeys.KEY_RESET).getNode(forType)
                        .setComment("Minute period (number) OR 24-Hour time (hh:mm)")
                        .setValue(String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
            }
        }
    }

}
