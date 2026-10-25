package com.soulsvault.client.party;

import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * In-world party markers (like Xaero's player icons, but party-aware): each teammate in the player's dimension shows
 * as their face with name and distance above their head, visible through walls. Teammates the client doesn't track
 * (far away) use the positions the server sends twice a second.
 * <p>
 * The loader calls {@link #capture} while the world renders (to project positions with that frame's camera) and
 * {@link #render} as a HUD layer.
 */
public final class PartyMarkers {
    private static final int FACE_SIZE = 10;
    private static final int COLOR_FRAME = 0xCC121013;
    private static final int COLOR_BRONZE = 0xFF8C7853;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_LOW = 0xFFE05050;
    private static final int COLOR_TEXT = 0xFFEAE2D6;
    private static final int COLOR_SUB = 0xFFA89E8E;
    /** Above the head: player height plus room for the name. */
    private static final double HEAD_OFFSET = 0.6D;
    private static final double REPORTED_HEAD_HEIGHT = 2.4D;

    private record Marker(UUID id, String name, float x, float y, double distance, boolean leader, boolean low) {
    }

    private static volatile List<Marker> markers = List.of();

    private PartyMarkers() {
    }

    /**
     * Projects teammates onto the screen using the camera of the frame being rendered.
     *
     * @param view       camera rotation (the level render's pose)
     * @param projection the frame's projection matrix
     */
    public static void capture(Matrix4f view, Matrix4f projection, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        PartySnapshot party = ClientPartyCache.getSnapshot();
        SoulsVaultClientConfig.Settings config = SoulsVaultClientConfig.get();
        if (party == null || mc.player == null || mc.level == null || !config.partyMarkers()) {
            markers = List.of();
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        double minDistance = Math.max(0, config.partyMarkerMinDistance());
        List<Marker> found = new ArrayList<>();
        for (PartySnapshot.Member member : party.members()) {
            if (member.id().equals(mc.player.getUUID()) || !member.online() || member.dead()) {
                continue;
            }
            Vec3 head = headPosition(mc, member.id(), partialTick);
            if (head == null) {
                continue;
            }
            double distance = head.distanceTo(camera);
            if (distance < minDistance) {
                continue;
            }
            Vector4f point = new Vector4f((float) (head.x - camera.x), (float) (head.y - camera.y), (float) (head.z - camera.z), 1.0F);
            point.mul(view);
            point.mul(projection);
            if (point.w() <= 0.05F) {
                continue;
            }
            float ndcX = point.x() / point.w();
            float ndcY = point.y() / point.w();
            if (Math.abs(ndcX) > 1.1F || Math.abs(ndcY) > 1.1F) {
                continue;
            }
            float ratio = member.maxHealth() > 0.0F ? member.health() / member.maxHealth() : 1.0F;
            found.add(new Marker(member.id(), member.name(), (ndcX * 0.5F + 0.5F) * width, (0.5F - ndcY * 0.5F) * height,
                    distance, member.id().equals(party.leader()), ratio <= 0.25F));
        }
        found.sort(Comparator.comparingDouble(Marker::distance).reversed());
        markers = List.copyOf(found);
    }

    private static Vec3 headPosition(Minecraft mc, UUID id, float partialTick) {
        Player live = mc.level.getPlayerByUUID(id);
        if (live != null) {
            return live.getPosition(partialTick).add(0.0D, live.getBbHeight() + HEAD_OFFSET, 0.0D);
        }
        Vec3 reported = ClientPartyCache.getReportedPosition(id);
        return reported != null ? reported.add(0.0D, REPORTED_HEAD_HEIGHT, 0.0D) : null;
    }

    public static void render(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        List<Marker> current = markers;
        if (current.isEmpty() || mc.options.hideGui || !SoulsVaultClientConfig.get().partyMarkers()) {
            return;
        }
        Font font = mc.font;
        for (Marker marker : current) {
            int x = Math.round(marker.x());
            int y = Math.round(marker.y());
            int left = x - FACE_SIZE / 2 - 1;
            int top = y - FACE_SIZE / 2 - 1;
            graphics.fill(left, top, left + FACE_SIZE + 2, top + FACE_SIZE + 2, COLOR_FRAME);
            PlayerFaceRenderer.draw(graphics, skinOf(mc, marker.id()), left + 1, top + 1, FACE_SIZE);
            int border = marker.low() && (System.currentTimeMillis() / 300L) % 2L == 0L
                    ? COLOR_LOW : (marker.leader() ? COLOR_GOLD : COLOR_BRONZE);
            VaultItemSlotWidget.drawBorder(graphics, left, top, FACE_SIZE + 2, FACE_SIZE + 2, border);

            drawCentered(graphics, font, marker.name(), x, top - 7, COLOR_TEXT);
            drawCentered(graphics, font, Math.round(marker.distance()) + "m", x, top + FACE_SIZE + 4, COLOR_SUB);
        }
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        float scale = 0.7F;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - font.width(text) * scale / 2.0F, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    private static ResourceLocation skinOf(Minecraft mc, UUID id) {
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(id) : null;
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }
}
