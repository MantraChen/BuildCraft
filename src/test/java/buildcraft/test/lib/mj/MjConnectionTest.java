package buildcraft.test.lib.mj;

import buildcraft.api.mj.*;
import buildcraft.lib.cap.CapabilityHelper;
import buildcraft.lib.engine.EngineConnector;
import buildcraft.lib.mj.MjBatteryReceiver;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.junit.Test;

import static org.junit.Assert.*;

public class MjConnectionTest {
    @Test
    public void receiverUsesMachineBatteryAndReturnsExcess() {
        MjBattery battery = new MjBattery(100);
        MjBatteryReceiver receiver = new MjBatteryReceiver(battery);
        assertEquals(0, receiver.receivePower(150, true));
        assertEquals(0, battery.getStored());
        assertEquals(100, receiver.getPowerRequested());
        assertEquals(0, receiver.receivePower(150, false));
        assertEquals(150, battery.getStored());
        assertEquals(0, receiver.getPowerRequested());
        assertEquals(40, receiver.receivePower(40, true));
        assertEquals(40, receiver.receivePower(40, false));
        assertEquals(150, battery.getStored());
    }

    @Test
    public void extractionRequiresMinimumAndConservesEnergy() {
        MjBattery battery = new MjBattery(100);
        battery.addPower(80, false);
        assertEquals(0, battery.extractPower(90, 120));
        assertEquals(80, battery.getStored());
        assertEquals(50, battery.extractPower(10, 50));
        assertFalse(battery.extractPower(31));
        assertTrue(battery.extractPower(30));
        assertEquals(0, battery.extractAll());
    }

    @Test
    public void rejectsNegativeOrReversedAmountsWithoutMutation() {
        MjBattery battery = new MjBattery(100);
        battery.addPower(100, false);
        assertThrows(IllegalArgumentException.class, () -> battery.addPower(-1, false));
        assertThrows(IllegalArgumentException.class, () -> battery.addPowerChecking(-1, true));
        assertThrows(IllegalArgumentException.class, () -> battery.extractPower(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> battery.extractPower(20, 10));
        assertThrows(IllegalArgumentException.class, () -> new MjBattery(-1));
        assertEquals(100, battery.getStored());
    }

    @Test
    public void overfilledBatterySurvivesNbtAndPacketRoundTrips() {
        long stored = 16 * MjAPI.MJ + 123;
        MjBattery original = new MjBattery(10 * MjAPI.MJ);
        original.addPower(stored, false);
        MjBattery restored = new MjBattery(original.getCapacity());
        restored.readFromNbt(original.writeToNbt());
        assertEquals(stored, restored.getStored());
        ByteBuf buffer = Unpooled.buffer();
        try {
            restored.writeToBuffer(buffer);
            original.extractAll();
            original.readFromBuffer(buffer);
            assertEquals(stored, original.getStored());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void malformedStoredEnergyCannotBecomeNegative() {
        MjBattery battery = new MjBattery(100);
        NbtCompound nbt = new NbtCompound();
        nbt.putLong("stored", -1);
        battery.readFromNbt(nbt);
        assertEquals(0, battery.getStored());
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeLong(Long.MIN_VALUE);
            battery.readFromBuffer(buffer);
            assertEquals(0, battery.getStored());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void overflowReturnsUnacceptedEnergyInSimulationAndExecution() {
        MjBattery battery = new MjBattery(Long.MAX_VALUE);
        battery.addPower(Long.MAX_VALUE - 5, false);
        assertEquals(15, battery.addPower(20, true));
        assertEquals(Long.MAX_VALUE - 5, battery.getStored());
        assertEquals(15, battery.addPower(20, false));
        assertEquals(Long.MAX_VALUE, battery.getStored());
        assertEquals(1, battery.addPower(1, false));
        battery.tick(null, Vec3d.ZERO);
        assertEquals(Long.MAX_VALUE, battery.getStored());
    }

    @Test
    public void excessDecayKeepsHistoricalTwoCapacityThreshold() {
        MjBattery battery = new MjBattery(100);
        IMjEffectManager previous = MjAPI.EFFECT_MANAGER;
        try {
            MjAPI.EFFECT_MANAGER = null;
            battery.addPower(200, false);
            battery.tick(null, Vec3d.ZERO);
            assertEquals(200, battery.getStored());
            battery.addPower(100, false);
            battery.tick(null, Vec3d.ZERO);
            assertEquals(296, battery.getStored());
        } finally {
            MjAPI.EFFECT_MANAGER = previous;
        }
    }

    @Test
    public void redstoneEngineOnlyConnectsToPulseReceivers() {
        MjBatteryReceiver ordinary = new MjBatteryReceiver(new MjBattery(100));
        PulseReceiver pulse = new PulseReceiver();
        assertNull(MjAPI.getReceiver(new EngineConnector(true), ordinary));
        assertSame(pulse, MjAPI.getReceiver(new EngineConnector(true), pulse));
        assertSame(ordinary, MjAPI.getReceiver(new EngineConnector(false), ordinary));
    }

    @Test
    public void bothEndpointsMustAllowTheConnection() {
        IMjConnector source = new EngineConnector(false);
        MjBatteryReceiver rejecting = new MjBatteryReceiver(new MjBattery(100)) {
            @Override public boolean canConnect(IMjConnector other) { return false; }
        };
        MjBatteryReceiver disabled = new MjBatteryReceiver(new MjBattery(100)) {
            @Override public boolean canReceive() { return false; }
        };
        assertNull(MjAPI.getReceiver(source, rejecting));
        assertNull(MjAPI.getReceiver(source, disabled));
        assertNull(MjAPI.getReceiver(source, null));
        assertNull(MjAPI.getReceiver(source, new EngineConnector(false)));
    }

    @Test
    public void registeredMachineProviderExposesSameBattery() {
        CapabilityHelper capabilities = new CapabilityHelper();
        capabilities.addProvider(new Object());
        assertNull(capabilities.getMjConnector());
        MjBattery battery = new MjBattery(100);
        MjBatteryReceiver receiver = new MjBatteryReceiver(battery);
        capabilities.addProvider(new MjCapabilityHelper(receiver));
        IMjReceiver exposed = MjAPI.getReceiver(new EngineConnector(false), capabilities.getMjConnector());
        assertSame(receiver, exposed);
        assertEquals(0, exposed.receivePower(75, false));
        assertEquals(75, battery.getStored());
    }

    private static class PulseReceiver extends MjBatteryReceiver implements IMjRedstoneReceiver {
        PulseReceiver() { super(new MjBattery(100)); }
    }
}
