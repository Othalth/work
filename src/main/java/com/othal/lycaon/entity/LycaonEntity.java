package com.othal.lycaon.entity;

import com.othal.lycaon.LycaonConfig;
import com.othal.lycaon.LycaonMemory;
import com.othal.lycaon.ai.LycaonBrain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Von Lycaon: a player-like companion. He cannot be clicked; the player talks to him in chat.
 * The AI decides WHAT to do; this class does the actual walking, mining, crafting, fighting and building.
 */
public class LycaonEntity extends PathfinderMob {
    public enum Task { IDLE, GO_HOME, GATHER_WOOD, MINE, CRAFT, BUILD_HOME, HUNT, COLLECT, EXPLORE, FOLLOW_PLAYER, CARRY, FLEE }
    public enum Kind { LOG, STONE, COAL, IRON }

    private static final EntityDataAccessor<Boolean> CARRYING =
        SynchedEntityData.defineId(LycaonEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int PLANKS_NEEDED = 50; // a 4x4 hut: 34 wall blocks + 16 roof blocks

    private final SimpleContainer inventory = new SimpleContainer(36);
    private final Set<UUID> givenItems = new HashSet<>();

    private Task task = Task.IDLE;
    private Kind kind = Kind.LOG;
    private boolean initialized, finding;
    private int taskTicks, thinkTicks, harvestLimit, harvested, eatingTicks, craftTicks, placeCooldown, planIndex;
    private int noTargetUntil;
    private float mineProgress;
    private BlockPos target, standPos, planOrigin;
    private UUID carriedPlayer;
    private String pending; // "hug" or "give:<item>" - done once he has walked up to the player
    private List<BlockPos> plan = new ArrayList<>();

    public LycaonEntity(EntityType<? extends LycaonEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCustomName(Component.literal("Von Lycaon"));
        setCustomNameVisible(true);
        setDropChance(EquipmentSlot.MAINHAND, 0.0f); // his tools live in his inventory, not as extra loot
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 1.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CARRYING, false);
    }

    public boolean isCarrying() { return entityData.get(CARRYING); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Creeper.class, 8.0f, 1.0, 1.25));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.003f) {
            @Override public boolean canUse() { return task == Task.IDLE && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f, 0.02f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 5, true, false, e -> !(e instanceof Creeper)));
    }

    @Override
    public boolean canAttack(LivingEntity victim) {
        return !(victim instanceof Player) && !(victim instanceof Creeper) && super.canAttack(victim);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("LycaonInventory", inventory.createTag(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("LycaonInventory")) {
            inventory.fromTag(tag.getList("LycaonInventory", Tag.TAG_COMPOUND), level().registryAccess());
        }
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            Containers.dropContents(sl, blockPosition(), inventory);
            inventory.clearContent();
            LycaonMemory.get().note("I was defeated in battle.");
        }
        super.die(source);
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

        if (tickCount % 4 == 0) collectNearbyItems(sl);
        if (eatingTicks > 0) { tickEating(); return; }

        // fighting: the goals do the hitting; he runs when badly hurt
        if (getTarget() != null && task != Task.FLEE) {
            if (tickCount % 20 == 0) equipWeapon();
            if (getHealth() < 8.0f) startFlee();
            else return;
        }

        // eat when hurt and not in a fight
        if (getTarget() == null && task != Task.FLEE && task != Task.CARRY
            && tickCount % 40 == 0 && getHealth() < getMaxHealth() - 6.0f && countFood() > 0) {
            startEating();
            return;
        }

        taskTicks++;
        switch (task) {
            case IDLE -> idleTick(sl);
            case GO_HOME -> goHome();
            case GATHER_WOOD, MINE -> harvest(sl);
            case CRAFT -> craftTick();
            case BUILD_HOME -> buildHome(sl);
            case HUNT -> hunt(sl);
            case COLLECT -> collectTask(sl);
            case EXPLORE -> explore(sl);
            case FOLLOW_PLAYER -> followPlayer(sl);
            case CARRY -> carry(sl);
            case FLEE -> flee();
        }
    }

    private void idleTick(ServerLevel sl) {
        thinkTicks++;
        if (tickCount % 20 == 0) walkToNearbyItem(sl, 10);
        if (thinkTicks >= LycaonConfig.get().thinkSeconds * 20) think(sl);
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
        if (!LycaonBrain.ready()) { startTask(sl, autoPlan(), nearId, ""); return; }
        LycaonBrain.ask(sl.getServer(), buildContext(sl, near, "LIFE_DECISION: You are free right now. Decide what to do next, on your own."),
            false, reply -> {
                if (isRemoved() || !isAlive()) return;
                if (reply == null) { startTask(sl, autoPlan(), nearId, ""); return; }
                handleReply(sl, nearId, reply);
            });
    }

    /** The survival ladder he follows by himself (also used when there is no AI / no internet). */
    private String autoPlan() {
        LycaonMemory mem = LycaonMemory.get();
        int wood = countTag(ItemTags.LOGS) + countTag(ItemTags.PLANKS) / 4;
        boolean searching = tickCount < noTargetUntil;

        if (canCraftSomething()) return "craft";
        if (!has(ItemTags.PICKAXES) || !has(ItemTags.AXES) || !has(ItemTags.SWORDS)) return searching ? "explore" : "gather_wood";
        if (!hasAny(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)
            || !hasAny(Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE)
            || !hasAny(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)) return searching ? "explore" : "mine_stone";
        if (!mem.homeBuilt) return wood >= 14 ? "build_home" : (searching ? "explore" : "gather_wood");
        if (countFood() < 2) return "hunt";
        return switch (random.nextInt(4)) {
            case 0 -> searching ? "explore" : "mine_coal";
            case 1 -> searching ? "explore" : "gather_wood";
            case 2 -> "explore";
            default -> "go_home";
        };
    }

    private String buildContext(ServerLevel sl, Player focus, String event) {
        LycaonMemory mem = LycaonMemory.get();
        StringBuilder sb = new StringBuilder();
        sb.append("EVENT: ").append(event).append('\n');
        sb.append("Light level here (0 = pitch dark, 15 = bright daylight): ").append(sl.getMaxLocalRawBrightness(blockPosition())).append('\n');
        sb.append("Health: ").append((int) getHealth()).append("/20\n");
        sb.append("Position: ").append(blockPosition().toShortString()).append('\n');
        sb.append("Home: ").append(mem.hasHome ? mem.home().toShortString() : "none")
          .append(mem.homeBuilt ? " (house is built)" : " (no house yet)").append('\n');
        sb.append("Current task: ").append(task).append('\n');
        sb.append("Affection toward the player (0-100, higher = warmer): ").append(mem.affection).append('\n');
        if (focus != null) {
            sb.append("Nearby player: ").append(focus.getName().getString())
              .append(" (").append((int) distanceTo(focus)).append(" blocks away)\n");
        } else {
            sb.append("Nearby player: nobody\n");
        }
        sb.append("Inventory: ").append(inventorySummary()).append('\n');
        sb.append("Food items: ").append(countFood()).append('\n');
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

    /** Ask the AI (or use a canned line) about something that happened around him. */
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

    private void startTask(ServerLevel sl, String action, UUID playerId, String item) {
        Player p = playerId == null ? null : sl.getPlayerByUUID(playerId);
        switch (action) {
            case "go_home" -> begin(Task.GO_HOME);
            case "gather_wood" -> startHarvest(Kind.LOG, 12, Task.GATHER_WOOD);
            case "mine_stone" -> { if (!startHarvest(Kind.STONE, 16, Task.MINE)) startTask(sl, autoPlan(), playerId, ""); }
            case "mine_coal" -> { if (!startHarvest(Kind.COAL, 6, Task.MINE)) startTask(sl, autoPlan(), playerId, ""); }
            case "mine_iron" -> { if (!startHarvest(Kind.IRON, 6, Task.MINE)) startTask(sl, autoPlan(), playerId, ""); }
            case "craft" -> { if (canCraftSomething()) { begin(Task.CRAFT); craftTicks = 50; } }
            case "build_home" -> startBuild(sl);
            case "hunt" -> begin(Task.HUNT);
            case "collect_items" -> begin(Task.COLLECT);
            case "explore" -> begin(Task.EXPLORE);
            case "follow_player" -> { if (p != null) begin(Task.FOLLOW_PLAYER); }
            case "hug" -> { if (p != null) { pending = "hug"; begin(Task.FOLLOW_PLAYER); } }
            case "give_item" -> { if (p != null) { pending = "give:" + (item == null ? "" : item); begin(Task.FOLLOW_PLAYER); } }
            case "carry_player" -> { if (p != null) startCarry(p); }
            default -> { }
        }
    }

    private void begin(Task t) {
        stopMining();
        task = t;
        taskTicks = 0;
        harvested = 0;
        target = null;
        getNavigation().stop();
        setSprinting(false);
    }

    private void finishTask(String memoryNote) {
        stopMining();
        if (memoryNote != null) LycaonMemory.get().note(memoryNote);
        task = Task.IDLE;
        thinkTicks = 0;
        target = null;
        pending = null;
        getNavigation().stop();
        setSprinting(false);
    }

    private void stopMining() {
        if (target != null && mineProgress > 0 && level() instanceof ServerLevel sl) {
            sl.destroyBlockProgress(getId(), target, -1);
        }
        mineProgress = 0;
    }

    // ------------------------------------------------------------------ simple travel tasks

    private void goHome() {
        LycaonMemory mem = LycaonMemory.get();
        if (!mem.hasHome) mem.setHome(blockPosition());
        BlockPos h = mem.home();
        if (distanceToSqr(Vec3.atCenterOf(h)) < 9 || taskTicks > 20 * 60) { finishTask(null); return; }
        if (tickCount % 20 == 0) getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.0);
    }

    private void explore(ServerLevel sl) {
        if (target == null) {
            double angle = random.nextDouble() * Math.PI * 2, dist = 14 + random.nextInt(16);
            BlockPos flat = BlockPos.containing(getX() + Math.cos(angle) * dist, 0, getZ() + Math.sin(angle) * dist);
            target = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat);
        }
        if (distanceToSqr(Vec3.atCenterOf(target)) < 16 || taskTicks > 20 * 45) {
            finishTask("Went exploring around " + target.toShortString() + ".");
            return;
        }
        if (tickCount % 20 == 0) getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
    }

    private void followPlayer(ServerLevel sl) {
        Player p = sl.getNearestPlayer(this, 48);
        if (p == null || taskTicks > 20 * (pending != null ? 25 : 40)) { finishTask(null); return; }
        if (pending != null && distanceTo(p) < 2.8f) {
            String what = pending;
            pending = null;
            if (what.equals("hug")) hug(sl, p);
            else if (what.startsWith("give:")) giveItem(sl, p, what.substring(5));
            finishTask(null);
            return;
        }
        if (distanceTo(p) > 2.5 && tickCount % 10 == 0) getNavigation().moveTo(p, 1.0);
        getLookControl().setLookAt(p);
    }

    // ------------------------------------------------------------------ harvesting (wood / stone / ores)

    private boolean startHarvest(Kind k, int limit, Task t) {
        boolean needsPick = k != Kind.LOG;
        boolean hasPick = k == Kind.IRON
            ? hasAny(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)
            : has(ItemTags.PICKAXES);
        if (needsPick && !hasPick) return false; // the planner will craft / gather first
        kind = k;
        harvestLimit = limit;
        begin(t);
        return true;
    }

    private boolean matches(BlockState st) {
        return switch (kind) {
            case LOG -> st.is(BlockTags.LOGS);
            case STONE -> st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE);
            case COAL -> st.is(BlockTags.COAL_ORES);
            case IRON -> st.is(BlockTags.IRON_ORES);
        };
    }

    private BlockPos standFor(ServerLevel sl, BlockPos p) {
        if (kind == Kind.LOG) return p;
        for (BlockPos n : new BlockPos[]{p.above(), p.north(), p.south(), p.east(), p.west(), p.below()}) {
            if (sl.getBlockState(n).isAir() && sl.getBlockState(n.above()).isAir() && !sl.getBlockState(n.below()).isAir()) return n;
        }
        return null;
    }

    private boolean findHarvest(ServerLevel sl) {
        BlockPos o = blockPosition(), best = null, bestStand = null;
        double bestScore = Double.MAX_VALUE;
        int ry = kind == Kind.LOG ? 10 : 8;
        for (BlockPos p : BlockPos.betweenClosed(o.offset(-16, -ry, -16), o.offset(16, ry, 16))) {
            if (!matches(sl.getBlockState(p))) continue;
            BlockPos stand = standFor(sl, p);
            if (stand == null) continue;
            double dx = p.getX() - o.getX(), dy = p.getY() - o.getY(), dz = p.getZ() - o.getZ();
            double score = dx * dx + dz * dz + dy * dy * 3; // prefers close, low blocks
            if (score < bestScore) { bestScore = score; best = p.immutable(); bestStand = stand.immutable(); }
        }
        target = best;
        standPos = bestStand;
        return best != null;
    }

    private void harvest(ServerLevel sl) {
        if (harvested >= harvestLimit || taskTicks > 20 * 150) {
            finishTask("Finished " + (kind == Kind.LOG ? "chopping wood" : "mining " + kind.name().toLowerCase()) + " (" + harvested + " blocks).");
            return;
        }
        if (target == null || !matches(sl.getBlockState(target))) {
            stopMining();
            target = null;
            if (!findHarvest(sl)) {
                noTargetUntil = tickCount + 20 * 120;
                finishTask("Could not find any " + kind.name().toLowerCase() + " nearby.");
                return;
            }
        }
        Vec3 c = Vec3.atCenterOf(target);
        if (distanceToSqr(c) > 18) {
            if (tickCount % 10 == 0) getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, 1.0);
            return;
        }
        getNavigation().stop();
        getLookControl().setLookAt(c);
        mineTick(sl);
    }

    /** Breaks the block at vanilla speed for the tool he is holding, with crack animation and tool wear. */
    private void mineTick(ServerLevel sl) {
        BlockState st = sl.getBlockState(target);
        float hardness = st.getDestroySpeed(sl, target);
        if (hardness < 0) { target = null; return; }

        equipBestTool(st);
        ItemStack tool = getMainHandItem();
        boolean correct = !st.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(st);
        float speed = tool.getDestroySpeed(st);
        mineProgress += speed / Math.max(hardness, 0.01f) / (correct ? 30.0f : 100.0f);

        if (taskTicks % 5 == 0) swing(InteractionHand.MAIN_HAND);
        if (taskTicks % 4 == 0) sl.playSound(null, target, st.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6f, 0.75f);
        sl.destroyBlockProgress(getId(), target, Math.min(9, (int) (mineProgress * 10)));

        if (mineProgress >= 1.0f) {
            List<ItemStack> drops = correct
                ? Block.getDrops(st, sl, target, sl.getBlockEntity(target), this, tool)
                : new ArrayList<>();
            sl.destroyBlock(target, false);
            sl.destroyBlockProgress(getId(), target, -1);
            if (!tool.isEmpty()) tool.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
            for (ItemStack d : drops) give(d);
            harvested++;
            mineProgress = 0;
            target = null;
        }
    }

    private void equipBestTool(BlockState st) {
        ItemStack best = ItemStack.EMPTY;
        float bestSpeed = 1.0f;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty() || !s.isDamageableItem()) continue;
            float sp = s.getDestroySpeed(st);
            if (sp > bestSpeed) { best = s; bestSpeed = sp; }
        }
        if (getMainHandItem() != best) setItemSlot(EquipmentSlot.MAINHAND, best);
    }

    private void equipWeapon() {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            int rank = s.is(ItemTags.SWORDS) ? 2 : (s.is(ItemTags.AXES) ? 1 : 0);
            if (rank == 0) continue;
            int score = rank * 10000 + s.getMaxDamage();
            if (score > bestScore) { bestScore = score; best = s; }
        }
        if (getMainHandItem() != best) setItemSlot(EquipmentSlot.MAINHAND, best);
    }

    // ------------------------------------------------------------------ crafting (wood tools -> stone tools)

    private record Recipe(Item result, TagKey<Item> head, int headCount, int sticks, BooleanSupplier needed) {}

    private List<Recipe> recipes() {
        LycaonMemory mem = LycaonMemory.get();
        return List.of(
            new Recipe(Items.WOODEN_PICKAXE, ItemTags.PLANKS, 3, 2, () -> !has(ItemTags.PICKAXES)),
            new Recipe(Items.WOODEN_AXE, ItemTags.PLANKS, 3, 2, () -> !has(ItemTags.AXES)),
            new Recipe(Items.WOODEN_SWORD, ItemTags.PLANKS, 2, 1, () -> !has(ItemTags.SWORDS)),
            new Recipe(Items.STONE_PICKAXE, ItemTags.STONE_TOOL_MATERIALS, 3, 2,
                () -> has(ItemTags.PICKAXES) && !hasAny(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)),
            new Recipe(Items.STONE_AXE, ItemTags.STONE_TOOL_MATERIALS, 3, 2,
                () -> has(ItemTags.AXES) && !hasAny(Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE)),
            new Recipe(Items.STONE_SWORD, ItemTags.STONE_TOOL_MATERIALS, 2, 1,
                () -> has(ItemTags.SWORDS) && !hasAny(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)),
            new Recipe(Items.CRAFTING_TABLE, ItemTags.PLANKS, 4, 0,
                () -> !mem.homeBuilt && count(Items.CRAFTING_TABLE) == 0
                      && hasAny(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE))
        );
    }

    private int planksNeeded(Recipe r) {
        int sticksToMake = Math.max(0, r.sticks() - count(Items.STICK));
        int forSticks = (sticksToMake + 3) / 4 * 2;
        return forSticks + (r.head() == ItemTags.PLANKS ? r.headCount() : 0);
    }

    private boolean affordable(Recipe r) {
        int planksAvailable = countTag(ItemTags.PLANKS) + countTag(ItemTags.LOGS) * 4;
        if (planksAvailable < planksNeeded(r)) return false;
        return r.head() == ItemTags.PLANKS || countTag(r.head()) >= r.headCount();
    }

    private boolean canCraftSomething() {
        for (Recipe r : recipes()) if (r.needed().getAsBoolean() && affordable(r)) return true;
        return false;
    }

    private boolean craftOne() {
        for (Recipe r : recipes()) {
            if (!r.needed().getAsBoolean() || !affordable(r)) continue;
            ensurePlanks(planksNeeded(r));
            while (count(Items.STICK) < r.sticks()) {
                if (!removeTag(ItemTags.PLANKS, 2)) return false;
                give(new ItemStack(Items.STICK, 4));
            }
            if (!removeTag(r.head(), r.headCount())) return false;
            give(new ItemStack(r.result()));
            LycaonMemory.get().note("Crafted a " + BuiltInRegistries.ITEM.getKey(r.result()).getPath() + ".");
            return true;
        }
        return false;
    }

    private void craftTick() {
        if (tickCount % 10 == 0) swing(InteractionHand.MAIN_HAND);
        if (--craftTicks > 0) return;
        int made = 0;
        while (made < 6 && craftOne()) made++;
        finishTask(null);
    }

    private void ensurePlanks(int n) {
        while (countTag(ItemTags.PLANKS) < n && countTag(ItemTags.LOGS) > 0) {
            removeTag(ItemTags.LOGS, 1);
            give(new ItemStack(Items.OAK_PLANKS, 4));
        }
    }

    // ------------------------------------------------------------------ building a home

    private void startBuild(ServerLevel sl) {
        ensurePlanks(PLANKS_NEEDED);
        if (countTag(ItemTags.PLANKS) < PLANKS_NEEDED) {
            LycaonMemory.get().note("I need more wood before I can build a home.");
            return;
        }
        planOrigin = findBuildSite(sl);
        plan = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    boolean wall = x == 0 || x == 3 || z == 0 || z == 3;
                    boolean door = z == 0 && x == 1 && y < 2;
                    if (wall && !door) plan.add(planOrigin.offset(x, y, z));
                }
            }
        }
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) plan.add(planOrigin.offset(x, 3, z));
        planIndex = 0;
        placeCooldown = 0;
        begin(Task.BUILD_HOME);
    }

    private int surfaceY(ServerLevel sl, int x, int z) {
        return sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).getY();
    }

    private BlockPos findBuildSite(ServerLevel sl) {
        LycaonMemory mem = LycaonMemory.get();
        BlockPos base = mem.hasHome ? mem.home() : blockPosition();
        BlockPos bestPos = null;
        int bestDiff = Integer.MAX_VALUE;
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30);
            int cx = base.getX() + (int) Math.round(Math.cos(a) * 8), cz = base.getZ() + (int) Math.round(Math.sin(a) * 8);
            int[] ys = {surfaceY(sl, cx, cz), surfaceY(sl, cx + 3, cz), surfaceY(sl, cx, cz + 3), surfaceY(sl, cx + 3, cz + 3)};
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int y : ys) { min = Math.min(min, y); max = Math.max(max, y); }
            boolean dry = sl.getBlockState(new BlockPos(cx, min - 1, cz)).getFluidState().isEmpty()
                       && sl.getBlockState(new BlockPos(cx + 3, min - 1, cz + 3)).getFluidState().isEmpty();
            if (dry && max - min < bestDiff) { bestDiff = max - min; bestPos = new BlockPos(cx, max, cz); }
        }
        return bestPos != null ? bestPos : new BlockPos(base.getX() + 8, surfaceY(sl, base.getX() + 8, base.getZ()), base.getZ());
    }

    private void buildHome(ServerLevel sl) {
        if (planIndex >= plan.size()) { finishBuild(sl); return; }
        if (taskTicks > 20 * 300) { finishTask("I gave up on building for now."); return; }

        BlockPos p = plan.get(planIndex);
        if (sl.getBlockState(p).is(Blocks.OAK_PLANKS)) { planIndex++; return; }

        Vec3 c = Vec3.atCenterOf(p);
        if (distanceToSqr(c) > 20) {
            if (tickCount % 10 == 0) getNavigation().moveTo(p.getX() + 0.5, planOrigin.getY(), p.getZ() + 0.5, 1.0);
            return;
        }
        getLookControl().setLookAt(c);
        if (placeCooldown > 0) { placeCooldown--; return; }
        if (getBoundingBox().intersects(new AABB(p))) { plan.add(plan.remove(planIndex)); return; } // standing in the way
        if (!removeTag(ItemTags.PLANKS, 1)) { finishTask("I ran out of planks while building."); return; }

        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        sl.setBlock(p, planks, 3);
        sl.playSound(null, p, planks.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0f, 0.9f);
        swing(InteractionHand.MAIN_HAND);
        planIndex++;
        placeCooldown = 10;
    }

    private void finishBuild(ServerLevel sl) {
        LycaonMemory mem = LycaonMemory.get();
        mem.homeBuilt = true;
        mem.setHome(planOrigin.offset(1, 0, 1));
        if (count(Items.CRAFTING_TABLE) > 0) {
            removeItem(Items.CRAFTING_TABLE, 1);
            sl.setBlock(planOrigin.offset(2, 0, 2), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        }
        finishTask("Finished building my own house at " + planOrigin.toShortString() + ".");
        Player p = sl.getNearestPlayer(this, 48);
        ask(sl, p, false, "EVENT: You just finished building your own little wooden house. Say something about it.",
            "A modest dwelling, but a home nonetheless.");
    }

    // ------------------------------------------------------------------ food, hunting, items

    private int countFood() {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.get(DataComponents.FOOD) != null) n += s.getCount();
        }
        return n;
    }

    private void startEating() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.get(DataComponents.FOOD) != null) {
                setItemSlot(EquipmentSlot.MAINHAND, s);
                eatingTicks = 32;
                getNavigation().stop();
                return;
            }
        }
    }

    private void tickEating() {
        eatingTicks--;
        if (eatingTicks % 5 == 0) playSound(SoundEvents.GENERIC_EAT, 0.6f, 1.0f);
        if (eatingTicks <= 0) {
            ItemStack s = getMainHandItem();
            FoodProperties food = s.get(DataComponents.FOOD);
            if (food != null) {
                heal(food.nutrition());
                s.shrink(1);
            }
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    private void hunt(ServerLevel sl) {
        if (countFood() >= 4 || taskTicks > 20 * 90) { finishTask("Hunted for food."); return; }
        if (getTarget() != null) return;
        Animal prey = sl.getEntitiesOfClass(Animal.class, getBoundingBox().inflate(24),
                a -> a.isAlive() && (a instanceof Cow || a instanceof Pig || a instanceof Sheep || a instanceof Chicken))
            .stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(this))).orElse(null);
        if (prey == null) { noTargetUntil = tickCount + 20 * 120; finishTask("Found no animals to hunt."); return; }
        equipWeapon();
        setTarget(prey);
    }

    private void collectNearbyItems(ServerLevel sl) {
        for (ItemEntity ie : sl.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(1.2, 0.5, 1.2))) {
            if (!ie.isAlive() || ie.hasPickUpDelay() || givenItems.contains(ie.getUUID())) continue;
            ItemStack rest = inventory.addItem(ie.getItem().copy());
            if (rest.isEmpty()) ie.discard(); else ie.setItem(rest);
            playSound(SoundEvents.ITEM_PICKUP, 0.3f, 1.0f);
        }
    }

    private ItemEntity nearestItem(ServerLevel sl, double range) {
        return sl.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(range),
                ie -> ie.isAlive() && !givenItems.contains(ie.getUUID()))
            .stream().min(Comparator.comparingDouble(ie -> ie.distanceToSqr(this))).orElse(null);
    }

    private void walkToNearbyItem(ServerLevel sl, double range) {
        ItemEntity ie = nearestItem(sl, range);
        if (ie != null) getNavigation().moveTo(ie, 1.0);
    }

    private void collectTask(ServerLevel sl) {
        ItemEntity ie = nearestItem(sl, 24);
        if (ie == null || taskTicks > 20 * 20) { finishTask(null); return; }
        if (tickCount % 10 == 0) getNavigation().moveTo(ie, 1.0);
    }

    // ------------------------------------------------------------------ fleeing

    private void startFlee() {
        setTarget(null);
        begin(Task.FLEE);
        LycaonMemory.get().note("I had to run from a fight.");
    }

    private void flee() {
        setTarget(null);
        setSprinting(true);
        if (tickCount % 10 == 0) {
            LivingEntity attacker = getLastHurtByMob();
            if (attacker != null) {
                Vec3 away = position().subtract(attacker.position()).normalize().scale(14);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.35);
            } else if (LycaonMemory.get().hasHome) {
                BlockPos h = LycaonMemory.get().home();
                getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 1.35);
            }
        }
        if (taskTicks > 20 * 8) finishTask(null);
    }

    // ------------------------------------------------------------------ carrying (only when asked)

    private void startCarry(Player p) {
        if (p.startRiding(this)) {
            carriedPlayer = p.getUUID();
            entityData.set(CARRYING, true);
            begin(Task.CARRY);
        }
    }

    private void carry(ServerLevel sl) {
        Player p = carriedPlayer == null ? null : sl.getPlayerByUUID(carriedPlayer);
        if (p == null || p.getVehicle() != this || taskTicks > 20 * 10) {
            if (p != null && p.getVehicle() == this) p.stopRiding();
            carriedPlayer = null;
            entityData.set(CARRYING, false);
            finishTask(null);
            return;
        }
        LycaonMemory mem = LycaonMemory.get();
        if (mem.hasHome && tickCount % 20 == 0) {
            BlockPos h = mem.home();
            getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 0.9);
        }
    }

    // ------------------------------------------------------------------ hug / give (only when asked)

    private void hug(ServerLevel sl, Player player) {
        getLookControl().setLookAt(player);
        swing(InteractionHand.MAIN_HAND);
        sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.0, getZ(), 7, 0.4, 0.3, 0.4, 0.02);
        playSound(SoundEvents.VILLAGER_YES, 1.0f, 1.1f);
        player.heal(2.0f);
        LycaonMemory mem = LycaonMemory.get();
        mem.affection = Math.min(100, mem.affection + 3);
        mem.note("Shared a hug with " + player.getName().getString() + ".");
    }

    private void giveItem(ServerLevel sl, Player player, String itemId) {
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            if (slot == -1) slot = i;
            if (itemId != null && !itemId.isBlank()
                && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals(itemId.replace("minecraft:", ""))) { slot = i; break; }
        }
        if (slot == -1) { speak(sl, "I am afraid my pockets are empty at the moment."); return; }
        ItemStack stack = inventory.removeItem(slot, 8);
        ItemEntity drop = new ItemEntity(sl, getX(), getEyeY() - 0.3, getZ(), stack);
        Vec3 dir = player.position().subtract(position()).normalize().scale(0.25);
        drop.setDeltaMovement(dir.x, 0.1, dir.z);
        drop.setPickUpDelay(20);
        givenItems.add(drop.getUUID());
        if (givenItems.size() > 50) givenItems.clear();
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

    // ------------------------------------------------------------------ inventory helpers

    private void give(ItemStack s) {
        ItemStack rest = inventory.addItem(s);
        if (!rest.isEmpty() && level() instanceof ServerLevel sl) {
            sl.addFreshEntity(new ItemEntity(sl, getX(), getY() + 0.5, getZ(), rest));
        }
    }

    private int count(Item item) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private int countTag(TagKey<Item> tag) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.is(tag)) n += s.getCount();
        }
        return n;
    }

    private boolean has(TagKey<Item> tag) { return countTag(tag) > 0; }

    private boolean hasAny(Item... items) {
        for (Item it : items) if (count(it) > 0) return true;
        return false;
    }

    private boolean removeTag(TagKey<Item> tag, int n) {
        if (countTag(tag) < n) return false;
        for (int i = 0; i < inventory.getContainerSize() && n > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && s.is(tag)) {
                int take = Math.min(n, s.getCount());
                s.shrink(take);
                n -= take;
            }
        }
        return true;
    }

    private void removeItem(Item item, int n) {
        for (int i = 0; i < inventory.getContainerSize() && n > 0; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                int take = Math.min(n, s.getCount());
                s.shrink(take);
                n -= take;
            }
        }
    }

    // ------------------------------------------------------------------ speech

    private void speak(ServerLevel sl, String text) {
        Component msg = Component.literal("<Von Lycaon> ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : sl.players()) {
            if (p.distanceTo(this) < 64) p.sendSystemMessage(msg);
        }
    }

    private void systemNote(ServerLevel sl, String text) {
        Component msg = Component.literal("[Lycaon] " + text).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : sl.players()) {
            if (p.distanceTo(this) < 64) p.sendSystemMessage(msg);
        }
    }
}
