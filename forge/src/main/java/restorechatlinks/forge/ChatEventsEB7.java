package restorechatlinks.forge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

class ChatEventsEB7 {

    static final Logger LOGGER = LogManager.getLogger(ChatEventsEB7.class);

    public static final boolean HAS_NEW_EVENT_BUS = hasEB7Class();
    public static Class<?> BUS_GROUP_CLASS;

    private ChatEventsEB7() {
    }

    public static <T> void registerGroupBusEvent(Class<T> cls, Object busGroup, Consumer<T> in)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // Forge event bus
        Object eventBus = cls.getMethod("getBus", BUS_GROUP_CLASS).invoke(null, busGroup);
        eventBus.getClass().getMethod("addListener", Consumer.class).invoke(eventBus, in);
    }

    public static <T> void registerModBusEvent(Class<T> cls, Consumer<T> in)
            throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // Game event bus
        Object eventBus = cls.getField("BUS").get(null);
        eventBus.getClass().getMethod("addListener", Consumer.class).invoke(eventBus, in);
    }

    public static boolean hasEB7Class() {
        try {
            BUS_GROUP_CLASS = Class.forName("net.minecraftforge.eventbus.api.bus.BusGroup");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
