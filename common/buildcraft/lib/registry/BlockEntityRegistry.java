package buildcraft.lib.registry;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import buildcraft.lib.tile.TickingBlockEntity;

/** Registers explicit factories without placeholder types, positions or reflective constructors. */
public final class BlockEntityRegistry {
    private static final Map<Class<? extends BlockEntity>, BlockEntityType<?>> TYPES = new HashMap<>();
    private static final Set<BlockEntityType<?>> TICKING = new HashSet<>();

    private BlockEntityRegistry() {}

    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockEntityType<T> type, BlockPos pos, BlockState state);
    }

    public static <T extends BlockEntity> BlockEntityType<T> register(Identifier id, Class<T> tileClass,
            Factory<T> factory, Block... blocks) {
        Objects.requireNonNull(factory, "factory");
        Block[] enabled = Arrays.stream(blocks).filter(Objects::nonNull).distinct().toArray(Block[]::new);
        // Disabled blocks are never constructed, so no entity type is needed for them.
        if (enabled.length == 0) return null;
        if (TYPES.containsKey(tileClass) || Registries.BLOCK_ENTITY_TYPE.containsId(id)) {
            throw new IllegalArgumentException("Duplicate block entity registration: " + id);
        }
        AtomicReference<BlockEntityType<T>> reference = new AtomicReference<>();
        BlockEntityType<T> type = FabricBlockEntityTypeBuilder.create(
            (BlockPos pos, BlockState state) -> factory.create(Objects.requireNonNull(reference.get()), pos, state),
            enabled).build();
        reference.set(type);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, type);
        TYPES.put(tileClass, type);
        if (TickingBlockEntity.class.isAssignableFrom(tileClass)) TICKING.add(type);
        return type;
    }

    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity> BlockEntityType<T> type(Class<T> tileClass) {
        BlockEntityType<?> type = TYPES.get(tileClass);
        if (type == null) throw new IllegalStateException("Unregistered block entity: " + tileClass.getName());
        return (BlockEntityType<T>) type;
    }

    public static <T extends BlockEntity> T create(Class<T> tileClass, BlockPos pos, BlockState state) {
        BlockEntityType<T> type = type(tileClass);
        if (!type.supports(state)) throw new IllegalArgumentException("Invalid block for " + tileClass.getSimpleName());
        return Objects.requireNonNull(type.instantiate(pos, state));
    }

    public static <T extends BlockEntity> BlockEntityTicker<T> ticker(BlockEntityType<T> type) {
        return TICKING.contains(type) ? (world, pos, state, entity) -> ((TickingBlockEntity) entity).tick() : null;
    }
}
