package hoefarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки в <code>config/hoefarm.json</code>. Читаются один раз при старте сервера.
 * <p>
 * {@code tiers}: предмет → <code>[радиус по XZ, слоёв вверх, слоёв вниз]</code>.
 * Радиус 1 = квадрат 3x3, радиус 2 = 5x5. Предмета нет в списке — мотыга работает как в ванили.
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("hoefarm.json");

	public Map<String, int[]> tiers = defaultTiers();

	public boolean harvest = true;
	public boolean replant = true;
	public boolean till = true;
	public boolean plant = true;

	/** Сбор урожая пустой рукой (как в Right Click Harvest). По умолчанию выключено — только мотыги. */
	public boolean bare_hand_harvest = false;

	/** Shift + ПКМ = ванильное поведение (один блок). */
	public boolean sneak_does_vanilla = true;

	/** Одно очко прочности на столько обработанных блоков (минимум 1 за клик). */
	public int durability_per_blocks = 8;

	/** Голод за обработанный блок (ванильный сбор блока — 0.005). */
	public double hunger_per_block = 0.005;

	/** Блоки, которые мод не трогает вообще. */
	public List<String> blacklist = new ArrayList<>(List.of(
			"minecraft:pitcher_crop"
	));

	static Map<String, int[]> defaultTiers() {
		Map<String, int[]> map = new LinkedHashMap<>();
		map.put("minecraft:iron_hoe", new int[]{1, 0, 0});        // 3x3x1
		map.put("minecraft:diamond_hoe", new int[]{1, 1, 1});     // 3x3x3
		map.put("minecraft:netherite_hoe", new int[]{2, 1, 1});   // 5x5x3
		return map;
	}

	/** null — мотыга не из списка, отдаём клик ванили. */
	public int[] tierFor(ItemStack stack) {
		if (tiers == null || tiers.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id == null) {
			return null;
		}
		int[] area = tiers.get(id.toString());
		if (area == null || area.length < 3) {
			return null;
		}
		return area;
	}

	public boolean isBlacklisted(net.minecraft.world.level.block.Block block) {
		if (blacklist == null || blacklist.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return id != null && blacklist.contains(id.toString());
	}

	public static ModConfig load() {
		ModConfig config = new ModConfig();
		if (Files.exists(PATH)) {
			try {
				ModConfig loaded = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), ModConfig.class);
				if (loaded != null) {
					config = loaded;
					if (config.tiers == null) {
						config.tiers = defaultTiers();
					}
					if (config.blacklist == null) {
						config.blacklist = new ArrayList<>();
					}
				}
			} catch (Exception e) {
				HoeFarm.LOGGER.error("Не смог прочитать {}, беру значения по умолчанию", PATH, e);
			}
		}
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			HoeFarm.LOGGER.error("Не смог записать {}", PATH, e);
		}
	}
}
