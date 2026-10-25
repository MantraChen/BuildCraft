/*
 * Copyright (c) 2017 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.core.statements;

import java.util.Locale;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;

import net.minecraft.item.ItemStack;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.Direction;


import buildcraft.api.items.IList;
import buildcraft.api.statements.IStatement;
import buildcraft.api.statements.IStatementContainer;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.statements.ITriggerExternal;
import buildcraft.api.statements.StatementParameterItemStack;

import buildcraft.lib.client.sprite.SpriteHolderRegistry.SpriteHolder;
import buildcraft.lib.misc.LocaleUtil;

import buildcraft.core.BCCoreSprites;
import buildcraft.core.BCCoreStatements;

public class TriggerInventory extends BCStatement implements ITriggerExternal {
    public State state;

    public TriggerInventory(State state) {
        super(
            "buildcraft:inventory." + state.name().toLowerCase(Locale.ROOT),
            "buildcraft.inventory." + state.name().toLowerCase(Locale.ROOT),
            "buildcraft.getInventory()." + state.name().toLowerCase(Locale.ROOT)
        );
        this.state = state;
    }

    @Override
    public SpriteHolder getSprite() {
        return BCCoreSprites.TRIGGER_INVENTORY.get(state);
    }

    @Override
    public int maxParameters() {
        return state == State.CONTAINS || state == State.SPACE ? 1 : 0;
    }

    @Override
    public String getDescription() {
        return LocaleUtil.localize("gate.trigger.inventory." + state.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isTriggerActive(BlockEntity tile, Direction side, IStatementContainer container, IStatementParameter[] parameters) {
        var storage = StorageTriggerUtil.items(tile, side);
        if (!StorageTriggerUtil.hasViews(storage)) return false;
        ItemStack sample = StorageTriggerUtil.sample(parameters);
        boolean found = false;
        for (var view : storage.nonEmptyViews()) {
            if (StorageTriggerUtil.matches(sample, view.getResource())) { found = true; break; }
        }
        if (state == State.EMPTY) return !found;
        if (state == State.CONTAINS) return found;
        boolean space;
        if (!sample.isEmpty() && sample.getItem() instanceof IList) {
            // Lists describe predicates, not insertable resources. Test known matching resources;
            // empty slots retain the legacy capacity heuristic for an unspecified list member.
            space = false;
            for (var view : storage) {
                if (view.isResourceBlank() && view.getCapacity() > 0 && storage.supportsInsertion()
                    || !view.isResourceBlank() && StorageTriggerUtil.matches(sample, view.getResource())
                    && StorageTriggerUtil.hasSpace(storage, view.getResource())) { space = true; break; }
            }
        } else {
            space = StorageTriggerUtil.hasSpace(storage, sample.isEmpty() ? null : ItemVariant.of(sample));
        }
        return switch (state) {
            case EMPTY -> !found;
            case CONTAINS -> found;
            case SPACE -> space;
            case FULL -> !space;
        };
    }

    @Override
    public IStatementParameter createParameter(int index) {
        return new StatementParameterItemStack();
    }

    @Override
    public IStatement[] getPossible() {
        return BCCoreStatements.TRIGGER_INVENTORY_ALL;
    }

    public enum State {
        EMPTY,
        CONTAINS,
        SPACE,
        FULL;

        public static final State[] VALUES = values();
    }
}
