package buildcraft.lib.script;

import buildcraft.api.registry.EventBuildCraftReload;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Fabric callback for the existing BuildCraft registry reload lifecycle. */
@FunctionalInterface
public interface RegistryReloadEvents {
    Event<RegistryReloadEvents> EVENT = EventFactory.createArrayBacked(
        RegistryReloadEvents.class,
        listeners -> event -> {
            for (RegistryReloadEvents listener : listeners) {
                listener.onReload(event);
            }
        }
    );

    void onReload(EventBuildCraftReload event);
}
