package com.soulsvault.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent party state owned by Casket of Echoes: built-in parties, plus the leader options chosen for parties
 * that come from other mods (FTB Teams, OPAC, vanilla teams). Pending invites live here too but are not saved.
 */
public final class PartyRegistry {
    private static final String NBT_PARTIES = "Parties";
    private static final String NBT_EXTERNAL = "ExternalSettings";
    private static final String NBT_COVENANTS = "Covenants";
    private static final String NBT_LEADER = "Leader";
    private static final String NBT_KEY = "Key";
    private static final String NBT_SETTINGS = "Settings";

    /** A Casket of Echoes built-in party. Members are kept in join order; the leader is always a member. */
    public static final class BuiltinParty {
        private final UUID id;
        private String name;
        private UUID leader;
        private final List<UUID> members = new ArrayList<>();
        private PartySettings settings;

        BuiltinParty(UUID id, String name, UUID leader, PartySettings settings) {
            this.id = id;
            this.name = name;
            this.leader = leader;
            this.settings = settings;
        }

        public UUID getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public UUID getLeader() {
            return this.leader;
        }

        public List<UUID> getMembers() {
            return Collections.unmodifiableList(this.members);
        }

        public PartySettings getSettings() {
            return this.settings;
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", this.id);
            tag.putString("Name", this.name);
            tag.putUUID("Leader", this.leader);
            ListTag members = new ListTag();
            for (UUID member : this.members) {
                members.add(NbtUtils.createUUID(member));
            }
            tag.put("Members", members);
            tag.put(NBT_SETTINGS, this.settings.toTag());
            return tag;
        }

        static BuiltinParty fromTag(CompoundTag tag) {
            BuiltinParty party = new BuiltinParty(
                    tag.getUUID("Id"),
                    tag.getString("Name"),
                    tag.getUUID("Leader"),
                    PartySettings.fromTag(tag.getCompound(NBT_SETTINGS))
            );
            ListTag members = tag.getList("Members", Tag.TAG_INT_ARRAY);
            for (Tag member : members) {
                party.members.add(NbtUtils.loadUUID(member));
            }
            if (!party.members.contains(party.leader)) {
                party.members.add(0, party.leader);
            }
            return party;
        }
    }

    private final Map<UUID, BuiltinParty> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> partyByMember = new HashMap<>();
    private final Map<String, PartySettings> externalSettings = new HashMap<>();
    /** Parties with a live Covenant Casket, with their last known leader (receives unclaimed items). */
    private final Map<String, UUID> covenants = new LinkedHashMap<>();
    private final Map<UUID, List<PartyInvite>> invites = new HashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener != null ? listener : () -> {
        };
    }

    private void markDirty() {
        this.dirtyListener.run();
    }

    // ------------------------------------------------------------------------------------------------ built-in

    @Nullable
    public BuiltinParty getParty(UUID partyId) {
        return this.parties.get(partyId);
    }

    @Nullable
    public BuiltinParty getPartyOf(UUID player) {
        UUID partyId = this.partyByMember.get(player);
        return partyId != null ? this.parties.get(partyId) : null;
    }

    public Collection<BuiltinParty> getParties() {
        return Collections.unmodifiableCollection(this.parties.values());
    }

    public BuiltinParty create(UUID leader, String name) {
        BuiltinParty party = new BuiltinParty(UUID.randomUUID(), name, leader, PartySettings.DEFAULT);
        party.members.add(leader);
        this.parties.put(party.id, party);
        this.partyByMember.put(leader, party.id);
        this.invites.remove(leader);
        markDirty();
        return party;
    }

    public void addMember(BuiltinParty party, UUID player) {
        if (!party.members.contains(player)) {
            party.members.add(player);
        }
        this.partyByMember.put(player, party.id);
        this.invites.remove(player);
        markDirty();
    }

    /**
     * Removes a member, passing leadership to the longest-standing member if needed.
     *
     * @return {@code true} if the party became empty and was deleted
     */
    public boolean removeMember(BuiltinParty party, UUID player) {
        party.members.remove(player);
        this.partyByMember.remove(player);
        if (party.members.isEmpty()) {
            this.parties.remove(party.id);
            markDirty();
            return true;
        }
        if (party.leader.equals(player)) {
            party.leader = party.members.get(0);
        }
        markDirty();
        return false;
    }

    public void disband(BuiltinParty party) {
        for (UUID member : party.members) {
            this.partyByMember.remove(member);
        }
        party.members.clear();
        this.parties.remove(party.id);
        this.invites.values().forEach(list -> list.removeIf(invite -> invite.partyId().equals(party.id)));
        markDirty();
    }

    public void setLeader(BuiltinParty party, UUID newLeader) {
        if (party.members.contains(newLeader)) {
            party.leader = newLeader;
            markDirty();
        }
    }

    public void update(BuiltinParty party, String name, PartySettings settings) {
        party.name = name;
        party.settings = settings;
        markDirty();
    }

    // ------------------------------------------------------------------------------------------------ external

    public PartySettings getExternalSettings(String partyKey) {
        return this.externalSettings.getOrDefault(partyKey, PartySettings.EXTERNAL_DEFAULT);
    }

    public void setExternalSettings(String partyKey, PartySettings settings) {
        this.externalSettings.put(partyKey, settings);
        markDirty();
    }

    // ------------------------------------------------------------------------------------------------ covenants

    public Map<String, UUID> getCovenants() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.covenants));
    }

    public void markCovenant(String partyKey, @Nullable UUID leader) {
        UUID previous = this.covenants.get(partyKey);
        UUID next = leader != null ? leader : previous;
        if (!this.covenants.containsKey(partyKey) || (next != null && !next.equals(previous))) {
            this.covenants.put(partyKey, next != null ? next : new UUID(0L, 0L));
            markDirty();
        }
    }

    public void unmarkCovenant(String partyKey) {
        if (this.covenants.remove(partyKey) != null) {
            markDirty();
        }
    }

    // ------------------------------------------------------------------------------------------------ invites

    public List<PartyInvite> getInvites(UUID player) {
        List<PartyInvite> list = this.invites.get(player);
        if (list == null) {
            return List.of();
        }
        list.removeIf(PartyInvite::isExpired);
        return List.copyOf(list);
    }

    public void addInvite(UUID invitee, PartyInvite invite) {
        List<PartyInvite> list = this.invites.computeIfAbsent(invitee, id -> new ArrayList<>());
        list.removeIf(existing -> existing.partyId().equals(invite.partyId()) || existing.isExpired());
        list.add(invite);
    }

    @Nullable
    public PartyInvite takeInvite(UUID invitee, UUID partyId) {
        List<PartyInvite> list = this.invites.get(invitee);
        if (list == null) {
            return null;
        }
        for (PartyInvite invite : list) {
            if (invite.partyId().equals(partyId) && !invite.isExpired()) {
                list.remove(invite);
                return invite;
            }
        }
        return null;
    }

    public void removeInvite(UUID invitee, UUID partyId) {
        List<PartyInvite> list = this.invites.get(invitee);
        if (list != null) {
            list.removeIf(invite -> invite.partyId().equals(partyId));
        }
    }

    // ------------------------------------------------------------------------------------------------ persistence

    public CompoundTag save(CompoundTag root) {
        ListTag partyList = new ListTag();
        for (BuiltinParty party : this.parties.values()) {
            partyList.add(party.toTag());
        }
        root.put(NBT_PARTIES, partyList);

        ListTag externalList = new ListTag();
        for (Map.Entry<String, PartySettings> entry : this.externalSettings.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_KEY, entry.getKey());
            tag.put(NBT_SETTINGS, entry.getValue().toTag());
            externalList.add(tag);
        }
        root.put(NBT_EXTERNAL, externalList);

        ListTag covenantList = new ListTag();
        for (Map.Entry<String, UUID> entry : this.covenants.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putString(NBT_KEY, entry.getKey());
            tag.putUUID(NBT_LEADER, entry.getValue());
            covenantList.add(tag);
        }
        root.put(NBT_COVENANTS, covenantList);
        return root;
    }

    public void load(CompoundTag root) {
        this.parties.clear();
        this.partyByMember.clear();
        this.externalSettings.clear();
        ListTag partyList = root.getList(NBT_PARTIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < partyList.size(); i++) {
            BuiltinParty party = BuiltinParty.fromTag(partyList.getCompound(i));
            this.parties.put(party.id, party);
            for (UUID member : party.members) {
                this.partyByMember.put(member, party.id);
            }
        }
        ListTag externalList = root.getList(NBT_EXTERNAL, Tag.TAG_COMPOUND);
        for (int i = 0; i < externalList.size(); i++) {
            CompoundTag tag = externalList.getCompound(i);
            this.externalSettings.put(tag.getString(NBT_KEY), PartySettings.fromTag(tag.getCompound(NBT_SETTINGS)));
        }
        this.covenants.clear();
        ListTag covenantList = root.getList(NBT_COVENANTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < covenantList.size(); i++) {
            CompoundTag tag = covenantList.getCompound(i);
            this.covenants.put(tag.getString(NBT_KEY), tag.getUUID(NBT_LEADER));
        }
    }
}
