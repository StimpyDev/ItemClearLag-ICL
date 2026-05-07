package vt.icl.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents; // Nieuwe import
import net.fabricmc.loader.api.FabricLoader;
import vt.icl.ICLCommon;
import vt.icl.commands.IclCommand;
import vt.icl.fabric.permission.FabricPermissions;

import static vt.icl.ICLCommon.config;

public class ICL implements ModInitializer {
    @Override
    public void onInitialize() {
        ICLCommon.init();
        
        CommandRegistrationCallback.EVENT.register(IclCommand::register);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ICLCommon.onTick(server);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (config.UsePermissionsApi) {
                if (FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
                    ICLCommon.permissionHandler = new FabricPermissions();
                } else {
                    ICLCommon.permissionHandler = null;
                }
            } else {
                ICLCommon.LOGGER.info("Using default permission system");
                ICLCommon.permissionHandler = null;
            }

            ICLCommon.onServerStart(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ICLCommon.onServerStop();
        });
    }
}
