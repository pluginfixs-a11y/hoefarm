package hoefarm;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Самопроверка логики без клиента: строит поля в 1000 блоках от спавна, дёргает
 * {@link FarmAction#onUseBlock} за {@code FakePlayer} и пишет результат в лог.
 * Включается только системным свойством {@code -Dhoefarm.selftest=1}, в обычном запуске не работает.
 */
public final class SelfTest {
	private static final BlockPos ORIGIN = new BlockPos(1000, 200, 1000);
	private static final BlockState WHEAT_RIPE = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
	private static final BlockState TALL_GRASS_LOWER =
			Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
	private static final BlockState TALL_GRASS_UPPER =
			Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);

	private SelfTest() {
	}

	public static void run(MinecraftServer server) {
		try {
			ServerLevel level = server.overworld();
			level.setChunkForced(ORIGIN.getX() >> 4, ORIGIN.getZ() >> 4, true);
			level.getChunk(ORIGIN.getX() >> 4, ORIGIN.getZ() >> 4);

			testIron(level);
			testNetheriteTerrace(level);
			testTillAndPlant(level);
			testTillUnderGrass(level);
			testEventOrder(level);
		} catch (Throwable t) {
			HoeFarm.LOGGER.error("SELFTEST упал", t);
		}
	}

	/** Железо: 3x3x1 — 9 зрелых культур собраны и пересажены. */
	private static void testIron(ServerLevel level) {
		BlockPos c = ORIGIN.offset(0, 0, 0);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				set(level, c.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState());
				set(level, c.offset(dx, 1, dz), dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 ? WHEAT_RIPE : Blocks.AIR.defaultBlockState());
			}
		}

		FakePlayer player = player(level, c.offset(0, 1, 0), Items.IRON_HOE, 64);
		InteractionResult result = click(level, player, c.offset(0, 1, 0));

		int replanted = 0;
		int empty = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState s = level.getBlockState(c.offset(dx, 1, dz));
				if (s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 0) replanted++;
				if (s.isAir()) empty++;
			}
		}
		HoeFarm.LOGGER.info("SELFTEST iron: result={} пересажено={}/9 пусто={} прочность={} инвентарь={}",
				result, replanted, empty, player.getMainHandItem().getDamageValue(), inventory(player));
	}

	/** Незерит: 5x5 + терраса на +1 — высота работает. */
	private static void testNetheriteTerrace(ServerLevel level) {
		BlockPos c = ORIGIN.offset(20, 0, 0);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				set(level, c.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState());
				set(level, c.offset(dx, 1, dz), WHEAT_RIPE);
			}
		}
		// терраса: на dx=2 грядка на y+1 и пшеница на y+2
		for (int dz = -2; dz <= 2; dz++) {
			set(level, c.offset(2, 1, dz), Blocks.FARMLAND.defaultBlockState());
			set(level, c.offset(2, 2, dz), WHEAT_RIPE);
		}

		FakePlayer player = player(level, c.offset(0, 1, 0), Items.NETHERITE_HOE, 64);
		InteractionResult result = click(level, player, c.offset(0, 1, 0));

		int lower = 0;
		int terrace = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx == 2) continue;
				BlockState s = level.getBlockState(c.offset(dx, 1, dz));
				if (s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 0) lower++;
			}
		}
		for (int dz = -2; dz <= 2; dz++) {
			BlockState s = level.getBlockState(c.offset(2, 2, dz));
			if (s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 0) terrace++;
		}
		HoeFarm.LOGGER.info("SELFTEST netherite: result={} нижний слой={}/20 терраса={}/5 прочность={} инвентарь={}",
				result, lower, terrace, player.getMainHandItem().getDamageValue(), inventory(player));
	}

	/** Вспашка земли и посадка на пустую грядку. */
	private static void testTillAndPlant(ServerLevel level) {
		BlockPos c = ORIGIN.offset(40, 0, 0);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, c.offset(dx, 0, dz), Blocks.DIRT.defaultBlockState());
				set(level, c.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
			}
		}

		FakePlayer player = player(level, c.offset(0, 1, 0), Items.IRON_HOE, 64);
		InteractionResult result = click(level, player, c.offset(0, 0, 0));

		int farmland = 0;
		int planted = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (level.getBlockState(c.offset(dx, 0, dz)).getBlock() instanceof FarmlandBlock) farmland++;
				if (level.getBlockState(c.offset(dx, 1, dz)).is(Blocks.WHEAT)) planted++;
			}
		}
		HoeFarm.LOGGER.info("SELFTEST till: result={} грядок={}/9 посажено={}/9 инвентарь={}",
				result, farmland, planted, inventory(player));
	}

	/** Заросшее поле: трава и высокая трава сносятся, клик — по самому растению (верхушке высокой травы). */
	private static void testTillUnderGrass(ServerLevel level) {
		BlockPos c = ORIGIN.offset(60, 0, 0);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, c.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState());
				if (dz == 0) {
					set(level, c.offset(dx, 1, dz), TALL_GRASS_LOWER);
					set(level, c.offset(dx, 2, dz), TALL_GRASS_UPPER);
				} else {
					set(level, c.offset(dx, 1, dz), Blocks.SHORT_GRASS.defaultBlockState());
				}
			}
		}

		FakePlayer player = player(level, c.offset(0, 1, 0), Items.IRON_HOE, 64);
		InteractionResult result = click(level, player, c.offset(0, 2, 0));

		int farmland = 0;
		int planted = 0;
		int leftovers = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (level.getBlockState(c.offset(dx, 0, dz)).getBlock() instanceof FarmlandBlock) farmland++;
				if (level.getBlockState(c.offset(dx, 1, dz)).is(Blocks.WHEAT)) planted++;
				for (int dy = 1; dy <= 2; dy++) {
					BlockState s = level.getBlockState(c.offset(dx, dy, dz));
					if (s.is(Blocks.SHORT_GRASS) || s.is(Blocks.TALL_GRASS)) leftovers++;
				}
			}
		}
		HoeFarm.LOGGER.info("SELFTEST grass-till: result={} грядок={}/9 посажено={}/9 травы-осталось={} прочность={} инвентарь={}",
				result, farmland, planted, leftovers, player.getMainHandItem().getDamageValue(), inventory(player));
	}

	/** Кто первый в цепочке UseBlockCallback: мы или Right Click Harvest. 9 пересаженных = мы, 1 = они. */
	private static void testEventOrder(ServerLevel level) {
		BlockPos c = ORIGIN.offset(40, 0, 40);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				set(level, c.offset(dx, 0, dz), Blocks.FARMLAND.defaultBlockState());
				set(level, c.offset(dx, 1, dz), WHEAT_RIPE);
			}
		}
		FakePlayer player = player(level, c.offset(0, 1, 0), Items.IRON_HOE, 64);
		BlockPos pos = c.offset(0, 1, 0);
		InteractionResult result = net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT
				.invoker().interact(player, level, InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
		int replanted = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockState s = level.getBlockState(c.offset(dx, 1, dz));
				if (s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 0) replanted++;
			}
		}
		HoeFarm.LOGGER.info("SELFTEST event-order: result={} пересажено={}/9 (9 = hoefarm первый, 1 = rightclickharvest первый)",
				result, replanted);
	}

	private static FakePlayer player(ServerLevel level, BlockPos pos, net.minecraft.world.item.Item hoe, int seeds) {		FakePlayer player = FakePlayer.get(level);
		player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		player.getInventory().clearContent();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(hoe));
		player.getInventory().add(new ItemStack(Items.WHEAT_SEEDS, seeds));
		return player;
	}

	private static InteractionResult click(ServerLevel level, FakePlayer player, BlockPos pos) {
		return FarmAction.onUseBlock(player, level, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
	}

	private static String inventory(FakePlayer player) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (!s.isEmpty()) {
				sb.append(s.getCount()).append('x').append(s.getItem()).append(' ');
			}
		}
		return sb.toString().trim();
	}

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 3);
	}
}
