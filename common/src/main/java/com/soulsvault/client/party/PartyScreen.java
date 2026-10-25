package com.soulsvault.client.party;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.client.gui.widget.GothicButton;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.config.SoulsVaultConfig.PartyRule;
import com.soulsvault.network.PacketC2SPartyAction;
import com.soulsvault.party.CasketJoinPolicy;
import com.soulsvault.party.PartyInvite;
import com.soulsvault.party.PartySettings;
import com.soulsvault.party.PartySnapshot;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Party management GUI: members (invite, kick, promote, leave, disband, merge a set-aside Casket), pending invites
 * (with the casket-choice prompt and an "are you sure" confirmation before deleting a Casket), leader options that
 * apply live when saved, and the player's own party HUD settings.
 */
public class PartyScreen extends Screen {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 232;
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_MEMBER_ROWS = 6;

    private static final int COLOR_BG = 0xF4121013;
    private static final int COLOR_PANE = 0xEB0D0B0E;
    private static final int COLOR_BRONZE = 0xFF8C7853;
    private static final int COLOR_BRONZE_MUTED = 0xFF4A3F2C;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_TEXT = 0xFFEAE2D6;
    private static final int COLOR_SUB = 0xFFA89E8E;
    private static final int COLOR_WARNING = 0xFFE06C50;

    private enum Tab {
        MEMBERS,
        INVITES,
        OPTIONS
    }

    @Nullable
    private final Screen parent;
    private Tab tab;
    private int left;
    private int top;
    private int memberScroll;

    private String inviteName = "";
    private CasketJoinPolicy invitePolicy = CasketJoinPolicy.MERGE;
    private final Map<UUID, CasketJoinPolicy> chosenPolicies = new HashMap<>();
    @Nullable
    private PartyInvite confirmingOverride;
    private CasketJoinPolicy covenantPolicy = CasketJoinPolicy.MERGE;
    private boolean confirmingCovenantOverride;
    @Nullable
    private String editedName;
    @Nullable
    private PartySettings editedSettings;
    @Nullable
    private String editedForKey;
    private int structureSignature;

    private EditBox inviteBox;
    private EditBox nameBox;
    private final Runnable cacheListener = this::onCacheUpdated;

    public PartyScreen(@Nullable Screen parent, boolean invitesTab) {
        super(Component.translatable("gui.soulsvault.party.title"));
        this.parent = parent;
        this.tab = invitesTab ? Tab.INVITES : Tab.MEMBERS;
    }

    // ------------------------------------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        this.structureSignature = computeSignature();
        ClientPartyCache.removeListener(this.cacheListener);
        ClientPartyCache.addListener(this.cacheListener);
        syncEditedSettings();

        int tabW = 92;
        addTabButton(Tab.MEMBERS, Component.translatable("gui.soulsvault.party.tab.members"), this.left + 6, tabW);
        addTabButton(Tab.INVITES, Component.translatable("gui.soulsvault.party.tab.invites", ClientPartyCache.getInvites().size()),
                this.left + 8 + tabW, tabW);
        addTabButton(Tab.OPTIONS, Component.translatable("gui.soulsvault.party.tab.options"), this.left + 10 + tabW * 2, tabW);

        switch (this.tab) {
            case MEMBERS -> initMembersTab();
            case INVITES -> initInvitesTab();
            case OPTIONS -> initOptionsTab();
        }

        addRenderableWidget(new GothicButton(this.left + WIDTH - 56, this.top + HEIGHT - 20, 50, 14,
                Component.translatable("gui.done"), btn -> onClose()));
    }

    @Override
    public void removed() {
        ClientPartyCache.removeListener(this.cacheListener);
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (this.inviteBox != null) {
            this.inviteBox.tick();
        }
        if (this.nameBox != null) {
            this.nameBox.tick();
        }
    }

    private void onCacheUpdated() {
        int signature = computeSignature();
        if (signature != this.structureSignature) {
            Minecraft.getInstance().execute(this::rebuildWidgets);
        }
    }

    /**
     * Rebuild widgets only when the party's structure changes, not on every health update.
     */
    private static int computeSignature() {
        PartySnapshot party = ClientPartyCache.getSnapshot();
        List<Object> parts = new ArrayList<>();
        if (party != null) {
            parts.add(party.key());
            parts.add(party.leader());
            parts.add(party.viewerCanManage());
            parts.add(party.viewerCanInvite());
            parts.add(party.sharesCasket());
            parts.add(party.viewerInCovenant());
            parts.add(party.settings());
            parts.add(party.name());
            for (PartySnapshot.Member member : party.members()) {
                parts.add(member.id());
            }
        }
        for (PartyInvite invite : ClientPartyCache.getInvites()) {
            parts.add(invite.partyId());
            parts.add(invite.policy());
        }
        parts.add(ClientPartyCache.getPrivateCasketItems() > 0L);
        return parts.hashCode();
    }

    private void syncEditedSettings() {
        PartySnapshot party = ClientPartyCache.getSnapshot();
        String key = party != null ? party.key() : null;
        if (!Objects.equals(key, this.editedForKey) || this.editedSettings == null) {
            this.editedForKey = key;
            this.editedSettings = party != null ? party.settings() : PartySettings.DEFAULT;
            this.editedName = party != null ? party.name() : "";
        }
    }

    private void addTabButton(Tab target, Component label, int x, int w) {
        GothicButton button = new GothicButton(x, this.top + 20, w, 14, label, btn -> {
            this.tab = target;
            this.confirmingOverride = null;
            rebuildWidgets();
        });
        button.setHighlighted(this.tab == target);
        addRenderableWidget(button);
    }

    private static boolean builtinActiveClientSide() {
        SoulsVaultConfig.Party config = SoulsVaultConfig.party();
        if (!config.builtinPartiesEnabled()) {
            return false;
        }
        return switch (config.provider()) {
            case BUILTIN -> true;
            case AUTO -> !Services.PLATFORM.isModLoaded("ftbteams") && !Services.PLATFORM.isModLoaded("openpartiesandclaims");
            default -> false;
        };
    }

    private static void send(PacketC2SPartyAction packet) {
        Services.PLATFORM.sendToServer(packet);
    }

    // ------------------------------------------------------------------------------------------------ members tab

    private void initMembersTab() {
        PartySnapshot party = ClientPartyCache.getSnapshot();
        UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        boolean builtin = builtinActiveClientSide();

        int visibleRows = visibleMemberRows(party);
        if (party != null && party.isBuiltin() && self != null && self.equals(party.leader())) {
            List<PartySnapshot.Member> members = party.members();
            int maxScroll = Math.max(0, members.size() - visibleRows);
            this.memberScroll = Mth.clamp(this.memberScroll, 0, maxScroll);
            for (int v = 0; v < visibleRows && this.memberScroll + v < members.size(); v++) {
                PartySnapshot.Member member = members.get(this.memberScroll + v);
                if (member.id().equals(self)) {
                    continue;
                }
                int rowY = this.top + 44 + v * ROW_HEIGHT;
                addRenderableWidget(new GothicButton(this.left + WIDTH - 66, rowY + 3, 28, 13,
                        Component.translatable("gui.soulsvault.party.kick"),
                        btn -> send(PacketC2SPartyAction.member(PacketC2SPartyAction.Action.KICK, member.id()))));
                addRenderableWidget(new GothicButton(this.left + WIDTH - 36, rowY + 3, 28, 13,
                        Component.translatable("gui.soulsvault.party.promote"),
                        btn -> send(PacketC2SPartyAction.member(PacketC2SPartyAction.Action.TRANSFER, member.id()))));
            }
        }

        if (isCovenantPending(party)) {
            initCovenantPrompt();
        }

        int controlsY = this.top + HEIGHT - 46;
        if (builtin && (party == null || party.viewerCanInvite())) {
            this.inviteBox = new EditBox(this.font, this.left + 10, controlsY + 3, 100, 10,
                    Component.translatable("gui.soulsvault.party.invite.hint"));
            this.inviteBox.setBordered(false);
            this.inviteBox.setMaxLength(16);
            this.inviteBox.setTextColor(COLOR_TEXT);
            this.inviteBox.setValue(this.inviteName);
            this.inviteBox.setResponder(value -> this.inviteName = value);
            addRenderableWidget(this.inviteBox);

            boolean shares = party != null ? party.sharesCasket()
                    : SoulsVaultConfig.party().sharedCasket().resolve(PartySettings.DEFAULT.sharedCasket());
            if (shares) {
                addRenderableWidget(new GothicButton(this.left + 116, controlsY, 112, 15, policyLabel(this.invitePolicy), btn -> {
                    this.invitePolicy = nextAllowedPolicy(this.invitePolicy);
                    btn.setMessage(policyLabel(this.invitePolicy));
                }));
            }
            addRenderableWidget(new GothicButton(this.left + 232, controlsY, 60, 15,
                    Component.translatable("gui.soulsvault.party.invite"), btn -> {
                if (!this.inviteName.isBlank()) {
                    send(PacketC2SPartyAction.invite(this.inviteName.trim(), this.invitePolicy));
                    this.inviteName = "";
                    if (this.inviteBox != null) {
                        this.inviteBox.setValue("");
                    }
                }
            }));
        } else {
            this.inviteBox = null;
        }

        int footerY = this.top + HEIGHT - 20;
        int footerX = this.left + 6;
        if (party == null && builtin) {
            addRenderableWidget(new GothicButton(footerX, footerY, 80, 14, Component.translatable("gui.soulsvault.party.create"),
                    btn -> send(PacketC2SPartyAction.simple(PacketC2SPartyAction.Action.CREATE))));
        }
        if (party != null && party.isBuiltin()) {
            boolean leader = self != null && self.equals(party.leader());
            addRenderableWidget(new GothicButton(footerX, footerY, 50, 14, Component.translatable("gui.soulsvault.party.leave"),
                    btn -> send(PacketC2SPartyAction.simple(PacketC2SPartyAction.Action.LEAVE))));
            footerX += 54;
            if (leader) {
                addRenderableWidget(new GothicButton(footerX, footerY, 56, 14, Component.translatable("gui.soulsvault.party.disband"),
                        btn -> send(PacketC2SPartyAction.simple(PacketC2SPartyAction.Action.DISBAND))));
                footerX += 60;
            }
        }
        if (party != null && party.sharesCasket() && party.viewerInCovenant() && ClientPartyCache.getPrivateCasketItems() > 0L
                && SoulsVaultConfig.party().allowCasketMerge()) {
            addRenderableWidget(new GothicButton(footerX, footerY, 118, 14,
                    Component.translatable("gui.soulsvault.party.merge_private",
                            VaultItemSlotWidget.formatCompactCount(ClientPartyCache.getPrivateCasketItems())),
                    btn -> send(PacketC2SPartyAction.simple(PacketC2SPartyAction.Action.MERGE_PRIVATE))));
        }
    }

    private static boolean isCovenantPending(@Nullable PartySnapshot party) {
        return party != null && party.sharesCasket() && !party.viewerInCovenant();
    }

    private static int visibleMemberRows(@Nullable PartySnapshot party) {
        return isCovenantPending(party) ? VISIBLE_MEMBER_ROWS - 2 : VISIBLE_MEMBER_ROWS;
    }

    /**
     * The casket choice for a member of a sharing party who hasn't joined its Covenant yet (joins made in FTB Teams,
     * OPAC or /team, or sharing turned on after joining).
     */
    private void initCovenantPrompt() {
        int cardY = this.top + 44 + (VISIBLE_MEMBER_ROWS - 2) * ROW_HEIGHT;
        long privateItems = ClientPartyCache.getPrivateCasketItems();
        if (this.confirmingCovenantOverride) {
            addRenderableWidget(new GothicButton(this.left + 10, cardY + 14, 170, 14,
                    Component.translatable("gui.soulsvault.party.override.confirm",
                            VaultItemSlotWidget.formatCompactCount(privateItems)).withStyle(ChatFormatting.RED),
                    btn -> {
                        send(PacketC2SPartyAction.covenantJoin(CasketJoinPolicy.OVERRIDE, true));
                        this.confirmingCovenantOverride = false;
                        rebuildWidgets();
                    }));
            addRenderableWidget(new GothicButton(this.left + 184, cardY + 14, 60, 14, Component.translatable("gui.cancel"), btn -> {
                this.confirmingCovenantOverride = false;
                rebuildWidgets();
            }));
            return;
        }
        if (!isPolicyAllowed(this.covenantPolicy)) {
            this.covenantPolicy = nextAllowedPolicy(this.covenantPolicy);
        }
        addRenderableWidget(new GothicButton(this.left + 10, cardY + 14, 150, 14, policyLabel(this.covenantPolicy), btn -> {
            this.covenantPolicy = nextAllowedPolicy(this.covenantPolicy);
            btn.setMessage(policyLabel(this.covenantPolicy));
        }));
        GothicButton join = new GothicButton(this.left + 164, cardY + 14, 90, 14,
                Component.translatable("gui.soulsvault.covenant.join"), btn -> {
            if (this.covenantPolicy == CasketJoinPolicy.OVERRIDE && privateItems > 0L) {
                this.confirmingCovenantOverride = true;
                rebuildWidgets();
            } else {
                send(PacketC2SPartyAction.covenantJoin(this.covenantPolicy, false));
            }
        });
        join.active = !(this.covenantPolicy == CasketJoinPolicy.EMPTY_FIRST && privateItems > 0L);
        addRenderableWidget(join);
    }

    private static Component policyLabel(CasketJoinPolicy policy) {
        return Component.translatable("gui.soulsvault.party.policy.label", policy.displayName());
    }

    private static boolean isPolicyAllowed(CasketJoinPolicy policy) {
        SoulsVaultConfig.Party config = SoulsVaultConfig.party();
        return switch (policy) {
            case MERGE -> config.allowCasketMerge();
            case OVERRIDE -> config.allowCasketOverride();
            case EMPTY_FIRST -> true;
        };
    }

    private static CasketJoinPolicy nextAllowedPolicy(CasketJoinPolicy current) {
        CasketJoinPolicy next = current.next();
        for (int i = 0; i < CasketJoinPolicy.values().length && !isPolicyAllowed(next); i++) {
            next = next.next();
        }
        return next;
    }

    // ------------------------------------------------------------------------------------------------ invites tab

    private void initInvitesTab() {
        this.inviteBox = null;
        List<PartyInvite> invites = ClientPartyCache.getInvites();
        long privateItems = ClientPartyCache.getPrivateCasketItems();

        if (this.confirmingOverride != null) {
            PartyInvite invite = this.confirmingOverride;
            int y = this.top + 120;
            addRenderableWidget(new GothicButton(this.left + 20, y, 160, 16,
                    Component.translatable("gui.soulsvault.party.override.confirm",
                            VaultItemSlotWidget.formatCompactCount(privateItems)).withStyle(ChatFormatting.RED),
                    btn -> {
                        send(PacketC2SPartyAction.accept(invite.partyId(), CasketJoinPolicy.OVERRIDE, true));
                        this.confirmingOverride = null;
                        rebuildWidgets();
                    }));
            addRenderableWidget(new GothicButton(this.left + 186, y, 94, 16, Component.translatable("gui.cancel"), btn -> {
                this.confirmingOverride = null;
                rebuildWidgets();
            }));
            return;
        }

        int cardY = this.top + 42;
        for (PartyInvite invite : invites.subList(0, Math.min(3, invites.size()))) {
            CasketJoinPolicy chosen = this.chosenPolicies.getOrDefault(invite.partyId(), invite.policy());
            boolean casketMatters = invite.partySharesCasket() && privateItems > 0L;
            if (casketMatters) {
                addRenderableWidget(new GothicButton(this.left + 10, cardY + 24, 150, 14, policyLabel(chosen), btn -> {
                    CasketJoinPolicy next = nextInviteeChoice(invite, this.chosenPolicies.getOrDefault(invite.partyId(), invite.policy()));
                    this.chosenPolicies.put(invite.partyId(), next);
                    btn.setMessage(policyLabel(next));
                }));
            }
            GothicButton accept = new GothicButton(this.left + WIDTH - 118, cardY + 24, 54, 14,
                    Component.translatable("gui.soulsvault.party.accept"), btn -> {
                CasketJoinPolicy policy = this.chosenPolicies.getOrDefault(invite.partyId(), invite.policy());
                if (casketMatters && policy == CasketJoinPolicy.OVERRIDE) {
                    this.confirmingOverride = invite;
                    rebuildWidgets();
                } else {
                    send(PacketC2SPartyAction.accept(invite.partyId(), policy, false));
                }
            });
            accept.active = !(casketMatters && chosen == CasketJoinPolicy.EMPTY_FIRST);
            addRenderableWidget(accept);
            addRenderableWidget(new GothicButton(this.left + WIDTH - 60, cardY + 24, 54, 14,
                    Component.translatable("gui.soulsvault.party.decline"),
                    btn -> send(PacketC2SPartyAction.decline(invite.partyId()))));
            cardY += 52;
        }
    }

    /**
     * The invitee may take the inviter's proposal, or instead empty their Casket first or override it.
     */
    private static CasketJoinPolicy nextInviteeChoice(PartyInvite invite, CasketJoinPolicy current) {
        List<CasketJoinPolicy> options = new ArrayList<>();
        for (CasketJoinPolicy policy : new CasketJoinPolicy[]{invite.policy(), CasketJoinPolicy.EMPTY_FIRST, CasketJoinPolicy.OVERRIDE}) {
            if (!options.contains(policy) && isPolicyAllowed(policy)) {
                options.add(policy);
            }
        }
        if (options.isEmpty()) {
            return current;
        }
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    // ------------------------------------------------------------------------------------------------ options tab

    private void initOptionsTab() {
        this.inviteBox = null;
        PartySnapshot party = ClientPartyCache.getSnapshot();
        SoulsVaultConfig.Party rules = SoulsVaultConfig.party();
        int y = this.top + 44;

        if (party != null) {
            boolean canEdit = party.viewerCanManage();
            if (party.isBuiltin() && canEdit) {
                this.nameBox = new EditBox(this.font, this.left + 82, y + 3, 150, 10, Component.translatable("gui.soulsvault.party.name"));
                this.nameBox.setBordered(false);
                this.nameBox.setMaxLength(32);
                this.nameBox.setTextColor(COLOR_TEXT);
                this.nameBox.setValue(this.editedName != null ? this.editedName : party.name());
                this.nameBox.setResponder(value -> this.editedName = value);
                addRenderableWidget(this.nameBox);
            } else {
                this.nameBox = null;
            }
            y += 18;

            PartySettings settings = this.editedSettings != null ? this.editedSettings : party.settings();
            y = addToggle(y, "shared_casket", rules.sharedCasket(), canEdit, settings.sharedCasket(),
                    value -> this.editedSettings = withShared(value));
            y = addToggle(y, "friendly_fire", rules.preventFriendlyFire(), canEdit, settings.preventFriendlyFire(),
                    value -> this.editedSettings = withFriendlyFire(value));
            y = addToggle(y, "share_advancements", rules.shareAdvancements(), canEdit, settings.shareAdvancements(),
                    value -> this.editedSettings = withAdvancements(value));
            if (party.provider() != PartyProviderType.FTB_TEAMS) {
                y = addToggle(y, "share_quests", rules.shareQuests(), canEdit, settings.shareQuests(),
                        value -> this.editedSettings = withQuests(value));
            }
            if (party.isBuiltin()) {
                y = addToggle(y, "members_invite", PartyRule.LEADER_CHOICE, canEdit, settings.membersCanInvite(),
                        value -> this.editedSettings = withInvite(value));
            }
            if (canEdit) {
                addRenderableWidget(new GothicButton(this.left + 10, y + 2, 150, 14, Component.translatable("gui.soulsvault.party.options.save"),
                        btn -> send(PacketC2SPartyAction.saveSettings(
                                this.editedName != null ? this.editedName : party.name(),
                                this.editedSettings != null ? this.editedSettings : party.settings()))));
            }
        }

        // The player's own HUD preferences (saved immediately in the client config)
        SoulsVaultClientConfig.Settings client = SoulsVaultClientConfig.get();
        int hudY = this.top + HEIGHT - 42;
        addRenderableWidget(new GothicButton(this.left + 10, hudY, 88, 14,
                Component.translatable(client.partyHudVisible() ? "gui.soulsvault.party.hud.shown" : "gui.soulsvault.party.hud.hidden"),
                btn -> {
                    SoulsVaultClientConfig.setPartyHud(!SoulsVaultClientConfig.get().partyHudVisible(),
                            SoulsVaultClientConfig.get().partyHudStyle(), SoulsVaultClientConfig.get().partyHudAnchor());
                    rebuildWidgets();
                }));
        addRenderableWidget(new GothicButton(this.left + 102, hudY, 92, 14,
                Component.translatable("gui.soulsvault.party.hud.style",
                        Component.translatable("gui.soulsvault.party.hud.style." + client.partyHudStyle().name().toLowerCase(Locale.ROOT))),
                btn -> {
                    SoulsVaultClientConfig.PartyHudStyle[] styles = SoulsVaultClientConfig.PartyHudStyle.values();
                    SoulsVaultClientConfig.Settings now = SoulsVaultClientConfig.get();
                    SoulsVaultClientConfig.setPartyHud(now.partyHudVisible(),
                            styles[(now.partyHudStyle().ordinal() + 1) % styles.length], now.partyHudAnchor());
                    rebuildWidgets();
                }));
        addRenderableWidget(new GothicButton(this.left + 198, hudY, 94, 14,
                Component.translatable("gui.soulsvault.party.hud.anchor",
                        Component.translatable("gui.soulsvault.party.hud.anchor." + client.partyHudAnchor().name().toLowerCase(Locale.ROOT))),
                btn -> {
                    SoulsVaultClientConfig.HudAnchor[] anchors = SoulsVaultClientConfig.HudAnchor.values();
                    SoulsVaultClientConfig.Settings now = SoulsVaultClientConfig.get();
                    SoulsVaultClientConfig.setPartyHud(now.partyHudVisible(), now.partyHudStyle(),
                            anchors[(now.partyHudAnchor().ordinal() + 1) % anchors.length]);
                    rebuildWidgets();
                }));
        int visualsY = this.top + HEIGHT - 20;
        addRenderableWidget(new GothicButton(this.left + 10, visualsY, 110, 14,
                Component.translatable("gui.soulsvault.party.hud.portrait",
                        Component.translatable("gui.soulsvault.party.hud.portrait." + client.partyPortrait().name().toLowerCase(Locale.ROOT))),
                btn -> {
                    SoulsVaultClientConfig.Settings now = SoulsVaultClientConfig.get();
                    SoulsVaultClientConfig.PortraitMode[] modes = SoulsVaultClientConfig.PortraitMode.values();
                    SoulsVaultClientConfig.setPartyVisuals(modes[(now.partyPortrait().ordinal() + 1) % modes.length], now.partyMarkers());
                    rebuildWidgets();
                }));
        String markerMode = client.partyMarkers().name().toLowerCase(Locale.ROOT);
        boolean xaeroDraws = client.partyMarkers() == SoulsVaultClientConfig.MarkerMode.AUTO && !PartyMarkers.active();
        GothicButton markers = addRenderableWidget(new GothicButton(this.left + 124, visualsY, 110, 14,
                Component.translatable(xaeroDraws ? "gui.soulsvault.party.hud.markers.auto_xaero" : "gui.soulsvault.party.hud.markers." + markerMode),
                btn -> {
                    SoulsVaultClientConfig.Settings now = SoulsVaultClientConfig.get();
                    SoulsVaultClientConfig.MarkerMode[] modes = SoulsVaultClientConfig.MarkerMode.values();
                    SoulsVaultClientConfig.setPartyVisuals(now.partyPortrait(), modes[(now.partyMarkers().ordinal() + 1) % modes.length]);
                    rebuildWidgets();
                }));
        markers.setTooltip(Tooltip.create(Component.translatable("gui.soulsvault.party.hud.markers." + markerMode + ".desc")));
    }

    private int addToggle(int y, String key, PartyRule rule, boolean canEdit, boolean value, Consumer<Boolean> setter) {
        boolean forced = rule != PartyRule.LEADER_CHOICE;
        boolean effective = rule.resolve(value);
        MutableComponent label = Component.translatable("gui.soulsvault.party.option." + key)
                .append(": ")
                .append(Component.translatable(effective ? "options.on" : "options.off"));
        if (forced) {
            label.append(" ").append(Component.translatable("gui.soulsvault.party.option.forced"));
        }
        GothicButton button = new GothicButton(this.left + 10, y, WIDTH - 20, 14, label, btn -> {
            setter.accept(!currentValue(key));
            rebuildWidgets();
        });
        button.active = canEdit && !forced;
        button.setHighlighted(effective);
        addRenderableWidget(button);
        return y + 17;
    }

    private boolean currentValue(String key) {
        PartySettings settings = this.editedSettings != null ? this.editedSettings : PartySettings.DEFAULT;
        return switch (key) {
            case "shared_casket" -> settings.sharedCasket();
            case "friendly_fire" -> settings.preventFriendlyFire();
            case "share_advancements" -> settings.shareAdvancements();
            case "share_quests" -> settings.shareQuests();
            default -> settings.membersCanInvite();
        };
    }

    private PartySettings withShared(boolean value) {
        return modify(s -> new PartySettings(value, s.preventFriendlyFire(), s.shareAdvancements(), s.shareQuests(), s.membersCanInvite()));
    }

    private PartySettings withFriendlyFire(boolean value) {
        return modify(s -> new PartySettings(s.sharedCasket(), value, s.shareAdvancements(), s.shareQuests(), s.membersCanInvite()));
    }

    private PartySettings withAdvancements(boolean value) {
        return modify(s -> new PartySettings(s.sharedCasket(), s.preventFriendlyFire(), value, s.shareQuests(), s.membersCanInvite()));
    }

    private PartySettings withQuests(boolean value) {
        return modify(s -> new PartySettings(s.sharedCasket(), s.preventFriendlyFire(), s.shareAdvancements(), value, s.membersCanInvite()));
    }

    private PartySettings withInvite(boolean value) {
        return modify(s -> new PartySettings(s.sharedCasket(), s.preventFriendlyFire(), s.shareAdvancements(), s.shareQuests(), value));
    }

    private PartySettings modify(Function<PartySettings, PartySettings> change) {
        return change.apply(this.editedSettings != null ? this.editedSettings : PartySettings.DEFAULT);
    }

    // ------------------------------------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.fill(this.left, this.top, this.left + WIDTH, this.top + HEIGHT, COLOR_BG);
        VaultItemSlotWidget.drawBorder(graphics, this.left, this.top, WIDTH, HEIGHT, COLOR_BRONZE);
        VaultItemSlotWidget.drawBorder(graphics, this.left + 2, this.top + 2, WIDTH - 4, HEIGHT - 4, COLOR_BRONZE_MUTED);
        graphics.fill(this.left + 6, this.top + 38, this.left + WIDTH - 6, this.top + HEIGHT - 24, COLOR_PANE);

        PartySnapshot party = ClientPartyCache.getSnapshot();
        Component heading = party != null
                ? Component.translatable("gui.soulsvault.party.heading", party.name(), providerName(party.provider()))
                : this.title;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(heading.getString(), WIDTH - 16), this.left + 8, this.top + 7, COLOR_GOLD, true);

        switch (this.tab) {
            case MEMBERS -> renderMembers(graphics, party);
            case INVITES -> renderInvites(graphics);
            case OPTIONS -> renderOptions(graphics, party);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static Component providerName(PartyProviderType provider) {
        return Component.translatable("gui.soulsvault.party.provider." + provider.name().toLowerCase(Locale.ROOT));
    }

    private void renderMembers(GuiGraphics graphics, @Nullable PartySnapshot party) {
        if (party == null) {
            Component message = builtinActiveClientSide()
                    ? Component.translatable("gui.soulsvault.party.none.builtin")
                    : Component.translatable("gui.soulsvault.party.none.external");
            int y = this.top + 50;
            for (var line : this.font.split(message, WIDTH - 24)) {
                graphics.drawString(this.font, line, this.left + 12, y, COLOR_SUB, false);
                y += 11;
            }
        } else {
            List<PartySnapshot.Member> members = party.members();
            String ownDimension = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.dimension().location().toString() : "";
            int visibleRows = visibleMemberRows(party);
            for (int v = 0; v < visibleRows && this.memberScroll + v < members.size(); v++) {
                PartySnapshot.Member member = members.get(this.memberScroll + v);
                int rowY = this.top + 44 + v * ROW_HEIGHT;
                graphics.fill(this.left + 8, rowY, this.left + WIDTH - 8, rowY + ROW_HEIGHT - 2, 0xF016131A);
                VaultItemSlotWidget.drawBorder(graphics, this.left + 8, rowY, WIDTH - 16, ROW_HEIGHT - 2, COLOR_BRONZE_MUTED);
                PlayerFaceRenderer.draw(graphics, skinOf(member.id()), this.left + 11, rowY + 1, 16);
                boolean leader = member.id().equals(party.leader());
                String name = (leader ? "★ " : "") + member.name();
                graphics.drawString(this.font, name, this.left + 32, rowY + 5, member.online() ? COLOR_TEXT : COLOR_SUB, false);
                Component status;
                if (!member.online()) {
                    status = Component.translatable("gui.soulsvault.party.hud.offline");
                } else if (member.dead()) {
                    status = Component.translatable("gui.soulsvault.party.hud.dead");
                } else {
                    status = Component.translatable("gui.soulsvault.party.member.stats",
                            Math.round(member.health()), Math.round(member.maxHealth()), member.food(), member.armor())
                            .append(member.dimension().equals(ownDimension) ? "" : "  ~");
                }
                graphics.pose().pushPose();
                graphics.pose().translate(this.left + 128.0F, rowY + 6.0F, 0.0F);
                graphics.pose().scale(0.75F, 0.75F, 1.0F);
                graphics.drawString(this.font, status, 0, 0, member.online() ? COLOR_SUB : 0xFF6B6257, false);
                graphics.pose().popPose();
            }
            if (isCovenantPending(party)) {
                int cardY = this.top + 44 + (VISIBLE_MEMBER_ROWS - 2) * ROW_HEIGHT;
                graphics.fill(this.left + 8, cardY, this.left + WIDTH - 8, cardY + 32, 0xF0231B12);
                VaultItemSlotWidget.drawBorder(graphics, this.left + 8, cardY, WIDTH - 16, 32, COLOR_GOLD);
                Component prompt = this.confirmingCovenantOverride
                        ? Component.translatable("gui.soulsvault.covenant.override_warning",
                                VaultItemSlotWidget.formatCompactCount(ClientPartyCache.getPrivateCasketItems()))
                        : Component.translatable("gui.soulsvault.covenant.choose",
                                VaultItemSlotWidget.formatCompactCount(ClientPartyCache.getPrivateCasketItems()));
                graphics.drawString(this.font, this.font.plainSubstrByWidth(prompt.getString(), WIDTH - 24),
                        this.left + 12, cardY + 3, this.confirmingCovenantOverride ? COLOR_WARNING : COLOR_GOLD, false);
            } else if (party.sharesCasket()) {
                graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.casket_shared"),
                        this.left + 10, this.top + HEIGHT - 60, COLOR_GOLD, false);
            }
        }
        if (this.inviteBox != null) {
            int boxY = this.top + HEIGHT - 46;
            graphics.fill(this.left + 6, boxY, this.left + 112, boxY + 15, 0xF00D0B0E);
            VaultItemSlotWidget.drawBorder(graphics, this.left + 6, boxY, 106, 15, this.inviteBox.isFocused() ? COLOR_GOLD : COLOR_BRONZE);
            if (this.inviteBox.getValue().isEmpty() && !this.inviteBox.isFocused()) {
                graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.invite.hint"), this.left + 10, boxY + 4, 0xFF6B6257, false);
            }
        }
    }

    private void renderInvites(GuiGraphics graphics) {
        List<PartyInvite> invites = ClientPartyCache.getInvites();
        long privateItems = ClientPartyCache.getPrivateCasketItems();
        if (this.confirmingOverride != null) {
            int y = this.top + 56;
            for (var line : this.font.split(Component.translatable("gui.soulsvault.party.override.warning",
                    VaultItemSlotWidget.formatCompactCount(privateItems), this.confirmingOverride.partyName()), WIDTH - 40)) {
                graphics.drawString(this.font, line, this.left + 20, y, COLOR_WARNING, false);
                y += 11;
            }
            return;
        }
        if (invites.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.invites.none"), this.left + 12, this.top + 50, COLOR_SUB, false);
            return;
        }
        int cardY = this.top + 42;
        for (PartyInvite invite : invites.subList(0, Math.min(3, invites.size()))) {
            graphics.fill(this.left + 8, cardY, this.left + WIDTH - 8, cardY + 46, 0xF016131A);
            VaultItemSlotWidget.drawBorder(graphics, this.left + 8, cardY, WIDTH - 16, 46, COLOR_BRONZE_MUTED);
            graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.invite.from", invite.inviterName(), invite.partyName()),
                    this.left + 12, cardY + 4, COLOR_TEXT, false);
            Component detail;
            if (!invite.partySharesCasket()) {
                detail = Component.translatable("gui.soulsvault.party.invite.no_shared");
            } else if (privateItems <= 0L) {
                detail = Component.translatable("gui.soulsvault.party.invite.casket_empty");
            } else {
                CasketJoinPolicy chosen = this.chosenPolicies.getOrDefault(invite.partyId(), invite.policy());
                detail = chosen == CasketJoinPolicy.EMPTY_FIRST
                        ? Component.translatable("gui.soulsvault.party.invite.empty_first_blocked", VaultItemSlotWidget.formatCompactCount(privateItems))
                        : chosen.description();
            }
            graphics.pose().pushPose();
            graphics.pose().translate(this.left + 12.0F, cardY + 15.0F, 0.0F);
            graphics.pose().scale(0.8F, 0.8F, 1.0F);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(detail.getString(), (int) ((WIDTH - 28) / 0.8F)), 0, 0, COLOR_SUB, false);
            graphics.pose().popPose();
            cardY += 52;
        }
    }

    private void renderOptions(GuiGraphics graphics, @Nullable PartySnapshot party) {
        if (party == null) {
            graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.options.no_party"), this.left + 12, this.top + 50, COLOR_SUB, false);
        } else {
            graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.name"), this.left + 12, this.top + 47, COLOR_SUB, false);
            if (this.nameBox != null) {
                graphics.fill(this.left + 78, this.top + 43, this.left + 236, this.top + 58, 0xF00D0B0E);
                VaultItemSlotWidget.drawBorder(graphics, this.left + 78, this.top + 43, 158, 15, this.nameBox.isFocused() ? COLOR_GOLD : COLOR_BRONZE);
            } else {
                graphics.drawString(this.font, party.name(), this.left + 82, this.top + 47, COLOR_TEXT, false);
            }
            if (!party.viewerCanManage()) {
                graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.options.read_only"),
                        this.left + 12, this.top + HEIGHT - 66, 0xFF6B6257, false);
            }
        }
        graphics.drawString(this.font, Component.translatable("gui.soulsvault.party.hud.section"), this.left + 10, this.top + HEIGHT - 54, COLOR_GOLD, false);
    }

    private static ResourceLocation skinOf(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(id) : null;
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }

    // ------------------------------------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.tab == Tab.MEMBERS) {
            this.memberScroll += delta > 0 ? -1 : 1;
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RETURN && this.inviteBox != null && this.inviteBox.isFocused() && !this.inviteName.isBlank()) {
            send(PacketC2SPartyAction.invite(this.inviteName.trim(), this.invitePolicy));
            this.inviteName = "";
            this.inviteBox.setValue("");
            return true;
        }
        boolean editing = (this.inviteBox != null && this.inviteBox.isFocused()) || (this.nameBox != null && this.nameBox.isFocused());
        if (editing && keyCode != InputConstants.KEY_ESCAPE) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
