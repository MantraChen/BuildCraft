package buildcraft.lib.tile;

/** A machine updated by its owning block's native BlockEntityTicker. */
public interface TickingBlockEntity {
    void tick();
}
