package com.othal.lycaon.entity;

import com.othal.lycaon.LycaonConfig;
import com.othal.lycaon.LycaonMemory;
import com.othal.lycaon.ai.LycaonBrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class LycaonEntity extends PathfinderMob {
    public enum Task { IDLE, GO_HOME, GATHER_WOOD, EXPLORE, FOLLOW_PLAYER, CARRY }

    private final SimpleContainer inventory = new SimpleContainer(27); // not saved to disk yet (milestone 2)

    private Task task = Task.IDLE;
    private boolean initialized;
    private boolean finding;           // true until he has walked up to the player for the first time
    private int taskTicks, thinkTicks, breakCooldown, logsGathered;
    private BlockPos target;
    private UUID carriedPlayer;

    public LycaonEntity(EntityType<? extends LycaonEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Von Lycaon"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.ATTACK_DAMAGE, 3.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override public boolean canUse() { return task == Task.IDLE && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof Player) && super.canAttack(target);
    }

    // ------------------------------------------------------------------ main loop

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;

        if (!initialized) {
            initialized = true;
            finding = !LycaonMemory.get().met;
        }
        if (finding) { findPlayerTick(sl); return; }
        if (getTarget() != null) return; // busy fighting

        taskTicks++;
        switch (task) {
            case IDLE -> {
                thinkTicks++;
                if (thinkTicks >= LycaonConfig.get().thinkSeconds * 20) think(sl);
            }
            case GO_HOME -> goHome(sl);
            case GATHER_WOOD -> gatherWood(sl);
            case EXPLORE -> explore(sl);
            case FOLLOW_PLAYER -> followPlayer(sl);
            case CARRY -> carry(sl);
        }
    }

    private void findPlayerTick(ServerLevel sl) {
        Player p = sl.getNearestPlayer(this, 128);
        if (p == null) return;
        if (distanceTo(p) > 5) {
            if (tickCount % 20 == 0) getNavigation().moveTo(p, 1.0);
            getLookControl().setLookAt(p);
        } else {
            finding = false;
            LycaonMemory mem = LycaonMemory.get();
            mem.met = true;
            mem.setHome(blockPosition());
            mem.note("Met " + p.getName().getString() + " and decided to settle near them.");
            ask(sl, p, true,
                "EVENT: You just discovered a person named " + p.getName().getString()
                + " in this world for the first time. Introduce yourself briefly and say you will stay nearby and live here.",
                "Good day. I seem to have found some company. I shall be staying nearby, if you do not mind.");
        }
    }

    // ------------------------------------------------------------------ thinking

    private void think(ServerLevel sl) {
        thinkTicks = 0;
        Player near = sl.getNearestPlayer(this, 48);
        UUID nearId = near == null ? null : near.getUUID();
        if (!LycaonBrain.ready()) { startTask(sl, fallbackAction(), nearId); return; }
        LycaonBrain.ask(sl.getServer(), buildContext(sl, near, "LIFE_DECISION: You are free right now. Decide what to do next, on your own."),
            false, reply -> {
                if (isRemoved() || !isAlive()) return;
                if (reply == null) { startTask(sl, fallbackAction(), nearId); return; }
                handleReply(sl, nearId, reply);
            });
    }

    /** Works with no internet / no key: a simple routine so he still lives his life. */
    private String fallbackAction() {
        int logs = countItems("log");
        if (logs < 8) return "gather_wood";
        return random.nextInt(3) == 0 ? "go_home" : "explore";
    }

    private String buildContext(ServerLevel sl, Player focus, String event) {
        LycaonMemory mem = LycaonMemory.get();
        int light = sl.getMaxLocalRawBrightness(blockPosition());
        StringBuilder sb = new StringBuilder();
        sb.append("EVENT: ").append(event).append('\n');
        sb.append("Light level here (0 = pitch dark, 15 = bright daylight): ").append(light).append('\n');
        sb.append("Health: ").append((int) getHealth()).append("/20\n");
        sb.append("Position: ").append(blockPosition().toShortString()).append('\n');
        sb.append("Home: ").append(mem.hasHome ? mem.home().toShortString() : "none").append('\n');
        sb.append("Current task: ").append(task).append('\n');
        sb.append("Affection toward the player (0-100 scale, higher = warmer): ").append(mem.affection).append('\n');
        if (focus != null) {
            sb.append("Nearby player: ").append(focus.getName().getString())
              .append(" (").append((int) distanceTo(focus)).append(" blocks away)\n");
        } else {
            sb.append("Nearby player: nobody\n");
        }
        sb.append("Inventory: ").append(inventorySummary()).append('\n');
        sb.append("Recent memories: ").append(mem.notes.isEmpty() ? "none" : String.join(" | ", mem.notes)).append('\n');
        return sb.toString();
    }

    private String inventorySummary() {
        Map<String, Integer> counts = new TreeMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) counts.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "empty";
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(v).append("x ").append(k).append(", "));
        return sb.substring(0, sb.length() - 2);
    }

    private int countItems(String contains) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains(contains)) n += s.getCount();
        }
        return n;
    }

    /** Ask the AI (or use a canned line) for something that happened around him. */
    private void ask(ServerLevel sl, Player focus, boolean isChat, String event, String cannedLine) {
        UUID id = focus == null ? null : focus.getUUID();
        if (!LycaonBrain.ready()) { if (cannedLine != null) speak(sl, cannedLine); return; }
        LycaonBrain.ask(sl.getServer(), buildContext(sl, focus, event), isChat, reply -> {
            if (isRemoved() || !isAlive()) return;
            if (reply == null) {
                if (cannedLine != null) speak(sl, cannedLine);
                else if (isChat) systemNote(sl, "AI unavailable: " + LycaonBrain.lastError() + " (see logs/latest.log)");
                return;
            }
            handleReply(sl, id, reply);
        });
    }

    private void handleReply(ServerLevel sl, UUID playerId, LycaonBrain.Reply reply) {
        if (reply.say() != null && !reply.say().isBlank()) speak(sl, reply.say());
        startTask(sl, reply.action() == null ? "none" : reply.action(), playerId, reply.item());
    }

    private void startTask(ServerLevel sl, String action, UUID playerId) { startTask(sl, action, playerId, ""); }

    private void startTask(ServerLevel sl, String action, UUID playerId, String item) {
        Player p = playerId == null ? null : sl.getPlayerByUUID(playerId);
        switch (action) {
            case "go_home" -> begin(Task.GO_HOME);
            case "gather_wood" -> { logsGathered = 0; target = null; begin(Task.GATHER_WOOD); }
            case "explore" -> { target = null; begin(Task.EXPLORE); }
            case "follow_player" -> { if (p != null) begin(Task.FOLLOW_PLAYER); }
            case "hug" -> { if (p != null) hug(sl, p, false); }
            case "give_item" -> { if (p != null) giveItem(sl, p, item); }
            case "carry_player" -> { if (p != null) startCarry(p); }
            default -> { }
        }
    }

    private void begin(Task t) {
        task = t;
        taskTicks = 0;
        getNavigation().stop();
    }

    private void finishTask(String memoryNote) {
        if (memoryNote != null) LycaonMemory.get().note(memoryNote);
        task = Task.IDLE;
        thinkTicks = 0;
        target = null;
        getNavigation().stop();
    }

    // ------------------------------------------------------------------ tasks

    private void goHome(ServerLevel sl) {
        LycaonMemory mem = LycaonMemory.get();
        if (!mem.hasHome) mem.setHome(blockPosition());
        BlockPos h = mem.home();
        if (distanceToSqr(Vec3.atCenterOf(h)) < 9 || taskTicks > 20 * 60) { finishTask(null); return; }
        if (tickCount % 20 == 0) getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.0);
    }

    private void explore(ServerLevel sl) {
        if (target == null) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 12 + random.nextInt(14);
            BlockPos flat = BlockPos.containing(getX() + Math.cos(angle) * dist, 0, getZ() + Math.sin(angle) * dist);
            target = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat);
        }
        if (distanceToSqr(Vec3.atCenterOf(target)) < 16 || taskTicks > 20 * 40) {
            finishTask("Went exploring around " + target.toShortString() + ".");
            return;
        }
        if (tickCount % 20 == 0) getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.9);
    }

    private void followPlayer(ServerLevel sl) {
        Player p = sl.getNearestPlayer(this, 48);
        if (p == null || taskTicks > 20 * 40) { finishTask(null); return; }
        if (distanceTo(p) > 3.5 && tickCount % 10 == 0) getNavigation().moveTo(p, 1.0);
        getLookControl().setLookAt(p);
    }

    private void gatherWood(ServerLevel sl) {
        if (taskTicks > 20 * 90 || logsGathered >= 8) {
            finishTask("Gathered " + logsGathered + " logs.");
            return;
        }
        if (breakCooldown > 0) breakCooldown--;
        if (target == null || !sl.getBlockState(target).is(BlockTags.LOGS)) target = findLog(sl);
        if (target == null) { finishTask("Could not find any trees nearby."); return; }

        Vec3 center = Vec3.atCenterOf(target);
        if (distanceToSqr(center) > 12) {
            if (tickCount % 10 == 0) getNavigation().moveTo(center.x, target.getY(), center.z, 1.0);
        } else {
            getLookControl().setLookAt(center);
            if (breakCooldown <= 0) {
                swing(InteractionHand.MAIN_HAND);
                BlockState state = sl.getBlockState(target);
                List<ItemStack> drops = Block.getDrops(state, sl, target, sl.getBlockEntity(target));
                sl.destroyBlock(target, false);
                for (ItemStack d : drops) inventory.addItem(d);
                logsGathered++;
                breakCooldown = 12; // deliberately slow, like a relaxed player
                target = null;
            }
        }
    }

    private BlockPos findLog(ServerLevel sl) {
        BlockPos o = blockPosition(), best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-20, -6, -20), o.offset(20, 10, 20))) {
            if (!sl.getBlockState(p).is(BlockTags.LOGS)) continue;
            double dx = p.getX() - o.getX(), dy = p.getY() - o.getY(), dz = p.getZ() - o.getZ();
            double score = dx * dx + dz * dz + dy * dy * 4; // prefers low logs close by
            if (score < bestScore) { bestScore = score; best = p.immutable(); }
        }
        return best;
    }

    // ------------------------------------------------------------------ carrying

    private void startCarry(Player p) {
        if (p.startRiding(this)) {
            carriedPlayer = p.getUUID();
            begin(Task.CARRY);
        }
    }

    private void carry(ServerLevel sl) {
        Player p = carriedPlayer == null ? null : sl.getPlayerByUUID(carriedPlayer);
        if (p == null || p.getVehicle() != this || taskTicks > 20 * 10) {
            if (p != null && p.getVehicle() == this) p.stopRiding();
            carriedPlayer = null;
            finishTask(null);
            return;
        }
        LycaonMemory mem = LycaonMemory.get();
        if (mem.hasHome && tickCount % 20 == 0) {
            BlockPos h = mem.home();
            getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.0);
        }
    }

    // ------------------------------------------------------------------ interactions

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(level() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            String name = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
            ItemStack one = held.copyWithCount(1);
            held.shrink(1);
            inventory.addItem(one);
            LycaonMemory.get().affection = Math.min(100, LycaonMemory.get().affection + 2);
            LycaonMemory.get().note(player.getName().getString() + " gave me a gift: " + name + ".");
            ask(sl, player, true, "The player just handed you a gift: 1x " + name + ". React to it.", "How thoughtful of you. Thank you.");
        } else if (player.isShiftKeyDown()) {
            hug(sl, player, true);
        } else {
            getLookControl().setLookAt(player);
            ask(sl, player, true, "The player walked up and greeted you with a friendly tap.", "Yes? Is there something you need?");
        }
        return InteractionResult.SUCCESS;
    }

    private void hug(ServerLevel sl, Player player, boolean askAi) {
        getLookControl().setLookAt(player);
        swing(InteractionHand.MAIN_HAND);
        sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.0, getZ(), 7, 0.4, 0.3, 0.4, 0.02);
        playSound(SoundEvents.VILLAGER_YES, 1.0f, 1.1f);
        player.heal(2.0f);
        LycaonMemory mem = LycaonMemory.get();
        mem.affection = Math.min(100, mem.affection + 3);
        mem.note("Shared a hug with " + player.getName().getString() + ".");
        if (askAi) ask(sl, player, true, "The player just hugged you. React in character.", "...Ahem. That was unexpected, but not unwelcome.");
    }

    private void giveItem(ServerLevel sl, Player player, String itemId) {
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            if (slot == -1) slot = i; // fallback: first non-empty slot
            if (itemId != null && !itemId.isBlank()
                && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals(itemId.replace("minecraft:", ""))) { slot = i; break; }
        }
        if (slot == -1) { speak(sl, "I am afraid my pockets are empty at the moment."); return; }
        ItemStack stack = inventory.removeItem(slot, 8);
        ItemEntity drop = new ItemEntity(sl, getX(), getEyeY() - 0.3, getZ(), stack);
        Vec3 dir = player.position().subtract(position()).normalize().scale(0.25);
        drop.setDeltaMovement(dir.x, 0.1, dir.z);
        drop.setPickUpDelay(20);
        sl.addFreshEntity(drop);
        swing(InteractionHand.MAIN_HAND);
        LycaonMemory.get().note("Gave " + player.getName().getString() + " some " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath() + ".");
    }

    /** Called when a player nearby says something in chat. */
    public void hearPlayer(ServerPlayer player, String text) {
        if (!(level() instanceof ServerLevel sl) || finding) return;
        getLookControl().setLookAt(player);
        ask(sl, player, true, player.getName().getString() + " says to you in chat: \"" + text + "\"", null);
    }

    // ------------------------------------------------------------------ speech

    private void systemNote(ServerLevel sl, String text) {
        Component msg = Component.literal("[Lycaon] " + text).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceTo(this) < 64) p.sendSystemMessage(msg);
        }
    }

    private void speak(ServerLevel sl, String text) {
        Component msg = Component.literal("<Von Lycaon> ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : sl.players()) {
            if (p.distanceTo(this) < 64) p.sendSystemMessage(msg);
        }
    }
}
