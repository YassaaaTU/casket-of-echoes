package com.soulsvault.forge.compat;

import com.soulsvault.Constants;
import com.soulsvault.party.PartyService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional FTB Quests quest sharing for non-FTB parties (soft dependency via reflection): when a party with
 * {@code shareQuests} enabled has an online member complete a quest, the quest is force-completed for the other
 * online members. FTB Teams party teams are skipped since FTB already shares their progress.
 */
public final class FtbQuestsCompat {
    private static final String MOD_ID = "ftbquests";
    private static final ThreadLocal<Boolean> PROPAGATING = ThreadLocal.withInitial(() -> false);

    private static Method getQuestMethod;
    private static Method getOnlineMembersMethod;
    private static Method getDataMethod;
    private static Method teamDataGetMethod;
    private static Method isCompletedMethod;
    private static Method forceProgressMethod;
    private static Constructor<?> progressChangeConstructor;
    private static Method setResetMethod;
    private static Field questFileInstanceField;
    private static Method forAllQuestsMethod;

    private FtbQuestsCompat() {
    }

    public static void init() {
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> completedEvent = Class.forName("dev.ftb.mods.ftbquests.events.ObjectCompletedEvent");
            Class<?> questEventClass = Class.forName("dev.ftb.mods.ftbquests.events.ObjectCompletedEvent$QuestEvent");
            Class<?> progressEvent = Class.forName("dev.ftb.mods.ftbquests.events.ObjectProgressEvent");
            Class<?> teamData = Class.forName("dev.ftb.mods.ftbquests.quest.TeamData");
            Class<?> questObject = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObject");
            Class<?> questObjectBase = Class.forName("dev.ftb.mods.ftbquests.quest.QuestObjectBase");
            Class<?> baseQuestFile = Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile");
            Class<?> serverQuestFile = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
            Class<?> progressChange = Class.forName("dev.ftb.mods.ftbquests.util.ProgressChange");
            Class<?> architecturyEvent = Class.forName("dev.architectury.event.Event");
            Class<?> eventActor = Class.forName("dev.architectury.event.EventActor");
            Class<?> eventResult = Class.forName("dev.architectury.event.EventResult");

            getQuestMethod = questEventClass.getMethod("getQuest");
            getOnlineMembersMethod = progressEvent.getMethod("getOnlineMembers");
            getDataMethod = progressEvent.getMethod("getData");
            teamDataGetMethod = teamData.getMethod("get", Player.class);
            isCompletedMethod = teamData.getMethod("isCompleted", questObject);
            forceProgressMethod = questObjectBase.getMethod("forceProgress", teamData, progressChange);
            progressChangeConstructor = progressChange.getConstructor(baseQuestFile, questObjectBase, UUID.class);
            setResetMethod = progressChange.getMethod("setReset", boolean.class);
            questFileInstanceField = serverQuestFile.getField("INSTANCE");
            forAllQuestsMethod = baseQuestFile.getMethod("forAllQuests", Consumer.class);
            Method pass = eventResult.getMethod("pass");

            Object listener = Proxy.newProxyInstance(FtbQuestsCompat.class.getClassLoader(), new Class<?>[]{eventActor},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "act" -> {
                            onQuestCompleted(args[0]);
                            yield pass.invoke(null);
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "CasketOfEchoesQuestSharing";
                        default -> null;
                    });
            Object questEvent = completedEvent.getField("QUEST").get(null);
            architecturyEvent.getMethod("register", Object.class).invoke(questEvent, listener);
            Constants.LOG.info("FTB Quests party quest sharing hooked");
        } catch (Throwable t) {
            Constants.LOG.warn("FTB Quests party quest sharing unavailable: {}", t.toString());
        }
    }

    /**
     * Gives every listed player each quest any of them has completed (offline catch-up). Players sharing the same
     * FTB team data are skipped against each other.
     */
    public static void syncMembers(List<ServerPlayer> players) {
        if (forAllQuestsMethod == null || players.size() < 2) {
            return;
        }
        try {
            Object questFile = questFileInstanceField.get(null);
            if (questFile == null) {
                return;
            }
            List<Object> quests = new ArrayList<>();
            forAllQuestsMethod.invoke(questFile, (Consumer<Object>) quests::add);
            List<Object> teamData = new ArrayList<>(players.size());
            for (ServerPlayer player : players) {
                teamData.add(teamDataGetMethod.invoke(null, player));
            }
            PROPAGATING.set(true);
            try {
                for (Object quest : quests) {
                    boolean anyDone = false;
                    for (Object data : teamData) {
                        if (data != null && (Boolean) isCompletedMethod.invoke(data, quest)) {
                            anyDone = true;
                            break;
                        }
                    }
                    if (!anyDone) {
                        continue;
                    }
                    for (int i = 0; i < players.size(); i++) {
                        Object data = teamData.get(i);
                        if (data != null && !(Boolean) isCompletedMethod.invoke(data, quest)) {
                            Object change = progressChangeConstructor.newInstance(questFile, quest, players.get(i).getUUID());
                            setResetMethod.invoke(change, false);
                            forceProgressMethod.invoke(quest, data, change);
                        }
                    }
                }
            } finally {
                PROPAGATING.set(false);
            }
        } catch (Throwable t) {
            Constants.LOG.debug("FTB Quests party catch-up failed: {}", t.toString());
        }
    }

    private static void onQuestCompleted(Object event) {
        if (PROPAGATING.get()) {
            return;
        }
        try {
            Object quest = getQuestMethod.invoke(event);
            Object completedData = getDataMethod.invoke(event);
            @SuppressWarnings("unchecked")
            List<ServerPlayer> completers = (List<ServerPlayer>) getOnlineMembersMethod.invoke(event);
            List<ServerPlayer> targets = new ArrayList<>();
            for (ServerPlayer completer : completers) {
                for (ServerPlayer target : PartyService.questShareTargets(completer)) {
                    if (!completers.contains(target) && !targets.contains(target)) {
                        targets.add(target);
                    }
                }
            }
            Object questFile = questFileInstanceField.get(null);
            for (ServerPlayer target : targets) {
                Object targetData = teamDataGetMethod.invoke(null, target);
                if (targetData == null || targetData == completedData || (Boolean) isCompletedMethod.invoke(targetData, quest)) {
                    continue;
                }
                Object change = progressChangeConstructor.newInstance(questFile, quest, target.getUUID());
                setResetMethod.invoke(change, false);
                PROPAGATING.set(true);
                try {
                    forceProgressMethod.invoke(quest, targetData, change);
                } finally {
                    PROPAGATING.set(false);
                }
            }
        } catch (Throwable t) {
            Constants.LOG.debug("FTB Quests party quest sharing failed: {}", t.toString());
        }
    }
}
