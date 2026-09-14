package dev.minegame.mines;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class DiceInstance {
    private static final float DISPLAY_SCALE = 0.72F;
    private static final int MAX_AGE_TICKS = 600;

    private final org.bukkit.NamespacedKey entityKey;
    private final UUID id = UUID.randomUUID();
    private final UUID ownerId;
    private final World world;
    private final Vector position;
    private final Vector velocity;
    private final Vector angularVelocity;
    private final int result;
    private final ItemStack sourceItem;
    private final String color;
    private final boolean craps;
    private final UUID sessionId;
    private final int dieNumber;
    private final UUID rollGroupId;
    private final boolean glowing;
    private final boolean particles;
    private Quaternionf rotation = new Quaternionf();
    private Quaternionf settleStartRotation;
    private Quaternionf targetRotation;
    private ItemDisplay display;
    private Interaction interaction;
    private DiceState state = DiceState.THROWN;
    private int age;
    private int bounceCount;
    private int settleTicks;
    private boolean removing;

    DiceInstance(org.bukkit.NamespacedKey entityKey, UUID ownerId, Location spawnLocation,
                 Vector initialVelocity, int result, ItemStack sourceItem, String color, boolean craps,
                 UUID sessionId, int dieNumber, UUID rollGroupId, boolean glowing, boolean particles) {
        this.entityKey = entityKey;
        this.ownerId = ownerId;
        this.world = spawnLocation.getWorld();
        this.position = spawnLocation.toVector();
        this.velocity = initialVelocity;
        this.angularVelocity = new Vector(
                randomAngularVelocity(),
                randomAngularVelocity(),
                randomAngularVelocity()
        );
        this.result = result;
        this.sourceItem = sourceItem.clone();
        this.color = color;
        this.craps = craps;
        this.sessionId = sessionId;
        this.dieNumber = dieNumber;
        this.rollGroupId = rollGroupId;
        this.glowing = glowing;
        this.particles = particles;
    }

    void spawn() {
        if (world == null) {
            throw new IllegalStateException("Die spawn world is missing");
        }
        Location location = location();
        display = world.spawn(location, ItemDisplay.class, spawned -> {
            spawned.setItemStack(sourceItem);
            // DiceOrientation accounts for the skull model in this item context.
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setSilent(true);
            spawned.setGravity(false);
            spawned.setGlowing(glowing);
            spawned.setBillboard(Display.Billboard.FIXED);
            spawned.setDisplayWidth((float) (DiceManager.dieHalfSize() * 2.0D));
            spawned.setDisplayHeight((float) (DiceManager.dieHalfSize() * 2.0D));
            spawned.setInterpolationDuration(1);
            spawned.setTeleportDuration(1);
            spawned.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, id.toString());
            spawned.addScoreboardTag("minegames_dice");
        });
        applyTransformation();
        try {
            interaction = world.spawn(location, Interaction.class, spawned -> {
                spawned.setInteractionWidth((float) (DiceManager.dieHalfSize() * 2.0D));
                spawned.setInteractionHeight((float) (DiceManager.dieHalfSize() * 2.0D));
                spawned.setResponsive(true);
                spawned.setPersistent(false);
                spawned.setInvulnerable(true);
                spawned.setSilent(true);
                spawned.setGravity(false);
                spawned.getPersistentDataContainer().set(entityKey, PersistentDataType.STRING, id.toString());
                spawned.addScoreboardTag("minegames_dice_interaction");
            });
        } catch (RuntimeException ex) {
            display.remove();
            display = null;
            throw ex;
        }
    }

    boolean tick() {
        if (removing || world == null || !world.isChunkLoaded(chunkX(), chunkZ())) {
            return false;
        }
        if (display == null || !display.isValid() || interaction == null || !interaction.isValid()) {
            return false;
        }
        age++;
        if (age > MAX_AGE_TICKS) {
            return false;
        }
        if (state == DiceState.FINISHED) {
            updateEntities();
            return true;
        }
        if (state == DiceState.SETTLING) {
            tickSettling();
            updateEntities();
            return true;
        }

        velocity.setY(velocity.getY() - DiceManager.gravity());
        velocity.setX(velocity.getX() * DiceManager.airDrag());
        velocity.setZ(velocity.getZ() * DiceManager.airDrag());
        rotateWithAngularVelocity();
        moveAndCollide();
        if (position.getY() < world.getMinHeight() - 4.0D) {
            return false;
        }
        angularVelocity.multiply(0.985D);
        if (particles && age % 3 == 0) {
            world.spawnParticle(Particle.END_ROD, location().add(0.0D, 0.02D, 0.0D),
                    1, 0.035D, 0.035D, 0.035D, 0.0D);
        }
        updateEntities();
        return true;
    }

    private void moveAndCollide() {
        Vector movement = velocity.clone();
        int steps = Math.max(1, (int) Math.ceil(movement.length() / 0.12D));
        for (int step = 0; step < steps; step++) {
            Vector delta = movement.clone().multiply(1.0D / steps);

            Vector xCandidate = position.clone().setX(position.getX() + delta.getX());
            if (collides(xCandidate)) {
                velocity.setX(-velocity.getX() * DiceManager.bounceHorizontalDamping());
                angularVelocity.setY(angularVelocity.getY() * 0.86D);
            } else {
                position.setX(xCandidate.getX());
            }

            Vector zCandidate = position.clone().setZ(position.getZ() + delta.getZ());
            if (collides(zCandidate)) {
                velocity.setZ(-velocity.getZ() * DiceManager.bounceHorizontalDamping());
                angularVelocity.setX(angularVelocity.getX() * 0.86D);
            } else {
                position.setZ(zCandidate.getZ());
            }

            Vector yCandidate = position.clone().setY(position.getY() + delta.getY());
            if (!collides(yCandidate)) {
                position.setY(yCandidate.getY());
                continue;
            }

            if (velocity.getY() < 0.0D) {
                Double surface = supportingSurface(position, yCandidate.getY());
                if (surface != null) {
                    position.setY(surface + DiceManager.dieHalfSize());
                    bounce(surface);
                    return;
                }
            }
            velocity.setY(-velocity.getY() * DiceManager.bounceVerticalDamping());
            angularVelocity.multiply(0.82D);
            state = DiceState.BOUNCING;
            return;
        }

    }

    private void bounce(double surface) {
        bounceCount++;
        velocity.setY(Math.abs(velocity.getY()) * DiceManager.bounceVerticalDamping());
        velocity.setX(velocity.getX() * DiceManager.bounceHorizontalDamping());
        velocity.setZ(velocity.getZ() * DiceManager.bounceHorizontalDamping());
        angularVelocity.multiply(0.82D);
        state = DiceState.BOUNCING;
        world.playSound(new Location(world, position.getX(), surface, position.getZ()),
                Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.42F, Math.min(1.35F, 0.78F + bounceCount * 0.07F));

        double horizontalSpeed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());
        if (Math.abs(velocity.getY()) < 0.09D && (horizontalSpeed < 0.12D || bounceCount >= 5)) {
            beginSettling();
        }
    }

    private void beginSettling() {
        if (state == DiceState.SETTLING || state == DiceState.FINISHED) {
            return;
        }
        state = DiceState.SETTLING;
        settleTicks = 0;
        velocity.zero();
        angularVelocity.zero();
        settleStartRotation = new Quaternionf(rotation);
        targetRotation = finalRotation(result, ThreadLocalRandom.current().nextFloat() * (float) (Math.PI * 2.0D));
    }

    private void tickSettling() {
        settleTicks++;
        float progress = Math.min(1.0F, settleTicks / (float) DiceManager.maxSettleTicks());
        float smooth = progress * progress * (3.0F - 2.0F * progress);
        settleStartRotation.slerp(targetRotation, smooth, rotation);
        if (settleTicks >= DiceManager.maxSettleTicks()) {
            rotation.set(targetRotation);
            state = DiceState.FINISHED;
            world.playSound(location(), Sound.BLOCK_STONE_BUTTON_CLICK_OFF, 0.32F, 1.2F);
        }
    }

    private void rotateWithAngularVelocity() {
        rotation.rotateXYZ(
                (float) angularVelocity.getX(),
                (float) angularVelocity.getY(),
                (float) angularVelocity.getZ()
        ).normalize();
    }

    private void updateEntities() {
        Location location = location();
        display.teleport(location);
        interaction.teleport(location);
        applyTransformation();
    }

    private void applyTransformation() {
        display.setTransformation(new Transformation(
                new Vector3f(),
                new Quaternionf(rotation),
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE),
                new Quaternionf()
        ));
    }

    private boolean collides(Vector candidate) {
        BoundingBox dieBox = BoundingBox.of(candidate, DiceManager.dieHalfSize(), DiceManager.dieHalfSize(), DiceManager.dieHalfSize());
        int minX = floor(dieBox.getMinX());
        int maxX = floor(dieBox.getMaxX());
        int minY = floor(dieBox.getMinY());
        int maxY = floor(dieBox.getMaxY());
        int minZ = floor(dieBox.getMinZ());
        int maxZ = floor(dieBox.getMaxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!solid(block)) {
                        continue;
                    }
                    BoundingBox blockBox = block.getBoundingBox();
                    if (blockBox.getVolume() > 0.0D && blockBox.overlaps(dieBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Double supportingSurface(Vector horizontalPosition, double nextY) {
        double oldBottom = position.getY() - DiceManager.dieHalfSize();
        double nextBottom = nextY - DiceManager.dieHalfSize();
        if (nextBottom > oldBottom + 0.001D) {
            return null;
        }
        int minX = floor(horizontalPosition.getX() - DiceManager.dieHalfSize());
        int maxX = floor(horizontalPosition.getX() + DiceManager.dieHalfSize());
        int minZ = floor(horizontalPosition.getZ() - DiceManager.dieHalfSize());
        int maxZ = floor(horizontalPosition.getZ() + DiceManager.dieHalfSize());
        int minY = floor(nextBottom) - 1;
        int maxY = floor(oldBottom) + 1;
        Double highest = null;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!solid(block)) {
                        continue;
                    }
                    BoundingBox blockBox = block.getBoundingBox();
                    if (blockBox.getMaxX() <= horizontalPosition.getX() - DiceManager.dieHalfSize()
                            || blockBox.getMinX() >= horizontalPosition.getX() + DiceManager.dieHalfSize()
                            || blockBox.getMaxZ() <= horizontalPosition.getZ() - DiceManager.dieHalfSize()
                            || blockBox.getMinZ() >= horizontalPosition.getZ() + DiceManager.dieHalfSize()) {
                        continue;
                    }
                    double top = blockBox.getMaxY();
                    if (oldBottom >= top - 0.08D && nextBottom <= top + 0.08D
                            && (highest == null || top > highest)) {
                        highest = top;
                    }
                }
            }
        }
        return highest;
    }

    private boolean solid(Block block) {
        return block.getType() != Material.AIR && block.isSolid() && !block.isPassable() && !block.isLiquid();
    }

    private Quaternionf finalRotation(int face, float yaw) {
        return DiceOrientation.finalRotation(color, face, yaw);
    }

    private float randomAngularVelocity() {
        return (float) (ThreadLocalRandom.current().nextDouble(-0.34D, 0.34D));
    }

    private int floor(double value) {
        return (int) Math.floor(value);
    }

    boolean isInChunk(org.bukkit.Chunk chunk) {
        return world.equals(chunk.getWorld()) && chunkX() == chunk.getX() && chunkZ() == chunk.getZ();
    }

    void removeEntities() {
        removing = true;
        if (display != null && display.isValid()) {
            display.remove();
        }
        if (interaction != null && interaction.isValid()) {
            interaction.remove();
        }
        display = null;
        interaction = null;
    }

    UUID id() {
        return id;
    }

    UUID ownerId() {
        return ownerId;
    }

    boolean craps() {
        return craps;
    }

    UUID sessionId() {
        return sessionId;
    }

    int dieNumber() {
        return dieNumber;
    }

    UUID rollGroupId() {
        return rollGroupId;
    }

    int result() {
        return result;
    }

    ItemStack sourceItem() {
        return sourceItem.clone();
    }

    boolean expired() {
        return age > MAX_AGE_TICKS;
    }

    DiceState state() {
        return state;
    }

    World world() {
        return world;
    }

    Location location() {
        return new Location(world, position.getX(), position.getY(), position.getZ());
    }

    int chunkX() {
        return floor(position.getX()) >> 4;
    }

    int chunkZ() {
        return floor(position.getZ()) >> 4;
    }
}
