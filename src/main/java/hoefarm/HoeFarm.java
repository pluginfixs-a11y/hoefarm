package hoefarm;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Мотыга обрабатывает поле целиком: собирает зрелое, пересаживает, пашет землю и сажает.
 * <p>
 * Логика сбора/пересадки взята из Right Click Harvest 4.7.1 (MIT, JamCoreModding) —
 * см. {@link FarmAction} и {@link hoefarm.mixin.CropBlockAccessor}.
 */
public class HoeFarm implements ModInitializer {
	public static final String MOD_ID = "hoefarm";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config;

	public static ModConfig config() {
		if (config == null) {
			config = ModConfig.load();
		}
		return config;
	}

	@Override
	public void onInitialize() {
		ModConfig c = config();
		LOGGER.info("HoeFarm: тиры {}", c.tiers);
		UseBlockCallback.EVENT.register(FarmAction::onUseBlock);

		if (System.getProperty("hoefarm.selftest") != null) {
			ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
			LOGGER.info("HoeFarm: включена самопроверка (-Dhoefarm.selftest)");
		}
	}

	static Map<String, int[]> defaultTiers() {
		return ModConfig.defaultTiers();
	}
}
