import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * Java agent that sets sun.nio.ch.PipeImpl.noUnixDomainSockets = true
 * before any Selector.open() is called.
 * 
 * This forces PipeImpl to use TCP loopback instead of AF_UNIX,
 * working around a Windows bug where AF_UNIX connect() fails with EINVAL.
 */
public class PipeFixAgent {
    public static void premain(String args) {
        try {
            // Get Unsafe instance (sun.misc.Unsafe is still accessible in JDK 17)
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            // Get the noUnixDomainSockets static field from PipeImpl
            Class<?> pipeImplClass = Class.forName("sun.nio.ch.PipeImpl");
            Field noUnixField = pipeImplClass.getDeclaredField("noUnixDomainSockets");

            // Use Unsafe to set the static boolean to true (bypasses module restrictions)
            long offset = unsafe.staticFieldOffset(noUnixField);
            Object base = unsafe.staticFieldBase(noUnixField);
            unsafe.putBoolean(base, offset, true);

            System.out.println("[PipeFix] PipeImpl.noUnixDomainSockets = true — TCP loopback will be used");
        } catch (Exception e) {
            System.err.println("[PipeFix] Could not patch PipeImpl: " + e);
        }
    }
}
