package com.soulsvault.client.party;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.config.SoulsVaultClientConfig.MarkerMode;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * In-world party markers, drawn the way Xaero's Minimap draws tracked players: each teammate in the player's
 * dimension is a small face at their feet, visible through walls, translucent so it never hides the world, hidden up
 * close and fading in over the next 10 blocks. Looking at a marker (crosshair over it) highlights it: opaque, drawn on
 * top, with name, distance and health. When several overlap, the one nearest the crosshair wins and keeps winning
 * until another is clearly nearer, so the label doesn't flicker. Party touches on top: gold frame for the leader,
 * pulsing red frame for teammates at a quarter health or less.
 * <p>
 * Positions are the live entity when the client tracks the teammate, otherwise the position the server reports
 * every 250 ms, interpolated. The loader calls {@link #capture} while the world renders (to project with that frame's
 * camera) and {@link #render} as a HUD layer.
 */
public final class PartyMarkers {
    /** Face plus a one-pixel frame, in GUI pixels. */
    private static final int ICON = 10;
    private static final int FACE = 8;
    private static final float ALPHA = 0.6F;
    private static final float HIGHLIGHT_ALPHA = 0.95F;
    private static final double FADE_LENGTH = 10.0D;
    /** The crosshair highlights a marker within this many GUI pixels of its centre. */
    private static final float HOVER_REACH = 7.0F;
    /** Another marker takes the highlight only when this much nearer the crosshair (squared GUI pixels). */
    private static final float HOVER_HYSTERESIS = 4.0F;
    private static final float LABEL_SCALE = 0.8F;
    private static final int COLOR_FRAME = 0xFFEAE2D6;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_LOW = 0xFFE05050;
    private static final int COLOR_LABEL_BG = 0x66000000;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_SUB = 0xFFB8AE9E;
    private static final int COLOR_HEALTH = 0xFFC0392B;
    private static final int COLOR_HEALTH_BG = 0xAA1A1416;

    private record Marker(UUID id, String name, float x, float y, double distance, float fade, boolean leader,
                          float healthRatio, @Nullable Player live) {
    }

    private static volatile List<Marker> markers = List.of();
    @Nullable
    private static UUID highlighted;

    private PartyMarkers() {
    }

    /**
     * Whether the Casket draws markers right now: {@link MarkerMode#AUTO} leaves teammates to Xaero's Minimap when
     * it already draws them in the world.
     */
    public static boolean active() {
        MarkerMode mode = SoulsVaultClientConfig.get().partyMarkers();
        return mode == MarkerMode.ON || mode == MarkerMode.AUTO && !XaeroPartyTracker.minimapDrawsTeammatesInWorld();
    }

    /**
     * Projects teammates onto the screen using the camera of the frame being rendered.
     *
     * @param view       camera rotation (the level render's pose)
     * @param projection the frame's projection matrix (includes view bobbing)
     */
    public static void capture(Matrix4f view, Matrix4f projection, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PartySnapshot party = ClientPartyCache.getSnapshot();
        if (party == null || mc.player == null || mc.level == null || !active()) {
            markers = List.of();
            return;
        }
        SoulsVaultClientConfig.Settings config = SoulsVaultClientConfig.get();
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        float width = mc.getWindow().getGuiScaledWidth();
        float height = mc.getWindow().getGuiScaledHeight();
        double minDistance = Math.max(0, config.partyMarkerMinDistance());
        List<Marker> found = new ArrayList<>();
        for (PartySnapshot.Member member : party.members()) {
            if (member.id().equals(mc.player.getUUID()) || !member.online() || member.dead()) {
                continue;
            }
            Player live = mc.level.getPlayerByUUID(member.id());
            Vec3 feet = feetPosition(mc, member.id(), live, partialTick);
            if (feet == null) {
                continue;
            }
            double offX = feet.x - eye.x;
            double offY = feet.y - eye.y;
            double offZ = feet.z - eye.z;
            // Behind the camera (or level with it): nothing sensible to project
            if (offX * look.x() + offY * look.y() + offZ * look.z() < 0.05D) {
                continue;
            }
            double distance = Math.sqrt(offX * offX + offY * offY + offZ * offZ);
            if (distance < minDistance) {
                continue;
            }
            Vector4f point = new Vector4f((float) offX, (float) offY, (float) offZ, 1.0F).mul(view).mul(projection);
            if (point.w() <= 0.0F) {
                continue;
            }
            float x = (1.0F + point.x() / point.w()) / 2.0F * width;
            float y = (1.0F - point.y() / point.w()) / 2.0F * height;
            if (x < -ICON || y < -ICON || x > width + ICON || y > height + ICON) {
                continue;
            }
            float fade = minDistance > 0.0D ? (float) Math.min(1.0D, (distance - minDistance) / FADE_LENGTH) : 1.0F;
            float healthRatio = member.maxHealth() > 0.0F ? Math.min(1.0F, member.health() / member.maxHealth()) : 1.0F;
            found.add(new Marker(member.id(), member.name(), x, y, distance, fade, member.id().equals(party.leader()),
                    healthRatio, live));
        }
        // Far first, so nearer teammates draw on top
        found.sort(Comparator.comparingDouble(Marker::distance).reversed());
        markers = List.copyOf(found);
    }

    @Nullable
    private static Vec3 feetPosition(Minecraft mc, UUID id, @Nullable Player live, float partialTick) {
        if (live != null && !live.isRemoved()) {
            return live.getPosition(partialTick);
        }
        ClientPartyCache.TrackedMember tracked = ClientPartyCache.getTracked(id);
        if (tracked == null || !tracked.dimension().equals(mc.level.dimension())) {
            return null;
        }
        return tracked.position();
    }

    public static void render(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        List<Marker> current = markers;
        if (current.isEmpty() || mc.options.hideGui || !active()) {
            highlighted = null;
            return;
        }
        Marker focus = mc.screen == null ? pickHighlighted(current, screenWidth / 2.0F, screenHeight / 2.0F) : null;
        highlighted = focus != null ? focus.id() : null;
        float guiScale = (float) mc.getWindow().getGuiScale();
        boolean blink = (System.currentTimeMillis() / 300L) % 2L == 0L;
        for (Marker marker : current) {
            if (marker != focus) {
                drawMarker(graphics, mc, marker, ALPHA * marker.fade(), false, guiScale, blink);
            }
        }
        if (focus != null) {
            drawMarker(graphics, mc, focus, HIGHLIGHT_ALPHA, true, guiScale, blink);
        }
    }

    /** The marker under the crosshair nearest its centre, sticking with the current one unless another is clearly nearer. */
    @Nullable
    private static Marker pickHighlighted(List<Marker> current, float centerX, float centerY) {
        Marker best = null;
        float bestDistance = Float.MAX_VALUE;
        Marker previous = null;
        float previousDistance = Float.MAX_VALUE;
        for (Marker marker : current) {
            float dx = marker.x() - centerX;
            float dy = marker.y() - centerY;
            if (Math.abs(dx) > HOVER_REACH || Math.abs(dy) > HOVER_REACH) {
                continue;
            }
            float squared = dx * dx + dy * dy;
            if (squared < bestDistance) {
                best = marker;
                bestDistance = squared;
            }
            if (marker.id().equals(highlighted)) {
                previous = marker;
                previousDistance = squared;
            }
        }
        return previous != null && previousDistance <= bestDistance + HOVER_HYSTERESIS ? previous : best;
    }

    private static void drawMarker(GuiGraphics graphics, Minecraft mc, Marker marker, float alpha, boolean focused,
                                   float guiScale, boolean blink) {
        if (alpha <= 0.02F) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Snap to whole screen pixels so the face stays crisp
        pose.translate(Math.round(marker.x() * guiScale) / guiScale, Math.round(marker.y() * guiScale) / guiScale, 0.0F);
        int half = ICON / 2;
        int frame = marker.healthRatio() <= 0.25F && blink ? COLOR_LOW : marker.leader() ? COLOR_GOLD : COLOR_FRAME;
        graphics.fill(-half, -half, half, half, withAlpha(frame, alpha));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        Player live = marker.live();
        boolean hat = live == null || live.isModelPartShown(PlayerModelPart.HAT);
        boolean upsideDown = live != null ? LivingEntityRenderer.isEntityUpsideDown(live)
                : "Dinnerbone".equals(marker.name()) || "Grumm".equals(marker.name());
        PlayerFaceRenderer.draw(graphics, skinOf(mc, marker), -FACE / 2, -FACE / 2, FACE, hat, upsideDown);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        if (focused) {
            drawLabel(graphics, mc.font, marker, half);
        }
        pose.popPose();
    }

    /** Name and distance to the right of the face, health bar under it. */
    private static void drawLabel(GuiGraphics graphics, Font font, Marker marker, int half) {
        graphics.fill(-half, half + 1, half, half + 2, COLOR_HEALTH_BG);
        graphics.fill(-half, half + 1, -half + Math.round(ICON * marker.healthRatio()), half + 2, COLOR_HEALTH);

        String distance = Math.round(marker.distance()) + "m";
        int nameWidth = font.width(marker.name());
        int labelWidth = nameWidth + 4 + font.width(distance);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(half + 2, 0.0F, 0.0F);
        pose.scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
        graphics.fill(-2, -5, labelWidth + 2, 5, COLOR_LABEL_BG);
        graphics.drawString(font, marker.name(), 0, -4, COLOR_TEXT, false);
        graphics.drawString(font, distance, nameWidth + 4, -4, COLOR_SUB, false);
        pose.popPose();
    }

    private static int withAlpha(int color, float alpha) {
        return Math.round(((color >>> 24) & 0xFF) * alpha) << 24 | (color & 0xFFFFFF);
    }

    private static ResourceLocation skinOf(Minecraft mc, Marker marker) {
        if (marker.live() instanceof AbstractClientPlayer player) {
            return player.getSkinTextureLocation();
        }
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(marker.id()) : null;
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(marker.id());
    }
}
