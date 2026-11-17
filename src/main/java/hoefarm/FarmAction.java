package hoefarm;

import hoefarm.mixin.CropBlockAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обработка поля по ПКМ мотыгой.
 * <p>
 * Сбор, пересадка и подбор дропа — адаптация Right Click Harvest 4.7.1 (MIT, JamCoreModding):
 * {@code isMature}, {@code getReplantState} через {@code getAgeProperty}, {@code dropStacks} с вычетом
 * одного семечка на пересадку. Добавлено: объём по высоте, вспашка, посадка на пустую грядку,
 * дроп прямо в инвентарь и цена в прочности за клик, а не за блок.
 */
public final class FarmAction {
	/** Семечко → блок культуры. Мелон/тыква не в списке: их стебли наш сбор всё равно не трогает. */
	private static final Map<Item, Block> SEEDS = new HashMap<>();

	static {
		SEEDS.put(Items.WHEAT_SEEDS, Blocks.WHEAT);
		SEEDS.put(Items.CARROT, Blocks.CARROTS);
		SEEDS.put(Items.POTATO, Blocks.POTATOES);
		SEEDS.put(Items.BEETROOT_SEEDS, Blocks.BEETROOTS);
		SEEDS.put(Items.TORCHFLOWER_SEEDS, Blocks.TORCHFLOWER_CROP);
		SEEDS.put(Items.NETHER_WART, Blocks.NETHER_WART);
	}

	private FarmAction() {
	}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		ModConfig cfg = HoeFarm.config();
		if (cfg.sneak_does_vanilla && player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}

		ItemStack tool = player.getItemInHand(hand);
		boolean isHoe = tool.is(ItemTags.HOES);
		if (!isHoe && !cfg.bare_hand_harvest) {
			return InteractionResult.PASS;
		}

		BlockPos origin = hit.getBlockPos();
		if (!server.hasChunkAt(origin)) {
			return InteractionResult.PASS;
		}

		BlockState originState = server.getBlockState(origin);

		// Клик по траве, папоротнику или снегу на земле: спускаемся к блоку под растением
		// (верхушка высокой травы — два шага вниз), иначе вспахать заросшее поле нельзя.
		for (int i = 0; i < 2 && isClearablePlant(originState); i++) {
			origin = origin.below();
			originState = server.getBlockState(origin);
		}

		if (cfg.isBlacklisted(originState.getBlock()) || !isFarmTarget(originState)) {
			return InteractionResult.PASS;
		}

		int[] area;
		if (isHoe) {
			area = cfg.tierFor(tool);
			if (area == null) {
				return InteractionResult.PASS;
			}
		} else {
			area = new int[]{0, 0, 0};
		}

		Item seed = seedForClick(originState, player);
		Counts counts = new Counts();
		run(server, player, tool, origin, area, cfg, seed, counts);

		if (counts.changed == 0) {
			return InteractionResult.PASS;
		}

		int perPoint = Math.max(1, cfg.durability_per_blocks);
		tool.hurtAndBreak(Math.max(1, (counts.changed + perPoint - 1) / perPoint), player, EquipmentSlot.MAINHAND);
		player.causeFoodExhaustion((float) (cfg.hunger_per_block * counts.changed));
		player.awardStat(Stats.ITEM_USED.get(tool.getItem()));

		if (counts.tilled > 0) {
			server.playSound(null, origin, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		if (counts.harvested > 0) {
			server.playSound(null, origin, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		}

		return InteractionResult.SUCCESS;
	}

	private static void run(ServerLevel level, Player player, ItemStack tool, BlockPos origin, int[] area,
	                        ModConfig cfg, Item seed, Counts counts) {
		int radius = area[0];
		int up = area[1];
		int down = area[2];

		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				// сверху вниз: сначала собираем культуру, потом смотрим на землю под ней
				for (int dy = up; dy >= -down; dy--) {
					BlockPos pos = origin.offset(dx, dy, dz);
					if (!level.hasChunkAt(pos) || !level.mayInteract(player, pos)) {
						continue;
					}

					BlockState state = level.getBlockState(pos);
					Block block = state.getBlock();

					// Блэклист проверяется для каждого блока поля, а не только для того,
					// по которому кликнули: иначе культура из блэклиста рядом с пшеницей
					// всё равно собиралась бы.
					if (cfg.isBlacklisted(block)) {
						continue;
					}

					if (block instanceof CropBlock crop) {
						if (cfg.harvest && crop.isMaxAge(state) && harvest(level, pos, state, player, tool, cfg)) {
							counts.harvested++;
							counts.changed++;
						}
						continue;
					}

					if (block instanceof NetherWartBlock) {
						if (cfg.harvest && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE
								&& harvest(level, pos, state, player, tool, cfg)) {
							counts.harvested++;
							counts.changed++;
						}
						continue;
					}

					if (cfg.till && isTillable(state) && clearAbove(level, pos, player)) {
						till(level, pos, state);
						counts.tilled++;
						counts.changed++;
						if (cfg.plant && seed != null && plantAt(level, pos.above(), seed, player)) {
							counts.changed++;
						}
						continue;
					}

					if (cfg.plant && block instanceof FarmlandBlock && seed != null
							&& level.getBlockState(pos.above()).isAir()
							&& plantAt(level, pos.above(), seed, player)) {
						counts.changed++;
					}
				}
			}
		}
	}

	private static boolean harvest(ServerLevel level, BlockPos pos, BlockState state, Player player,
	                               ItemStack tool, ModConfig cfg) {
		Item seed = seedOf(state);
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool);

		boolean replanted = false;
		if (cfg.replant && seed != null) {
			for (ItemStack drop : drops) {
				if (!replanted && drop.is(seed)) {
					drop.shrink(1);
					replanted = true;
				}
			}
			if (!replanted) {
				replanted = takeItem(player, seed);
			}
		}

		if (replanted) {
			level.setBlock(pos, replantState(state), Block.UPDATE_CLIENTS);
		} else {
			level.removeBlock(pos, false);
		}

		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				give(player, drop);
			}
		}

		state.spawnAfterBreak(level, pos, tool, true);
		player.awardStat(Stats.BLOCK_MINED.get(state.getBlock()));
		return true;
	}

	private static BlockState replantState(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return state.setValue(((CropBlockAccessor) crop).invokeGetAgeProperty(), 0);
		}
		if (state.getBlock() instanceof NetherWartBlock) {
			return state.setValue(NetherWartBlock.AGE, 0);
		}
		return state;
	}

	private static Item seedOf(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			ItemLike seed = ((CropBlockAccessor) crop).invokeGetBaseSeedId();
			return seed == null ? null : seed.asItem();
		}
		if (state.getBlock() instanceof NetherWartBlock) {
			return Items.NETHER_WART;
		}
		return null;
	}

	/** Что сажаем в пустые клетки: культура, по которой кликнули, иначе первое семечко из инвентаря. */
	private static Item seedForClick(BlockState origin, Player player) {
		Item seed = seedOf(origin);
		if (seed != null) {
			return seed;
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && SEEDS.containsKey(stack.getItem())) {
				return stack.getItem();
			}
		}
		return null;
	}

	private static boolean plantAt(ServerLevel level, BlockPos pos, Item seed, Player player) {
		Block crop = SEEDS.get(seed);
		if (crop == null) {
			return false;
		}
		BlockState state = crop.defaultBlockState();
		if (!state.canSurvive(level, pos) || !takeItem(player, seed)) {
			return false;
		}
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		return true;
	}

	private static boolean takeItem(Player player, Item item) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static boolean isTillable(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.DIRT_PATH
				|| block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT;
	}

	/**
	 * Декоративная мелочь, которую игра сама считает заменяемой: короткая и высокая трава, папоротники,
	 * снежный слой, огонь. Культуры, саженцы и цветы сюда не попадают — их не трогаем.
	 */
	private static boolean isClearablePlant(BlockState state) {
		return !state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	/** Над землёй пусто или стоит трава — траву сносим с дропом и пашем. */
	private static boolean clearAbove(ServerLevel level, BlockPos pos, Player player) {
		BlockPos above = pos.above();
		BlockState aboveState = level.getBlockState(above);
		if (aboveState.isAir()) {
			return true;
		}
		if (!isClearablePlant(aboveState)) {
			return false;
		}
		level.destroyBlock(above, true, player);
		return true;
	}

	/** Ванильная карта HoeItem.TILLABLES: трава/дёрн/грубая земля → грядка, грубая/корневая → земля. */
	private static void till(ServerLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT) {
			level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
			if (block == Blocks.ROOTED_DIRT) {
				Block.popResource(level, pos, new ItemStack(Items.HANGING_ROOTS));
			}
		} else {
			level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
		}
	}

	private static boolean isFarmTarget(BlockState state) {
		Block block = state.getBlock();
		return block instanceof CropBlock || block instanceof NetherWartBlock
				|| block instanceof FarmlandBlock || isTillable(state);
	}

	private static final class Counts {
		int changed;
		int harvested;
		int tilled;
	}
}
