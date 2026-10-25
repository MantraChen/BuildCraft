package buildcraft.builders;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.util.DyeColor;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import buildcraft.api.schematics.ISchematicBlock;
import buildcraft.api.schematics.SchematicBlockContext;
import buildcraft.api.schematics.SchematicBlockFactoryRegistry;
import buildcraft.api.schematics.SchematicEntityFactoryRegistry;

import buildcraft.builders.snapshot.SchematicBlockAir;
import buildcraft.builders.snapshot.SchematicBlockDefault;
import buildcraft.builders.snapshot.SchematicBlockFluid;
import buildcraft.builders.snapshot.SchematicEntityDefault;
import buildcraft.builders.snapshot.SchematicEntityGroup;

public class BCBuildersSchematics {
    public static void preInit() {
        registerSchematicFactory("air", 0, SchematicBlockAir::predicate, SchematicBlockAir::new);
        registerSchematicFactory("default", 100, SchematicBlockDefault::predicate, SchematicBlockDefault::new);
        registerSchematicFactory("fluid", 200, SchematicBlockFluid::predicate, SchematicBlockFluid::new);

        registerSchematicFactory("banner", 300, c -> c.block instanceof AbstractBannerBlock, BCBuildersSchematics::getBanner);
        registerSchematicFactory("vine", 300, c -> c.block instanceof VineBlock, BCBuildersSchematics::getVine);

        SchematicEntityFactoryRegistry.registerFactory("buildcraftbuilders:default", 100, SchematicEntityDefault::predicate,
            SchematicEntityDefault::new);
        SchematicEntityFactoryRegistry.registerFactory("buildcraftbuilders:riding_group", 200, SchematicEntityGroup::predicate,
            SchematicEntityGroup::new);
    }

    private static <S extends ISchematicBlock> void registerSchematicFactory(String name, int priority,
        Predicate<SchematicBlockContext> predicate, Supplier<S> supplier) {
        SchematicBlockFactoryRegistry.registerFactory("buildcraftbuilders:" + name, priority, predicate, supplier);
    }

    private static SchematicBlockDefault getBanner() {
        return new SchematicBlockDefault() {
            @Nonnull
            @Override
            public List<ItemStack> computeRequiredItems() {
                var banner = new net.minecraft.block.entity.BannerBlockEntity(BlockPos.ORIGIN, blockState);
                if (tileNbt != null) banner.readNbt(tileNbt.copy());
                return List.of(banner.getPickStack());
            }
        };
    }

    private static SchematicBlockDefault getVine() {
        return new SchematicBlockDefault() {
            @Override
            public boolean isReadyToBuild(World world, BlockPos blockPos) {
                return super.isReadyToBuild(world, blockPos);
            }
        };
    }
}
