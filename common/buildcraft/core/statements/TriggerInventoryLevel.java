/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.statements;

import java.util.Locale;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;

import net.minecraft.item.ItemStack;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Direction;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import buildcraft.api.items.IList;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementParameterItemStack;

import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.StringUtilBC;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerInventoryLevel extends BCStatement implements ITriggerExternal {
    public TriggerType type;

    public TriggerInventoryLevel(TriggerType type) {
        super("buildcraft:inventorylevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.inventorylevel." + type.name().toLowerCase(Locale.ROOT),
            "buildcraft.filteredBuffer." + type.name().toLowerCase(Locale.ROOT));
        this.type = type;
    }

    @Override
    public int maxParameters() {
        return 1;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_INVENTORY_LEVEL.get(type);
    }

    @Override
    public String getDescription() {
        return StringUtilBC.formatSafe(LocaleUtil.localize("gate.trigger.inventorylevel.below"), (int) (type.level * 100));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, IStatementContainer container,
        IStatementParameter[] parameters) {
        var storage = StorageTriggerUtil.items(tile, side);
        if (!StorageTriggerUtil.hasViews(storage)) return false;
        ItemStack sample = StorageTriggerUtil.sample(parameters);
        double amount = 0, capacity = 0;
        for (var view : storage) {
            if (sample.isEmpty() || StorageTriggerUtil.matches(sample, view.getResource())) {
                amount += view.getAmount();
                capacity += view.getCapacity();
            } else if (view.isResourceBlank() && !(sample.getItem() instanceof IList)
                    && view instanceof Storage<?> slot) {
                @SuppressWarnings("unchecked") Storage<ItemVariant> itemSlot = (Storage<ItemVariant>) slot;
                capacity += StorageUtil.simulateInsert(itemSlot, ItemVariant.of(sample), sample.getMaxCount(), null);
            }
        }
        // Non-slotted mod storages can expose views without insertion operations on each view.
        // A real sample lets us measure their available space without assuming a slot layout.
        if (!sample.isEmpty() && !(sample.getItem() instanceof IList) && !(storage instanceof SlottedStorage<?>)) {
            capacity = Math.max(capacity, amount + StorageUtil.simulateInsert(storage, ItemVariant.of(sample), Long.MAX_VALUE, null));
        }
        return capacity > 0 && amount / capacity < type.level;
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_INVENTORY_ALL;
    }

    public enum TriggerType {
        BELOW25(0.25F),
        BELOW50(0.5F),
        BELOW75(0.75F);

        TriggerType(float level) {
            this.level = level;
        }

        public static final TriggerType[] VALUES = values();

        public final float level;
    }
}
