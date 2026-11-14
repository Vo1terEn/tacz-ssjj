package cn.ssjjcompat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Defers lazy attribute reads until an optional attribute owner has been assigned. */
public final class AttributeOwnerCompat {
    private static final String OWNER_CONTRACT =
            "dev.shadowsoffire.attributeslib.util.IEntityOwned";
    private static final Class<?> OWNER_TYPE;
    private static final Method GET_OWNER;

    static {
        Class<?> ownerType = null;
        Method getOwner = null;
        try {
            ownerType = Class.forName(OWNER_CONTRACT, false,
                    AttributeOwnerCompat.class.getClassLoader());
            getOwner = ownerType.getMethod("getOwner");
            if (getOwner.getReturnType().isPrimitive()) {
                throw new IllegalStateException(OWNER_CONTRACT + ".getOwner must return an entity");
            }
        } catch (ClassNotFoundException absent) {
            // Apothic Attributes is optional; the original attribute path remains available.
        } catch (NoSuchMethodException incompatibleContract) {
            throw new ExceptionInInitializerError(incompatibleContract);
        }
        OWNER_TYPE = ownerType;
        GET_OWNER = getOwner;
    }

    private AttributeOwnerCompat() {}

    public static boolean ready(Object attributeMap) {
        if (attributeMap == null) return false;
        if (OWNER_TYPE == null || !OWNER_TYPE.isInstance(attributeMap)) return true;
        try {
            return GET_OWNER.invoke(attributeMap) != null;
        } catch (IllegalAccessException inaccessibleContract) {
            throw new IllegalStateException("Cannot read " + OWNER_CONTRACT + ".getOwner",
                    inaccessibleContract);
        } catch (InvocationTargetException invocationFailure) {
            Throwable cause = invocationFailure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(OWNER_CONTRACT + ".getOwner failed", cause);
        }
    }
}
