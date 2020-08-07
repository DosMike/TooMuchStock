package de.dosmike.sponge.toomuchstock;

import com.google.inject.Inject;
import de.dosmike.sponge.toomuchstock.maths.PriceCalculator;
import de.dosmike.sponge.toomuchstock.maths.PriceManipulator;
import de.dosmike.sponge.toomuchstock.service.PriceCalculationService;
import de.dosmike.sponge.toomuchstock.service.impl.PriceCalculationProvider;
import de.dosmike.sponge.toomuchstock.utils.ItemDefinitions;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePostInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.service.ChangeServiceProviderEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Plugin(id = "toomuchstock", name = "Too Much Stock", version = "1.0")
public class TooMuchStock {

    private static TooMuchStock instance;
    private EconomyService economyService = null;
    private SpongeExecutorService syncScheduler = null;

    private ItemDefinitions itemDefinitions = new ItemDefinitions();
    private PriceCalculator priceCalculator = null;

    @Listener
    public void onChangeServiceProvider(ChangeServiceProviderEvent event) {
        if (event.getService().equals(EconomyService.class)) {
            economyService = (EconomyService) event.getNewProvider();
        }
    }

    static TooMuchStock getInstance() { return instance; }
    public static EconomyService getEconomy() {
        return instance.economyService;
    }
    public static SpongeExecutorService getSyncScheduler() {
        return instance.syncScheduler;
    }
    public static ItemDefinitions getItemDefinitionTable() {
        return instance.itemDefinitions;
    }
    public static PriceCalculator getPriceCalculator() { return instance.priceCalculator; }
    public static Path getCacheDirectory() { return instance.configPath.resolve("cache"); }

    PluginContainer getContainer() {
        return Sponge.getPluginManager().fromInstance(this).orElseThrow(()->new InternalError("No plugin container for self returned"));
    }

    @Inject
    private Logger logger;

    public static void l(String format, Object... args) {
        instance.logger.info(String.format(format, args));
    }

    public static void w(String format, Object... args) {
        instance.logger.warn(String.format(format, args));
    }

    @Inject
    @DefaultConfig(sharedRoot = false)
    private ConfigurationLoader<CommentedConfigurationNode> configManager;

    @Inject
    @ConfigDir(sharedRoot = false)
    private Path configPath;

    @Listener(order=Order.LATE) //order late to have economy (hopefully) load before, because we need the default currency
    public void onServerPreInit(GamePreInitializationEvent event) {
        instance = this;
        syncScheduler = Sponge.getScheduler().createSyncExecutor(this);
        Sponge.getEventManager().registerListeners(this, new EventListener());
        l("Loading configs...");
        loadConfigs(true);
        l("Register service...");
        Sponge.getServiceManager().setProvider(instance, PriceCalculationService.class, new PriceCalculationProvider());
    }

    @Listener
    public void onServerInit(GameInitializationEvent event) {
        // service should already be going in here
        l("Registering commands...");
        Commands.register(this);

        syncScheduler.scheduleAtFixedRate(()->priceCalculator.thinkTick(), 1, 1, TimeUnit.MINUTES);
    }

    @Listener
    public void onServerPostInit(GamePostInitializationEvent event) {

        CommandSource console = Sponge.getServer().getConsole();
        console.sendMessage(Text.of(TextColors.WHITE, " ___                             __              "));
        console.sendMessage(Text.of(TextColors.GRAY, "  |  _   _    |\\/|      _ |_    (_ _|_  _   _ |  "));
        console.sendMessage(Text.of(TextColors.GOLD, "  | (_) (_)   |  | |_| (_ | |   __) |_ (_) (_ |<  ",TextColors.YELLOW,"v"+getClass().getAnnotation(Plugin.class).version()));
        console.sendMessage(Text.NEW_LINE);

    }

    @Listener
    public void onPluginReload(GameReloadEvent event) {
        loadConfigs(false);
    }

    void loadConfigs(boolean hard) {

        HoconConfigurationLoader defaultLoader = HoconConfigurationLoader.builder().setURL(Sponge.getAssetManager().getAsset(instance, "default.conf").get().getUrl()).build();
        CommentedConfigurationNode defaultRoot = null;
        CommentedConfigurationNode config = null;

        try {
            defaultRoot = defaultLoader.load(ConfigurationOptions.defaults());
            //inject default currency into nodes. this makes the default config more reliable for
            //people that want to use this plugin out of the box
            for (String[] path : Arrays.asList(
                    new String[]{ConfigKeys.KEY_DEFAULT, ConfigKeys.KEY_GLOBAL,  ConfigKeys.KEY_INCOME},
                    new String[]{ConfigKeys.KEY_DEFAULT, ConfigKeys.KEY_GLOBAL,  ConfigKeys.KEY_SPENDING},
                    new String[]{ConfigKeys.KEY_DEFAULT, ConfigKeys.KEY_SHOPS,   ConfigKeys.KEY_INCOME},
                    new String[]{ConfigKeys.KEY_DEFAULT, ConfigKeys.KEY_SHOPS,   ConfigKeys.KEY_SPENDING},
                    new String[]{ConfigKeys.KEY_DEFAULT, ConfigKeys.KEY_PLAYERS, ConfigKeys.KEY_INCOME},
                    new String[]{ConfigKeys.KEY_DEFAULT, ConfigKeys.KEY_PLAYERS, ConfigKeys.KEY_SPENDING}
            )) {
                // cast is not redundant (you'll see if you remove it)
                ConfigurationNode node = defaultRoot.getNode((Object[])path);
                if (!node.getNode(ConfigKeys.KEY_DEFAULT_CURRENCY).isVirtual()) { // convert "defcur" into the actual default currency
                    Object value = node.getNode(ConfigKeys.KEY_DEFAULT_CURRENCY).getValue();
                    node.removeChild(ConfigKeys.KEY_DEFAULT_CURRENCY);
                    node.getNode(getEconomy().getDefaultCurrency().getId()).setValue(value);
                }
            }
        } catch (Exception e) { //should always load
            e.printStackTrace();
            return;
        }
        try {
            config = configManager.load(ConfigurationOptions.defaults());
            if (config.getNode(ConfigKeys.KEY_DEFAULT).isVirtual() ||
                config.getNode(ConfigKeys.KEY_ITEMS).isVirtual() ||
                config.getNode(ConfigKeys.KEY_RESET).isVirtual()) {
                config = config.mergeValuesFrom(defaultRoot);
                configManager.save(config);
            }

            ItemDefinitions definitions = new ItemDefinitions();
            for (Map.Entry<Object, ? extends CommentedConfigurationNode> entry : config.getNode(ConfigKeys.KEY_ITEMS).getChildrenMap().entrySet()) {
                definitions.fromConfiguration(entry.getKey().toString(), entry.getValue());
            }
            itemDefinitions = definitions;

            PriceManipulator globalManipulatorBase = PriceManipulator.fromConfiguration(config, ConfigKeys.KEY_GLOBAL);
            PriceManipulator shopManipulatorBase = PriceManipulator.fromConfiguration(config, ConfigKeys.KEY_SHOPS);
            PriceManipulator playerManipulatorBase = PriceManipulator.fromConfiguration(config, ConfigKeys.KEY_PLAYERS);
            if (hard || priceCalculator==null) {
                priceCalculator = PriceCalculator.builder()
                        .setGlobalManipulatorTemplate(globalManipulatorBase)
                        .setShopsManipulatorTemplate(shopManipulatorBase)
                        .setPlayerManipulatorTemplate(playerManipulatorBase)
                        .build();
            } else {
                priceCalculator.mergeManipulators(globalManipulatorBase, shopManipulatorBase, playerManipulatorBase);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (ObjectMappingException e) {
            Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.YELLOW,
                String.format("Could not load config: %s", e.getMessage())
            ));
            e.printStackTrace();
        }

    }

    void saveConfigs() {

        try {
            CommentedConfigurationNode config = configManager.createEmptyNode();

            itemDefinitions.toConfiguration(config.getNode(ConfigKeys.KEY_ITEMS).setComment("Can be created with in-game commands to e.g. register vote-keys"));

            priceCalculator.dumpBaseConfiguration(config);

            configManager.save(config);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (ObjectMappingException e) {
            Sponge.getServer().getBroadcastChannel().send(Text.of(TextColors.YELLOW,
                    String.format("Could not save config: %s", e.getMessage())
            ));
        }
    }

}
