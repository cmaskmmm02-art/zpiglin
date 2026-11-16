package com.example.zpiglinwatch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * If you do not see any zombified piglin on your screen (loaded ones that are
 * off-screen or behind blocks do not count), a chat message only YOU can see says
 * "closing in 15 seconds". If you still have not seen one when the countdown ends,
 * you are disconnected. Seeing one at any time cancels the countdown.
 */
public class ZPiglinWatchClient implements ClientModInitializer {

    // ---------------- settings ----------------
    /** Seconds until the game closes the connection. */
    private static final int COUNTDOWN_SECONDS = 15;

    /** true = actually disconnect when the countdown ends. false = only show the chat messages. */
    private static final boolean DISCONNECT_AT_END = true;

    /** true = only run in the Nether. false = run in every dimension. */
    private static final boolean ONLY_IN_NETHER = false;

    /** Piglins farther away than this many blocks do not count as seen. */
    private static final double MAX_DISTANCE = 64.0;

    /** true = a piglin behind a wall does not count as seen. */
    private static final boolean REQUIRE_LINE_OF_SIGHT = true;
    // ------------------------------------------

    private static final int COUNTDOWN_TICKS = COUNTDOWN_SECONDS * 20;

    private boolean counting = false;
    private int ticksLeft = COUNTDOWN_TICKS;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void stopCounting() {
        counting = false;
        ticksLeft = COUNTDOWN_TICKS;
    }

    private void onTick(MinecraftClient client) {
        if (client.world == null || client.player == null || client.getNetworkHandler() == null) {
            stopCounting();
            return;
        }
        if (client.currentScreen != null) {
            // Game menus / inventory open: do nothing, keep the state as it is.
            return;
        }
        if (ONLY_IN_NETHER && !client.world.getRegistryKey().getValue().getPath().equals("the_nether")) {
            stopCounting();
            return;
        }

        if (seesZombifiedPiglin(client)) {
            if (counting) {
                chat(client, "Zombified piglin seen - countdown cancelled.");
            }
            stopCounting();
            return;
        }

        // No zombified piglin on screen.
        if (!counting) {
            counting = true;
            ticksLeft = COUNTDOWN_TICKS;
            chat(client, "No zombified piglin on your screen - closing in " + COUNTDOWN_SECONDS + " seconds.");
            return;
        }

        ticksLeft--;
        int secondsLeft = ticksLeft / 20;
        if (ticksLeft > 0 && ticksLeft % 20 == 0 && (secondsLeft == 10 || secondsLeft == 5 || secondsLeft <= 3)) {
            chat(client, "Closing in " + secondsLeft + " seconds.");
        }
        if (ticksLeft <= 0) {
            stopCounting();
            if (DISCONNECT_AT_END) {
                client.getNetworkHandler().getConnection().disconnect(
                        Text.literal("No zombified piglin seen for " + COUNTDOWN_SECONDS + " seconds."));
            } else {
                chat(client, "Countdown finished (disconnect is turned off).");
            }
        }
    }

    /** Message in the chat window, visible only to the local player. */
    private static void chat(MinecraftClient client, String text) {
        client.player.sendMessage(Text.literal("[ZPiglinWatch] " + text), false);
    }

    private boolean seesZombifiedPiglin(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = client.player.getEyePos();

        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).normalize();
        Vec3d up = Vec3d.fromPolar(camera.getPitch() - 90.0f, camera.getYaw()).normalize();
        Vec3d right = forward.crossProduct(up).normalize();

        double vFov = Math.toRadians(client.options.getFov().getValue());
        double aspect = (double) client.getWindow().getFramebufferWidth()
                / Math.max(1, client.getWindow().getFramebufferHeight());
        double tanV = Math.tan(vFov / 2.0);
        double tanH = tanV * aspect;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ZombifiedPiglinEntity) || !entity.isAlive()) {
                continue;
            }
            Vec3d base = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
            if (base.distanceTo(camPos) > MAX_DISTANCE) {
                continue;
            }

            double w = entity.getWidth() / 2.0;
            double h = entity.getHeight();
            Vec3d[] points = new Vec3d[] {
                    base.add(0, h * 0.5, 0),
                    base.add(0, h * 0.9, 0),
                    base.add(0, 0.1, 0),
                    base.add(w, h * 0.5, 0),
                    base.add(-w, h * 0.5, 0),
                    base.add(0, h * 0.5, w),
                    base.add(0, h * 0.5, -w)
            };

            for (Vec3d p : points) {
                if (inFrustum(camPos, forward, up, right, tanH, tanV, p)
                        && (!REQUIRE_LINE_OF_SIGHT || hasLineOfSight(client, camPos, p, entity))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inFrustum(Vec3d camPos, Vec3d forward, Vec3d up, Vec3d right,
                                     double tanH, double tanV, Vec3d p) {
        Vec3d d = p.subtract(camPos);
        double z = d.dotProduct(forward);
        if (z <= 0.05) {
            return false;
        }
        double x = d.dotProduct(right);
        double y = d.dotProduct(up);
        return Math.abs(x) <= z * tanH && Math.abs(y) <= z * tanV;
    }

    private static boolean hasLineOfSight(MinecraftClient client, Vec3d from, Vec3d to, Entity entity) {
        HitResult hit = client.world.raycast(new RaycastContext(
                from, to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                entity));
        return hit.getType() == HitResult.Type.MISS;
    }
}
