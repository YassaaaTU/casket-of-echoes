package com.soulsvault.core;

import com.soulsvault.Constants;
import com.soulsvault.api.EchoSlotType;
import com.soulsvault.api.SoulsVaultApi;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.echo.HotbarEchoSlotType;
import com.soulsvault.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One player's Casket: their profile (per-item pins and refill targets, Build Echoes, Echo Pouch upgrades) bound to
 * a {@link VaultStorage} holding the items. The storage is the player's private one, or their party's shared one
 * when the party shares a Casket; the private storage is then set aside untouched.
 * <p>
 * On the client the same class mirrors what the server synced (entries, echoes and {@link VaultMeta}).
 */
public class PlayerVaultData {
    private static final String NBT_LEGACY_ENTRIES = "Entries";
    private static final String NBT_STORAGE = "Storage";
    private static final String NBT_PREFS = "Prefs";
    private static final String NBT_BUILD_ECHOES = "BuildEchoes";
    private static final String NBT_ECHO_UPGRADES = "EchoSlotUpgrades";
    private static final String NBT_ATTUNED = "Attuned";
    private static final String NBT_KEEPSAKES = "Keepsakes";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final VaultStorage privateStorage;
    @Nullable
    private final UUID owner;
    private VaultStorage storage;
    private final Map<ItemKey, ItemPrefs> prefs = new LinkedHashMap<>();
    private final List<BuildEcho> buildEchoes = new ArrayList<>();
    private int echoSlotUpgrades;
    private boolean attuned;
    private final Keepsakes keepsakes = new Keepsakes();
    private Runnable dirtyListener = () -> {
    };
    @Nullable
    private VaultMeta syncedMeta;

    public PlayerVaultData() {
        this("player:local", null);
    }

    /**
     * @param owner the player this Casket belongs to; recorded as the owner of items they put in a Covenant Casket
     */
    public PlayerVaultData(String privateStorageId, @Nullable UUID owner) {
        this.privateStorage = new VaultStorage(privateStorageId);
        this.storage = this.privateStorage;
        this.owner = owner;
    }

    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener != null ? listener : () -> {
        };
        this.privateStorage.setDirtyListener(this.dirtyListener);
        this.keepsakes.setDirtyListener(this.dirtyListener);
    }

    /**
     * The player's private Keepsakes; never part of a party's shared Casket.
     */
    public Keepsakes getKeepsakes() {
        return this.keepsakes;
    }

    private void markDirty() {
        this.dirtyListener.run();
    }

    // ------------------------------------------------------------------------------------------------ storage

    /**
     * Binds the Casket storage this player uses: a party's shared storage, or {@code null} for their private one.
     */
    public void bindStorage(@Nullable VaultStorage shared) {
        this.storage = shared != null ? shared : this.privateStorage;
    }

    public VaultStorage getStorage() {
        return this.storage;
    }

    public VaultStorage getPrivateStorage() {
        return this.privateStorage;
    }

    public boolean isUsingSharedStorage() {
        return this.storage != this.privateStorage;
    }

    public long getMaxStackCapacity() {
        return this.storage.getMaxStackCapacity();
    }

    public long getCount(ItemKey key) {
        return this.storage.getCount(key);
    }

    /**
     * Stores up to {@code amount} of {@code key} in the bound Casket (respecting {@link VaultRules} and capacity).
     *
     * @return the number of items accepted
     */
    public long deposit(ItemKey key, long amount) {
        return this.storage.deposit(key, amount, this.owner);
    }

    /**
     * @return the number of items actually withdrawn
     */
    public long withdraw(ItemKey key, long requestedAmount) {
        return this.storage.withdraw(key, requestedAmount, this.owner);
    }

    // ------------------------------------------------------------------------------------------------ view

    /**
     * This player's preferences for {@code key}, or the defaults when none are set.
     */
    public ItemPrefs getPrefs(ItemKey key) {
        ItemPrefs existing = this.prefs.get(key);
        return existing != null ? existing : ItemPrefs.defaultsFor(key);
    }

    /**
     * Whether this player has pinned or configured {@code key}.
     */
    public boolean hasPrefs(ItemKey key) {
        return this.prefs.containsKey(key);
    }

    /**
     * The entry this player sees for {@code key} (stored count + their own prefs), or {@code null} if neither
     * anything is stored nor any pin is set.
     */
    @Nullable
    public VaultEntry getEntry(ItemKey key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        long count = this.storage.getCount(key);
        ItemPrefs itemPrefs = this.prefs.get(key);
        if (count <= 0L && (itemPrefs == null || !itemPrefs.isPersistent())) {
            return null;
        }
        ItemPrefs effective = itemPrefs != null ? itemPrefs : ItemPrefs.defaultsFor(key);
        return new VaultEntry(
                key,
                count,
                effective.autoRefill(),
                effective.refillTarget(),
                effective.overflowDeposit(),
                effective.favorite(),
                this.storage.getLastUpdated(key)
        );
    }

    public Collection<VaultEntry> getEntries() {
        Set<ItemKey> keys = new LinkedHashSet<>(this.storage.keys());
        for (Map.Entry<ItemKey, ItemPrefs> pref : this.prefs.entrySet()) {
            if (pref.getValue().isPersistent()) {
                keys.add(pref.getKey());
            }
        }
        List<VaultEntry> entries = new ArrayList<>(keys.size());
        for (ItemKey key : keys) {
            VaultEntry entry = getEntry(key);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public List<BuildEcho> getBuildEchoes() {
        return Collections.unmodifiableList(this.buildEchoes);
    }

    // ------------------------------------------------------------------------------------------------ attunement

    /**
     * Raw attunement flag; use {@link Attunement#isAttuned} for the effective rule (config, creative mode).
     */
    public boolean isAttuned() {
        return this.attuned;
    }

    public void setAttuned(boolean attuned) {
        if (this.attuned != attuned) {
            this.attuned = attuned;
            markDirty();
        }
    }

    // ------------------------------------------------------------------------------------------------ echo slots

    /**
     * Number of Build Echo slots: base config + Echo Pouches, capped by {@code echoes.maxSlots}.
     */
    public int getEchoSlots() {
        if (this.syncedMeta != null) {
            return this.syncedMeta.echoSlots();
        }
        SoulsVaultConfig.Echoes echoes = SoulsVaultConfig.echoes();
        int max = Math.min(Constants.ABSOLUTE_MAX_BUILD_ECHOES, Math.max(1, echoes.maxSlots()));
        return Math.max(1, Math.min(max, echoes.baseSlots() + this.echoSlotUpgrades));
    }

    /**
     * Applies one Echo Pouch.
     *
     * @return {@code false} when already at {@code echoes.maxSlots}
     */
    public boolean addEchoSlotUpgrade() {
        int before = getEchoSlots();
        this.echoSlotUpgrades++;
        if (getEchoSlots() == before) {
            this.echoSlotUpgrades--;
            return false;
        }
        markDirty();
        return true;
    }

    /**
     * Capacity / sharing summary for the client, see {@link VaultMeta}.
     */
    public VaultMeta createMeta(String partyName) {
        if (this.syncedMeta != null) {
            return this.syncedMeta;
        }
        return new VaultMeta(
                this.storage.getMaxStackCapacity(),
                this.storage.getMaxUniqueEntries(),
                this.storage.uniqueEntries(),
                getEchoSlots(),
                isUsingSharedStorage(),
                isUsingSharedStorage() ? partyName : "",
                isUsingSharedStorage() ? this.privateStorage.totalItems() : 0L,
                this.storage.getExpansions()
        );
    }

    /**
     * Capacity and sharing summary (on the client, the last synced one).
     */
    public VaultMeta getMeta() {
        return createMeta("");
    }

    // ------------------------------------------------------------------------------------------------ transactions

    /**
     * Deposits up to {@code requestedAmount} matching {@code key} from the player's 36 main inventory slots.
     *
     * @return total items moved from the player's inventory into the vault
     */
    public long depositFromPlayerInventory(Player player, ItemKey key, long requestedAmount) {
        if (key == null || key.isEmpty() || requestedAmount <= 0L) {
            return 0L;
        }
        Inventory inv = player.getInventory();
        long remainingToDeposit = requestedAmount;
        long totalDeposited = 0L;

        for (int i = 0; i < inv.items.size() && remainingToDeposit > 0L; i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty() || !key.matches(slotStack)) {
                continue;
            }
            int toTake = (int) Math.min(slotStack.getCount(), remainingToDeposit);
            long accepted = deposit(key, toTake);
            if (accepted > 0L) {
                slotStack.shrink((int) accepted);
                if (slotStack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                remainingToDeposit -= accepted;
                totalDeposited += accepted;
            }
            if (accepted < toTake) {
                // Vault entry reached max capacity or the item is not allowed
                break;
            }
        }

        if (totalDeposited > 0L) {
            syncPlayerInventory(player);
        }
        return totalDeposited;
    }

    /**
     * Withdraws up to {@code requestedAmount} of {@code key} from the vault into the player's main inventory,
     * respecting the item's stack size and available inventory space.
     *
     * @return total items inserted into the player's inventory
     */
    public long withdrawToPlayerInventory(Player player, ItemKey key, long requestedAmount) {
        if (key == null || key.isEmpty() || requestedAmount <= 0L) {
            return 0L;
        }
        long stored = getCount(key);
        if (stored <= 0L) {
            return 0L;
        }
        int maxPossible = (int) Math.min(Integer.MAX_VALUE, Math.min(stored, requestedAmount));
        int inserted = insertIntoInventory(player.getInventory(), key, maxPossible);
        if (inserted > 0) {
            withdraw(key, inserted);
            syncPlayerInventory(player);
        }
        return inserted;
    }

    /**
     * Deposits all matching items of the given {@link VaultCategory} from the player's main inventory
     * into the vault (skipping items locked as favorites or within their configured autoRefill target).
     *
     * @return set of {@link ItemKey}s that were modified in the vault
     */
    public Set<ItemKey> depositAllInCategory(Player player, VaultCategory category) {
        Set<ItemKey> modifiedKeys = new LinkedHashSet<>();
        Inventory inv = player.getInventory();

        // First count carried quantities for keys with autoRefill so we do not dump below their refillTarget
        Map<ItemKey, Integer> carriedCounts = new LinkedHashMap<>();
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty()) {
                carriedCounts.merge(ItemKey.fromStack(stack), stack.getCount(), Integer::sum);
            }
        }

        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.fromStack(slotStack);
            if (!category.matches(key)) {
                continue;
            }

            ItemPrefs itemPrefs = this.prefs.get(key);
            if (category == VaultCategory.ALL && itemPrefs != null && itemPrefs.favorite()) {
                continue;
            }
            int allowedToDeposit = slotStack.getCount();
            if (itemPrefs != null && itemPrefs.autoRefill()) {
                int currentTotal = carriedCounts.getOrDefault(key, 0);
                int excess = Math.max(0, currentTotal - itemPrefs.refillTarget());
                allowedToDeposit = Math.min(slotStack.getCount(), excess);
            }

            if (allowedToDeposit <= 0) {
                continue;
            }

            long accepted = deposit(key, allowedToDeposit);
            if (accepted > 0L) {
                slotStack.shrink((int) accepted);
                if (slotStack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                carriedCounts.merge(key, -(int) accepted, Integer::sum);
                modifiedKeys.add(key);
            }
        }

        if (!modifiedKeys.isEmpty()) {
            syncPlayerInventory(player);
        }
        return modifiedKeys;
    }

    /**
     * Flips the favorite pin of {@code key}.
     *
     * @return the updated entry, or {@code null} when nothing is stored or pinned anymore
     */
    @Nullable
    public VaultEntry toggleFavorite(ItemKey key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        ItemPrefs current = getPrefs(key);
        setPrefs(key, current.withFavorite(!current.favorite()));
        return getEntry(key);
    }

    /**
     * Sets the auto-refill, overflow and carry-cap preferences of {@code key}.
     *
     * @return the updated entry, or {@code null} when nothing is stored or pinned anymore
     */
    @Nullable
    public VaultEntry configureRefill(ItemKey key, boolean autoRefill, int refillTarget, boolean overflowDeposit) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        setPrefs(key, getPrefs(key).withRefill(autoRefill, Math.max(1, refillTarget), overflowDeposit));
        return getEntry(key);
    }

    private void setPrefs(ItemKey key, ItemPrefs itemPrefs) {
        if (itemPrefs.isPersistent()) {
            this.prefs.put(key, itemPrefs);
        } else {
            this.prefs.remove(key);
        }
        markDirty();
    }

    /**
     * Rest &amp; Replenish:
     * <ol>
     *   <li>Auto-Sweep Overflow from the player's 36 main inventory slots into the Vault.</li>
     *   <li>Auto-Refill Pinned Items ({@code autoRefill == true}) from the Vault up to {@code refillTarget}, never
     *       past the pack's {@link ReplenishCaps} and as adjusted by the loader's refill event.</li>
     *   <li>Sync deltas to every viewer, toast, and play a muted Souls chime.</li>
     * </ol>
     * Callers check {@link VaultAccess.Type#REST} first.
     */
    public RestReplenishResult performRestAndReplenish(ServerPlayer player) {
        if (Services.PLATFORM.postRestReplenishPre(player)) {
            return new RestReplenishResult(0, 0, Collections.emptySet());
        }
        Inventory inv = player.getInventory();
        Set<ItemKey> changedKeys = new LinkedHashSet<>();
        ItemStack lastSweptStack = ItemStack.EMPTY;
        int totalSweptToVault = 0;
        ItemStack lastRefilledStack = ItemStack.EMPTY;
        int totalRefilledToPlayer = 0;

        // Step 1 — Auto-Sweep Overflow
        Map<ItemKey, Integer> carriedByKey = new LinkedHashMap<>();
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty()) {
                carriedByKey.merge(ItemKey.fromStack(stack), stack.getCount(), Integer::sum);
            }
        }

        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.fromStack(slotStack);
            ItemPrefs itemPrefs = this.prefs.get(key);
            if (itemPrefs == null || !itemPrefs.overflowDeposit()) {
                continue;
            }
            int carried = carriedByKey.getOrDefault(key, 0);
            int excess = carried - itemPrefs.refillTarget();
            if (excess <= 0) {
                continue;
            }
            int toMoveFromSlot = Math.min(slotStack.getCount(), excess);
            long accepted = deposit(key, toMoveFromSlot);
            if (accepted > 0L) {
                lastSweptStack = key.toSingleStack();
                slotStack.shrink((int) accepted);
                if (slotStack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                carriedByKey.put(key, carried - (int) accepted);
                totalSweptToVault += (int) accepted;
                changedKeys.add(key);
            }
        }

        // Step 2 — Auto-Refill Pinned Items, bounded by the pack's Estus-style caps
        List<ReplenishCaps.Rule> capRules = ReplenishCaps.getRules();
        Map<ReplenishCaps.Rule, Integer> carriedPerRule = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, ItemPrefs> pref : new ArrayList<>(this.prefs.entrySet())) {
            ItemKey key = pref.getKey();
            ItemPrefs itemPrefs = pref.getValue();
            long stored = getCount(key);
            if (!itemPrefs.autoRefill() || stored <= 0L) {
                continue;
            }
            int currentCount = countInInventory(inv, key);
            if (currentCount >= itemPrefs.refillTarget()) {
                continue;
            }
            int toWithdraw = (int) Math.min(stored, itemPrefs.refillTarget() - currentCount);
            List<ReplenishCaps.Rule> matching = new ArrayList<>();
            for (ReplenishCaps.Rule rule : capRules) {
                if (rule.matches(key)) {
                    matching.add(rule);
                    int carried = carriedPerRule.computeIfAbsent(rule, r -> r.countCarried(inv));
                    toWithdraw = Math.min(toWithdraw, Math.max(0, rule.maxCarried() - carried));
                }
            }
            // Listeners may raise the amount, but never past what the Casket holds (items are inserted before
            // they are withdrawn, so an unchecked amount would create items)
            toWithdraw = (int) Math.min(stored, Math.max(0, Services.PLATFORM.postReplenishRefill(player, key, toWithdraw)));
            if (toWithdraw <= 0) {
                continue;
            }
            int inserted = insertIntoInventory(inv, key, toWithdraw);
            if (inserted > 0) {
                withdraw(key, inserted);
                for (ReplenishCaps.Rule rule : matching) {
                    carriedPerRule.merge(rule, inserted, Integer::sum);
                }
                lastRefilledStack = key.toSingleStack();
                totalRefilledToPlayer += inserted;
                changedKeys.add(key);
            }
        }

        // Step 3 — Feedback
        if (!changedKeys.isEmpty()) {
            syncPlayerInventory(player);
            VaultSync.commit(player, changedKeys);
            if (totalRefilledToPlayer > 0 && !lastRefilledStack.isEmpty()) {
                Services.PLATFORM.sendOverflowToast(player, lastRefilledStack, totalRefilledToPlayer, true);
            } else if (totalSweptToVault > 0 && !lastSweptStack.isEmpty()) {
                Services.PLATFORM.sendOverflowToast(player, lastSweptStack, totalSweptToVault, false);
            }
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.65F,
                    0.85F
            );
        }
        Services.PLATFORM.postRestReplenishPost(player, totalSweptToVault, totalRefilledToPlayer);
        Milestones.award(player, Milestones.RESTED);
        return new RestReplenishResult(totalSweptToVault, totalRefilledToPlayer, changedKeys);
    }

    // ------------------------------------------------------------------------------------------------ build echoes

    @Nullable
    public BuildEcho getBuildEcho(int index) {
        return index >= 0 && index < this.buildEchoes.size() ? this.buildEchoes.get(index) : null;
    }

    private void putBuildEcho(BuildEcho echo) {
        int index = echo.getIndex();
        while (this.buildEchoes.size() <= index) {
            this.buildEchoes.add(BuildEcho.empty(this.buildEchoes.size()));
        }
        this.buildEchoes.set(index, echo);
        markDirty();
    }

    /**
     * Saves the player's hands, armor, Curios and every registered {@link EchoSlotType} into a Build Echo slot,
     * keeping the slot's existing name and per-type toggles.
     */
    public BuildEcho saveBuildEcho(ServerPlayer player, int index) {
        int clampedIndex = Math.max(0, Math.min(getEchoSlots() - 1, index));
        BuildEcho previous = getBuildEcho(clampedIndex);

        ItemKey mainHand = ItemKey.fromStack(player.getItemBySlot(EquipmentSlot.MAINHAND));
        ItemKey offHand = ItemKey.fromStack(player.getItemBySlot(EquipmentSlot.OFFHAND));
        EnumMap<EquipmentSlot, ItemKey> armor = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armor.put(slot, ItemKey.fromStack(player.getItemBySlot(slot)));
        }
        Map<String, List<ItemKey>> curios = Services.PLATFORM.captureCurioSlots(player);

        Map<String, List<BuildEcho.EchoStack>> extras = new LinkedHashMap<>();
        for (EchoSlotType type : SoulsVaultApi.getEchoSlotTypes()) {
            int slots = type.slotCount(player);
            List<BuildEcho.EchoStack> stacks = new ArrayList<>(slots);
            for (int i = 0; i < slots; i++) {
                ItemStack stack = type.getStack(player, i);
                stacks.add(new BuildEcho.EchoStack(ItemKey.fromStack(stack), type.restoresStackSize() ? stack.getCount() : 1));
            }
            extras.put(type.id().toString(), stacks);
        }

        BuildEcho echo = new BuildEcho(
                clampedIndex,
                previous != null ? previous.getName() : BuildEcho.defaultName(clampedIndex),
                mainHand,
                offHand,
                armor,
                curios,
                extras,
                previous != null ? previous.getTypeToggles() : Map.of(),
                player.getInventory().selected
        );
        putBuildEcho(echo);
        Milestones.award(player, Milestones.ECHO_SAVED);
        return echo;
    }

    /**
     * Renames a Build Echo slot (blank restores the default name).
     */
    public void renameBuildEcho(int index, String name) {
        BuildEcho echo = getBuildEcho(index);
        putBuildEcho((echo != null ? echo : BuildEcho.empty(index)).withName(name));
    }

    /**
     * Sets which extra slot groups (by {@link EchoSlotType} id) a Build Echo restores.
     */
    public void setBuildEchoTypeToggles(int index, Map<String, Boolean> toggles) {
        BuildEcho echo = getBuildEcho(index);
        putBuildEcho((echo != null ? echo : BuildEcho.empty(index)).withTypeToggles(toggles));
    }

    /**
     * Swaps the player's worn equipment, Curios and enabled extra slot groups with a Build Echo through the Casket.
     * Callers check {@link VaultAccess.Type#ECHO_SWAP} first.
     */
    public Set<ItemKey> loadBuildEcho(ServerPlayer player, int index) {
        BuildEcho echo = getBuildEcho(index);
        if (echo == null || index >= getEchoSlots()) {
            return Collections.emptySet();
        }
        Set<ItemKey> modifiedKeys = new LinkedHashSet<>();
        boolean restoresHotbar = echo.isTypeEnabled(HotbarEchoSlotType.INSTANCE)
                && SoulsVaultApi.getEchoSlotType(HotbarEchoSlotType.ID) != null
                && !echo.getExtraSlots(HotbarEchoSlotType.INSTANCE).isEmpty();

        // The mainhand is a hotbar slot; when the hotbar is restored it already covers the held item.
        if (!restoresHotbar) {
            swapEquipmentSlot(player, EquipmentSlot.MAINHAND, echo.getMainHand(), modifiedKeys);
        }
        swapEquipmentSlot(player, EquipmentSlot.OFFHAND, echo.getOffHand(), modifiedKeys);
        for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
            swapEquipmentSlot(player, armorSlot, echo.getArmor(armorSlot), modifiedKeys);
        }
        Services.PLATFORM.swapCurioSlots(player, this, echo.getCurioSlots(), modifiedKeys);

        for (EchoSlotType type : SoulsVaultApi.getEchoSlotTypes()) {
            if (echo.isTypeEnabled(type)) {
                swapExtraSlots(player, type, echo.getExtraSlots(type), modifiedKeys);
            }
        }
        if (restoresHotbar && echo.getSelectedHotbarSlot() >= 0 && echo.getSelectedHotbarSlot() < Inventory.getSelectionSize()) {
            player.getInventory().selected = echo.getSelectedHotbarSlot();
            player.connection.send(new ClientboundSetCarriedItemPacket(echo.getSelectedHotbarSlot()));
        }

        if (!modifiedKeys.isEmpty()) {
            syncPlayerInventory(player);
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    SoundSource.PLAYERS,
                    0.75F,
                    0.95F
            );
        }
        return modifiedKeys;
    }

    private void swapEquipmentSlot(
            ServerPlayer player,
            EquipmentSlot slot,
            ItemKey desiredKey,
            Set<ItemKey> modifiedKeys
    ) {
        ItemStack currentStack = player.getItemBySlot(slot);
        ItemKey currentKey = ItemKey.fromStack(currentStack);
        if (currentKey.equals(desiredKey)) {
            return;
        }

        // Only swap if desiredKey is either EMPTY (unequip to vault) or available in the vault
        if (!desiredKey.isEmpty() && getCount(desiredKey) <= 0L) {
            return;
        }
        // Curse of Binding keeps the piece on, exactly as the vanilla armor slots do
        if (!currentStack.isEmpty() && !player.isCreative() && EnchantmentHelper.hasBindingCurse(currentStack)) {
            return;
        }

        if (!currentStack.isEmpty()) {
            long accepted = deposit(currentKey, currentStack.getCount());
            if (accepted < currentStack.getCount()) {
                withdraw(currentKey, accepted);
                return;
            }
            modifiedKeys.add(currentKey);
            player.setItemSlot(slot, ItemStack.EMPTY);
        }

        if (!desiredKey.isEmpty()) {
            long withdrawn = withdraw(desiredKey, 1L);
            if (withdrawn > 0L) {
                player.setItemSlot(slot, desiredKey.toSingleStack());
                modifiedKeys.add(desiredKey);
            }
        }
    }

    private void swapExtraSlots(
            ServerPlayer player,
            EchoSlotType type,
            List<BuildEcho.EchoStack> saved,
            Set<ItemKey> modifiedKeys
    ) {
        int slots = Math.min(type.slotCount(player), saved.size());
        for (int i = 0; i < slots; i++) {
            BuildEcho.EchoStack desired = saved.get(i);
            ItemStack current = type.getStack(player, i);
            ItemKey currentKey = ItemKey.fromStack(current);

            if (currentKey.equals(desired.key())) {
                if (type.restoresStackSize() && !current.isEmpty()) {
                    int want = Math.min(desired.count(), current.getMaxStackSize()) - current.getCount();
                    long topUp = want > 0 ? withdraw(currentKey, want) : 0L;
                    if (topUp > 0L) {
                        type.setStack(player, i, currentKey.toStack(current.getCount() + (int) topUp));
                        modifiedKeys.add(currentKey);
                    }
                }
                continue;
            }
            if (!desired.isEmpty() && getCount(desired.key()) <= 0L) {
                continue;
            }

            if (!current.isEmpty()) {
                long accepted = deposit(currentKey, current.getCount());
                if (accepted < current.getCount()) {
                    withdraw(currentKey, accepted);
                    continue;
                }
                type.setStack(player, i, ItemStack.EMPTY);
                modifiedKeys.add(currentKey);
            }

            if (!desired.isEmpty()) {
                ItemStack probe = desired.key().toSingleStack();
                int want = type.restoresStackSize() ? Math.min(desired.count(), probe.getMaxStackSize()) : 1;
                long withdrawn = type.mayPlace(player, i, probe) ? withdraw(desired.key(), want) : 0L;
                if (withdrawn > 0L) {
                    type.setStack(player, i, desired.key().toStack((int) withdrawn));
                    modifiedKeys.add(desired.key());
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ inventory

    /**
     * Marks the inventory changed and pushes it to the client right away (both the inventory menu and an open
     * container).
     */
    public static void syncPlayerInventory(Player player) {
        player.getInventory().setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                serverPlayer.containerMenu.broadcastChanges();
            }
        }
    }

    /**
     * How many items matching {@code key} the 36 main inventory slots hold.
     */
    public static int countInInventory(Inventory inv, ItemKey key) {
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && key.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Calculates how many items of {@code key} (up to {@code maxAmount}) can fit into the player's
     * 36 main inventory slots without mutating the inventory.
     */
    public static int getInsertableCapacity(Inventory inv, ItemKey key, int maxAmount) {
        if (key == null || key.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        int maxStackSize = key.getMaxStackSize();
        int capacity = 0;

        for (int i = 0; i < inv.items.size() && capacity < maxAmount; i++) {
            ItemStack existing = inv.items.get(i);
            if (existing.isEmpty()) {
                capacity += maxStackSize;
            } else if (key.matches(existing) && existing.getCount() < maxStackSize) {
                capacity += (maxStackSize - existing.getCount());
            }
        }

        return Math.min(maxAmount, capacity);
    }

    /**
     * Handles world item pickup overflow (EntityItemPickupEvent):
     * <ul>
     *   <li>If the item's prefs have {@code overflowDeposit == true} and the pickup would exceed
     *       {@code refillTarget}, only tops up the player's inventory to {@code refillTarget} and routes the
     *       remainder directly into the Vault.</li>
     *   <li>If the player's inventory cannot fit the full stack and {@code vault.overflowOnFullInventory} is enabled,
     *       inserts what fits into the player's inventory and routes the remainder into the Vault.</li>
     * </ul>
     * Syncs deltas to every viewer and sends an overflow toast when items are routed to the Vault.
     */
    public PickupOverflowResult handleWorldItemPickup(ServerPlayer player, ItemStack entityStack) {
        if (player == null || entityStack == null || entityStack.isEmpty() || !Attunement.isAttuned(player)) {
            return PickupOverflowResult.NONE;
        }

        ItemKey key = ItemKey.fromStack(entityStack);
        if (key.isEmpty() || !VaultRules.canStore(key)) {
            return PickupOverflowResult.NONE;
        }

        Inventory inv = player.getInventory();
        ItemPrefs itemPrefs = this.prefs.get(key);
        int stackCount = entityStack.getCount();
        int allowedToInventory;

        if (itemPrefs != null && itemPrefs.overflowDeposit()) {
            int carriedCount = countInInventory(inv, key);
            int capRemaining = Math.max(0, itemPrefs.refillTarget() - carriedCount);
            allowedToInventory = getInsertableCapacity(inv, key, Math.min(stackCount, capRemaining));
        } else if (SoulsVaultConfig.isOverflowOnFullInventory()) {
            int fitInInventory = getInsertableCapacity(inv, key, stackCount);
            if (fitInInventory >= stackCount) {
                // Entire stack fits normally in the player's inventory; let vanilla handle it
                return PickupOverflowResult.NONE;
            }
            allowedToInventory = fitInInventory;
        } else {
            return PickupOverflowResult.NONE;
        }

        int remainderForVault = stackCount - allowedToInventory;
        if (remainderForVault <= 0) {
            return PickupOverflowResult.NONE;
        }

        long acceptedToVault = deposit(key, remainderForVault);
        if (acceptedToVault <= 0L) {
            return PickupOverflowResult.NONE;
        }

        int insertedToInventory = 0;
        if (allowedToInventory > 0) {
            insertedToInventory = insertIntoInventory(inv, key, allowedToInventory);
        }

        int totalConsumed = insertedToInventory + (int) acceptedToVault;
        entityStack.shrink(totalConsumed);

        if (insertedToInventory > 0) {
            syncPlayerInventory(player);
        }
        VaultSync.commit(player, List.of(key));
        Services.PLATFORM.sendOverflowToast(player, key.toSingleStack(), (int) acceptedToVault, false);
        Milestones.award(player, Milestones.OVERFLOW);

        return new PickupOverflowResult(true, insertedToInventory, (int) acceptedToVault, totalConsumed);
    }

    /**
     * Inserts up to {@code amount} items of {@code key} into the given player inventory's 36 main slots,
     * respecting the item's stack size.
     *
     * @return the number of items actually inserted
     */
    public static int insertIntoInventory(Inventory inv, ItemKey key, int amount) {
        if (key == null || key.isEmpty() || amount <= 0) {
            return 0;
        }
        int maxStackSize = key.getMaxStackSize();
        int remaining = amount;

        // Pass 1: Top up existing partial stacks matching key
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack existing = inv.items.get(i);
            if (!existing.isEmpty() && key.matches(existing) && existing.getCount() < maxStackSize) {
                int space = maxStackSize - existing.getCount();
                int toAdd = Math.min(space, remaining);
                existing.grow(toAdd);
                remaining -= toAdd;
            }
        }

        // Pass 2: Place into empty inventory slots
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack existing = inv.items.get(i);
            if (existing.isEmpty()) {
                int toPlace = Math.min(maxStackSize, remaining);
                inv.items.set(i, key.toStack(toPlace));
                remaining -= toPlace;
            }
        }

        return amount - remaining;
    }

    // ------------------------------------------------------------------------------------------------ client mirror

    /**
     * Client mirror: replaces everything with the first chunk of a full sync ({@code meta} {@code null} to reset).
     */
    public void replaceAllFromSync(Collection<VaultEntry> syncedEntries, Collection<BuildEcho> syncedEchoes,
                                   @Nullable VaultMeta meta) {
        this.storage.clearFromSync();
        this.prefs.clear();
        appendEntriesFromSync(syncedEntries);
        this.buildEchoes.clear();
        if (syncedEchoes != null) {
            this.buildEchoes.addAll(syncedEchoes);
        }
        this.syncedMeta = meta;
    }

    /**
     * Client mirror: adds the entries of a later sync chunk.
     */
    public void appendEntriesFromSync(Collection<VaultEntry> chunkEntries) {
        for (VaultEntry entry : chunkEntries) {
            applyDelta(entry.getKey(), entry);
        }
    }

    /**
     * Client mirror: updates one entry, or removes it when {@code updatedEntry} is {@code null}.
     */
    public void applyDelta(ItemKey key, @Nullable VaultEntry updatedEntry) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (updatedEntry == null) {
            this.storage.setFromSync(key, 0L, 0L);
            this.prefs.remove(key);
            return;
        }
        this.storage.setFromSync(key, updatedEntry.getCount(), updatedEntry.getLastUpdatedEpoch());
        ItemPrefs itemPrefs = new ItemPrefs(
                updatedEntry.isFavorite(),
                updatedEntry.isAutoRefill(),
                updatedEntry.getRefillTarget(),
                updatedEntry.isOverflowDeposit()
        );
        if (itemPrefs.isPersistent()) {
            this.prefs.put(key, itemPrefs);
        } else {
            this.prefs.remove(key);
        }
    }

    // ------------------------------------------------------------------------------------------------ persistence

    public CompoundTag save(CompoundTag root) {
        root.put(NBT_STORAGE, this.privateStorage.save(new CompoundTag()));

        ListTag prefsTag = new ListTag();
        for (Map.Entry<ItemKey, ItemPrefs> entry : this.prefs.entrySet()) {
            if (!entry.getKey().isEmpty() && entry.getValue().isPersistent()) {
                prefsTag.add(entry.getValue().toTag(entry.getKey()));
            }
        }
        root.put(NBT_PREFS, prefsTag);

        ListTag echoesTag = new ListTag();
        for (BuildEcho echo : this.buildEchoes) {
            echoesTag.add(echo.toTag());
        }
        root.put(NBT_BUILD_ECHOES, echoesTag);
        root.putInt(NBT_ECHO_UPGRADES, this.echoSlotUpgrades);
        root.putBoolean(NBT_ATTUNED, this.attuned);
        root.put(NBT_KEEPSAKES, this.keepsakes.save(new CompoundTag()));
        return root;
    }

    public void load(CompoundTag root) {
        this.prefs.clear();
        this.buildEchoes.clear();

        if (root.contains(NBT_STORAGE, Tag.TAG_COMPOUND)) {
            this.privateStorage.load(root.getCompound(NBT_STORAGE));
        } else if (root.contains(NBT_LEGACY_ENTRIES, Tag.TAG_LIST)) {
            // Pre-party format: counts and prefs were stored together on each entry
            ListTag entriesTag = root.getList(NBT_LEGACY_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < entriesTag.size(); i++) {
                VaultEntry entry = VaultEntry.fromLegacyTag(entriesTag.getCompound(i));
                if (entry.getKey().isEmpty()) {
                    continue;
                }
                this.privateStorage.depositUnchecked(entry.getKey(), entry.getCount());
                ItemPrefs itemPrefs = new ItemPrefs(
                        entry.isFavorite(), entry.isAutoRefill(), entry.getRefillTarget(), entry.isOverflowDeposit());
                if (itemPrefs.isPersistent()) {
                    this.prefs.put(entry.getKey(), itemPrefs);
                }
            }
        }

        ListTag prefsTag = root.getList(NBT_PREFS, Tag.TAG_COMPOUND);
        for (int i = 0; i < prefsTag.size(); i++) {
            CompoundTag tag = prefsTag.getCompound(i);
            ItemKey key = ItemPrefs.keyFromTag(tag);
            ItemPrefs itemPrefs = ItemPrefs.fromTag(tag);
            if (!key.isEmpty() && itemPrefs.isPersistent()) {
                this.prefs.put(key, itemPrefs);
            }
        }

        ListTag echoesTag = root.getList(NBT_BUILD_ECHOES, Tag.TAG_COMPOUND);
        for (int i = 0; i < echoesTag.size() && i < Constants.ABSOLUTE_MAX_BUILD_ECHOES; i++) {
            this.buildEchoes.add(BuildEcho.fromTag(echoesTag.getCompound(i)));
        }
        this.echoSlotUpgrades = Math.max(0, root.getInt(NBT_ECHO_UPGRADES));
        // Caskets saved before attunement existed belong to players who were already using them
        this.attuned = !root.contains(NBT_ATTUNED, Tag.TAG_BYTE) || root.getBoolean(NBT_ATTUNED);
        this.keepsakes.load(root.getCompound(NBT_KEEPSAKES));
    }

    /**
     * What a Rest &amp; Replenish moved.
     */
    public record RestReplenishResult(
            int sweptToVaultCount,
            int refilledToPlayerCount,
            Set<ItemKey> modifiedKeys
    ) {
    }

    /**
     * What a world pickup did with the Vault; {@code handled} means the pickup event must be cancelled.
     */
    public record PickupOverflowResult(boolean handled, int insertedToInventory, int sentToVault, int totalConsumed) {
        public static final PickupOverflowResult NONE = new PickupOverflowResult(false, 0, 0, 0);
    }
}
