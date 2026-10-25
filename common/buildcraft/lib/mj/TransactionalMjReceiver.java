/* Copyright (c) 2026 FelixMantra. SPDX-License-Identifier: MPL-2.0 */
package buildcraft.lib.mj;

import buildcraft.api.mj.IMjReceiver;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;

/** An MJ receiver whose work, as well as its energy, participates in a Fabric transaction. */
public interface TransactionalMjReceiver extends IMjReceiver {
    long receivePower(long microJoules, TransactionContext transaction);
}
