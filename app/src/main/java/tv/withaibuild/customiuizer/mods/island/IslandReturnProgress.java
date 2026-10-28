package tv.withaibuild.customiuizer.mods.island;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SharedMemory;
import android.system.OsConstants;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** One A14 shared scalar per accepted return. Binder transfers its read-only mapping
 * once on an IO worker; frames only load/store an aligned atomic integer. */
final class IslandReturnProgress implements AutoCloseable {
    private static final String DESCRIPTOR = "customiuizer.island.return.progress";
    private static final int UNITS = 1_000_000;
    private static final VarHandle VALUE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());
    private ByteBuffer buffer;

    IslandReturnProgress(ByteBuffer buffer) { this.buffer = buffer; }

    synchronized float read() {
        ByteBuffer current = buffer;
        return current == null ? -1f : (int) VALUE.getVolatile(current, 0) / (float) UNITS;
    }

    @Override public synchronized void close() {
        ByteBuffer old = buffer;
        buffer = null;
        if (old != null) SharedMemory.unmap(old);
    }

    static IslandReturnProgress receive(IBinder source) throws Exception {
        if (source == null) return null;
        Parcel request = Parcel.obtain();
        Parcel response = Parcel.obtain();
        try {
            request.writeInterfaceToken(DESCRIPTOR);
            if (!source.transact(IBinder.FIRST_CALL_TRANSACTION, request, response, 0)) return null;
            response.readException();
            SharedMemory memory = response.readTypedObject(SharedMemory.CREATOR);
            if (memory == null) return null;
            try {
                if (memory.getSize() != Integer.BYTES) return null;
                return new IslandReturnProgress(memory.mapReadOnly());
            } finally { memory.close(); }
        } finally { response.recycle(); request.recycle(); }
    }

    static final class Producer extends Binder implements AutoCloseable {
        private SharedMemory memory;
        private ByteBuffer buffer;

        Producer() throws Exception {
            SharedMemory created = SharedMemory.create("island-return", Integer.BYTES);
            ByteBuffer mapped = null;
            try {
                mapped = created.mapReadWrite();
                VALUE.setVolatile(mapped, 0, 0);
                if (!created.setProtect(OsConstants.PROT_READ)) throw new IllegalStateException("Read-only progress protection failed");
                memory = created; buffer = mapped;
            } catch (Exception error) {
                if (mapped != null) SharedMemory.unmap(mapped);
                created.close();
                throw error;
            }
        }

        synchronized void write(float progress) {
            ByteBuffer current = buffer;
            if (current != null) VALUE.setVolatile(current, 0,
                    Float.isFinite(progress) ? Math.round(Math.max(0f, Math.min(1f, progress)) * UNITS) : -UNITS);
        }

        @Override protected synchronized boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws android.os.RemoteException {
            if (code != IBinder.FIRST_CALL_TRANSACTION || reply == null) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(DESCRIPTOR);
            int caller = Binder.getCallingUid();
            if (caller != android.os.Process.SYSTEM_UID && caller != android.os.Process.myUid()) throw new SecurityException("SystemUI only");
            reply.writeNoException();
            reply.writeTypedObject(memory, 0);
            return true;
        }

        @Override public synchronized void close() {
            ByteBuffer old = buffer;
            buffer = null;
            if (old != null) {
                if ((int) VALUE.getVolatile(old, 0) < UNITS) VALUE.setVolatile(old, 0, -UNITS);
                SharedMemory.unmap(old);
            }
            if (memory != null) { memory.close(); memory = null; }
        }
    }
}
