package com.soulsvault.client.party;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.party.PartySnapshot;
import com.soulsvault.platform.Services;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Puts party members on Xaero's Minimap and World Map, the way Open Parties and Claims does: both mods let other
 * mods register a client-side "player tracker system" (an iterator of players plus a reader for their id, position
 * and dimension), and draw what it returns on the minimap, the world map and, for the minimap, as in-world icons.
 * Positions come from our own party sync, so Xaero's mods are not needed on the server.
 * <p>
 * Reflection and {@link Proxy} only: the tracker interfaces are looked up from each mod's {@code register} method, so
 * this keeps working across Xaero versions (the minimap renamed its interfaces in 24.x) and does nothing when Xaero's
 * mods are absent.
 */
public final class XaeroPartyTracker {
    private static final String SYSTEM_NAME = "soulsvault_party";
    private static final long OPTION_CHECK_MILLIS = 1000L;

    private static boolean onMinimap;
    private static boolean onWorldMap;

    private static boolean optionUnreadable;
    private static Object configManager;
    private static Method getEffective;
    private static Object inWorldOption;
    private static long optionCheckedAt;
    private static boolean minimapDrawsInWorld;

    private XaeroPartyTracker() {
    }

    /** Registers with whichever of Xaero's Minimap and World Map is installed. Call once on the client thread. */
    public static void register() {
        if (Services.PLATFORM.isModLoaded("xaerominimap") || Services.PLATFORM.isModLoaded("xaerominimapfair")) {
            onMinimap = registerWith("Xaero's Minimap", () -> {
                Object hudMod = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
                // Accessor names across versions: newest first, then 24.x-26.4, then the pre-24.x one
                for (String accessor : new String[]{"getRenderedPlayerTrackerManager", "getPlayerTracker", "getPlayerTrackerSystemManager"}) {
                    try {
                        return hudMod.getClass().getMethod(accessor).invoke(hudMod);
                    } catch (NoSuchMethodException ignored) {
                        // try the next name
                    }
                }
                throw new NoSuchMethodException("player tracker manager accessor");
            });
        }
        if (Services.PLATFORM.isModLoaded("xaeroworldmap")) {
            onWorldMap = registerWith("Xaero's World Map",
                    () -> Class.forName("xaero.map.WorldMap").getField("playerTrackerSystemManager").get(null));
        }
    }

    /**
     * Whether Xaero's Minimap is drawing party members as in-world icons right now (we feed it, and its "Tracked
     * Players in World" option is on), in which case the Casket's own markers stay out of the way.
     */
    public static boolean minimapDrawsTeammatesInWorld() {
        if (!onMinimap || !SoulsVaultClientConfig.get().partyOnXaeroMaps()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - optionCheckedAt >= OPTION_CHECK_MILLIS) {
            optionCheckedAt = now;
            minimapDrawsInWorld = readInWorldOption();
        }
        return minimapDrawsInWorld;
    }

    private interface ManagerLookup {
        Object find() throws ReflectiveOperationException;
    }

    private static boolean registerWith(String modName, ManagerLookup lookup) {
        try {
            Object manager = lookup.find();
            Method register = findRegister(manager.getClass());
            if (register == null) {
                Constants.LOG.warn("{} has no player tracker API this version of Casket of Echoes understands", modName);
                return false;
            }
            Class<?> trackerType = register.getParameterTypes()[1];
            Class<?> readerType = trackerType.getMethod("getReader").getReturnType();
            ClassLoader loader = trackerType.getClassLoader();
            Object reader = Proxy.newProxyInstance(loader, new Class<?>[]{readerType}, XaeroPartyTracker::readTeammate);
            Object tracker = Proxy.newProxyInstance(loader, new Class<?>[]{trackerType}, (proxy, method, args) ->
                    switch (method.getName()) {
                        case "getReader" -> reader;
                        case "getTrackedPlayerIterator" -> trackedTeammates().iterator();
                        default -> fallback(proxy, method, args);
                    });
            register.invoke(manager, SYSTEM_NAME, tracker);
            Constants.LOG.info("Party members are shown on {}", modName);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Constants.LOG.warn("Could not show party members on {}", modName, e);
            return false;
        }
    }

    /**
     * {@code register(String, tracker)}, preferring the non-deprecated overload (the minimap keeps its pre-24.x
     * interface as a deprecated alias).
     */
    private static Method findRegister(Class<?> managerClass) {
        Method fallback = null;
        for (Method method : managerClass.getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (!method.getName().equals("register") || params.length != 2 || params[0] != String.class || !params[1].isInterface()) {
                continue;
            }
            if (!method.isAnnotationPresent(Deprecated.class) && !params[1].isAnnotationPresent(Deprecated.class)) {
                return method;
            }
            fallback = method;
        }
        return fallback;
    }

    /** Party members the server reports (online, not spectating), never the player themself. */
    private static List<ClientPartyCache.TrackedMember> trackedTeammates() {
        Minecraft mc = Minecraft.getInstance();
        PartySnapshot party = ClientPartyCache.getSnapshot();
        if (party == null || mc.player == null || !SoulsVaultClientConfig.get().partyOnXaeroMaps()) {
            return List.of();
        }
        UUID self = mc.player.getUUID();
        List<ClientPartyCache.TrackedMember> teammates = new ArrayList<>();
        for (ClientPartyCache.TrackedMember tracked : ClientPartyCache.getTrackedMembers()) {
            if (!tracked.id().equals(self) && isMember(party, tracked.id())) {
                teammates.add(tracked);
            }
        }
        return teammates;
    }

    private static boolean isMember(PartySnapshot party, UUID id) {
        for (PartySnapshot.Member member : party.members()) {
            if (member.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The reader: Xaero passes back the objects our iterator returned. */
    private static Object readTeammate(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null || args.length != 1 || !(args[0] instanceof ClientPartyCache.TrackedMember tracked)) {
            return fallback(proxy, method, args);
        }
        return switch (method.getName()) {
            case "getId" -> tracked.id();
            case "getDimension" -> tracked.dimension();
            case "getX" -> tracked.position().x;
            case "getY" -> tracked.position().y;
            case "getZ" -> tracked.position().z;
            default -> fallback(proxy, method, args);
        };
    }

    /** Object methods, default methods, and a zero/null answer for anything a newer Xaero version adds. */
    private static Object fallback(Object proxy, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "equals":
                return args != null && args.length == 1 && proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "Casket of Echoes party tracker";
            default:
                break;
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        Class<?> type = method.getReturnType();
        return type.isPrimitive() && type != void.class ? Array.get(Array.newInstance(type, 1), 0) : null;
    }

    /** Xaero's "Tracked Players in World" (minimap client option, default on). */
    private static boolean readInWorldOption() {
        if (optionUnreadable) {
            return true;
        }
        try {
            if (getEffective == null) {
                Object hudMod = Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
                Object configs = hudMod.getClass().getMethod("getHudConfigs").invoke(hudMod);
                configManager = configs.getClass().getMethod("getClientConfigManager").invoke(configs);
                inWorldOption = Class.forName("xaero.hud.minimap.common.config.option.MinimapProfiledConfigOptions")
                        .getField("TRACKED_PLAYERS_IN_WORLD").get(null);
                for (Method method : configManager.getClass().getMethods()) {
                    if (method.getName().equals("getEffective") && method.getParameterCount() == 1
                            && method.getParameterTypes()[0].isInstance(inWorldOption)) {
                        getEffective = method;
                        break;
                    }
                }
                if (getEffective == null) {
                    throw new NoSuchMethodException("getEffective");
                }
            }
            return Boolean.TRUE.equals(getEffective.invoke(configManager, inWorldOption));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Unknown Xaero version: assume its default (icons on) so teammates never get two icons
            Constants.LOG.warn("Could not read Xaero's Minimap \"Tracked Players in World\" option; assuming it is on", e);
            optionUnreadable = true;
            return true;
        }
    }
}
