package hoefarm.mixin;

import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Взято из Right Click Harvest 4.7.1 (MIT, JamCoreModding).
 * <p>
 * {@code getAgeProperty()} нужен, чтобы пересаживать через {@code state.setValue(AGE, 0)}, а не через
 * {@code getStateForAge(0)}: второй сбрасывает прочие свойства блока (у модовых культур вроде томатов
 * Farmers Delight есть {@code ropelogged}), и пересаженная культура теряет их.
 */
@Mixin(CropBlock.class)
public interface CropBlockAccessor {
	@Invoker("getAgeProperty")
	IntegerProperty invokeGetAgeProperty();

	/** Семечко этой культуры (protected в ванили). */
	@Invoker("getBaseSeedId")
	ItemLike invokeGetBaseSeedId();
}
