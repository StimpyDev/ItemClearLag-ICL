package vt.icl;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vt.icl.config.ConfigManager;
import vt.icl.config.Configuration;
import vt.icl.config.lang.IclTranslationManager;
import vt.icl.mixin.ItemEntityAccessor;
import vt.icl.permission.PermissionHandler;
import vt.icl.permission.Permissions;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

import static vt.icl.config.lang.IclTranslationManager.createDefaultTranslationFiles;

public class ICLCommon {
    public static final String MOD_ID = "icl";
    public static final String MOD_PREFIX = ""; 
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID.toUpperCase());
    public static final Path CONFIG_DIR = new File("./config/" + MOD_ID.substring(0, 1).toUpperCase() + MOD_ID.substring(1)).toPath();
    
    public static Configuration config = ConfigManager.getConfig();
    public static Map<String, String> translations;
    private static Map<String, String> defaultTranslations;
    public static PermissionHandler permissionHandler;
    private static MinecraftServer server;
    
    private static long ticksUntilNextClean = -1;

    public static void init() {
        LOGGER.info("Initializing ICL");
        createDefaultTranslationFiles();
        reloadTranslations();
    }

    public static void reloadTranslations() {
        translations = IclTranslationManager.loadTranslation(config.NotificationLang);
        defaultTranslations = IclTranslationManager.loadTranslation("en_us");
    }

    public static void onServerStart(MinecraftServer server) {
        ICLCommon.server = server;
        resetSchedule();
    }

    public static void onServerStop() {
        ticksUntilNextClean = -1;
    }
    public static void CancelIcl(int tempDelay) {
        if (tempDelay > 0) {
            ticksUntilNextClean = (long) tempDelay * 20;
        } else {
            resetSchedule();
        }
    }

    public static void resetSchedule() {
        if (config.Delay > 0) {
            ticksUntilNextClean = (long) config.Delay * 20;
        } else {
            ticksUntilNextClean = -1;
        }
    }

    public static void onTick(MinecraftServer server) {
        if (ticksUntilNextClean <= 0) return;

        ticksUntilNextClean--;
        long secondsLeft = ticksUntilNextClean / 20;

        if (ticksUntilNextClean % 20 == 0) {
            handleNotifications(server, secondsLeft);
            if (secondsLeft <= 0) {
                clearItems(server);
                resetSchedule();
            }
        }
    }

    private static void handleNotifications(MinecraftServer server, long secondsLeft) {
        if (config.doShowNotification) {
            for (int i = 0; i < config.NotificationTimes; i++) {
                long notifyAt = config.NotificationStart - (long) i * config.NotificationDelay;
                if (secondsLeft == notifyAt && notifyAt > 0) {
                    broadcastIclMessage(server, "text.icl.notification", true, secondsLeft);
                }
            }
        }
        if (config.doNotificationCountdown && secondsLeft <= config.CountdownStart && secondsLeft > 0) {
            broadcastIclMessage(server, "text.icl.countdown", false, secondsLeft);
        }
    }

    private static void broadcastIclMessage(MinecraftServer server, String translationKey, boolean playSound, Object... args) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MutableComponent message = Component.literal(IclTranslate(translationKey, args))
                    .withStyle(ChatFormatting.valueOf(config.NotificationColor));
            IclMessage(player, message);
            if (playSound && config.doNotificationSound) IclPlaysound(player, false);
        }
    }

    private static void IclMessage(ServerPlayer player, MutableComponent message) {
        if (permissionCheckforCancel(player.createCommandSourceStack())) {
            message.append(Component.literal(" " + IclTranslate("text.icl.cancel.button"))
                    .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/icl cancel")))
                    .withStyle(ChatFormatting.RED));
        }
        player.sendSystemMessage(message);
    }

    public static void clearItems(MinecraftServer server) {
        int count = 0;
        for (var world : server.getAllLevels()) {
            for (var entity : world.getEntities(EntityTypeTest.forClass(ItemEntity.class), Entity::isAlive)) {
                if (config.preserveNoPickupItems && ((ItemEntityAccessor) entity).getPickupDelay() == Short.MAX_VALUE) continue;
                if (config.preserveNoDespawnItems && entity.getAge() == Short.MIN_VALUE) continue;
                count += entity.getItem().getCount();
                entity.discard();
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (config.doShowNotification) {
                player.sendSystemMessage(Component.literal(IclTranslate("text.icl.clear.finish", count)).withStyle(ChatFormatting.valueOf(config.NotificationColor)));
                if (config.doLastNotificationSound) IclPlaysound(player, true);
            }
        }
    }

    public static void reloadIcl() {
        config = ConfigManager.getConfig();
        reloadTranslations();
        resetSchedule();
    }

    public static String IclTranslate(String key, Object... args) {
        String translation = (translations != null) ? translations.get(key) : null;
        if (translation == null && defaultTranslations != null) translation = defaultTranslations.get(key);
        return (translation != null) ? (args.length > 0 ? String.format(translation, args) : translation) : key;
    }

    public static void IclPlaysound(ServerPlayer player, boolean isLastSound) {
        ResourceLocation sound = ResourceLocation.parse(isLastSound ? config.LastNotificationSound : config.NotificationSound);
        Holder<SoundEvent> registryEntry = Holder.direct(SoundEvent.createVariableRangeEvent(sound));
        player.connection.send(new ClientboundSoundPacket(registryEntry, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 1.0f, 0L), null);
    }

    private static boolean permissionCheckforCancel(CommandSourceStack source) {
        if (permissionHandler != null) return permissionHandler.hasPermission(source, MOD_ID + ".cancel");
        return !config.RequireOpCancel || source.hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
