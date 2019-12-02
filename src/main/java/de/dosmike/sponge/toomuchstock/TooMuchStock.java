package de.dosmike.sponge.toomuchstock;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import de.dosmike.sponge.VersionChecker;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.service.ChangeServiceProviderEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.service.economy.EconomyService;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;

@Plugin(id = "too_much_stock", name = "Too Much Stock", version = "0.1")
public class TooMuchStock {

    private static TooMuchStock instance;
    private EconomyService economyService = null;
    private SpongeExecutorService syncScheduler = null;

    @Listener
    public void onChangeServiceProvider(ChangeServiceProviderEvent event) {
        if (event.getService().equals(EconomyService.class)) {
            economyService = (EconomyService) event.getNewProvider();
        }
    }

    public static EconomyService getEconomy() {
        return instance.economyService;
    }
    public static SpongeExecutorService getSyncScheduler() {
        return instance.syncScheduler;
    }

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

    @Listener
    public void onServerInit(GameInitializationEvent event) {
        instance = this;
        syncScheduler = Sponge.getScheduler().createSyncExecutor(this);
        Sponge.getEventManager().registerListeners(this, new EventListener());
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        l("Registering commands...");
//        CommandRegistra.register();

        loadConfigs();

        //these two calls depend on loadConfig()
//        VersionChecker.checkPluginVersion(getContainer());

        CommandSource console = Sponge.getServer().getConsole();
        console.sendMessage(Text.of(TextColors.WHITE, " ___                             __              "));
        console.sendMessage(Text.of(TextColors.GRAY, "  |  _   _    |\\/|      _ |_    (_ _|_  _   _ |  "));
        console.sendMessage(Text.of(TextColors.GOLD, "  | (_) (_)   |  | |_| (_ | |   __) |_ (_) (_ |<  ",TextColors.YELLOW,"v"+getClass().getAnnotation(Plugin.class).version()));
        console.sendMessage(Text.NEW_LINE);

    }

    @Listener
    public void onPluginReload(GameReloadEvent event) {
        loadConfigs();
    }

    private void loadConfigs() {

        HoconConfigurationLoader defaultLoader = HoconConfigurationLoader.builder().setURL(Sponge.getAssetManager().getAsset(getContainer(), "").get().getUrl()).build();
        CommentedConfigurationNode defaultRoot = null;
        CommentedConfigurationNode config = null;
        try {
            defaultRoot = defaultLoader.load(ConfigurationOptions.defaults());
        } catch (Exception e) { //should always load
            e.printStackTrace();
            return;
        }
        try {
            config = configManager.load(ConfigurationOptions.defaults()).mergeValuesFrom(defaultRoot);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } finally {
            try {
                assert config != null; //make ide happy
                configManager.save(config);
            } catch (IOException ignore) {}
        }


    }

}
