package mod.xie.xm.tex;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieFailTex extends XieTex {

	public XieFailTex(IconRegister register) {
		super(register);
	}

	private static XieFailTex instance;
	
	public static XieFailTex getInstance(IconRegister register) {
		if (instance == null) instance = new XieFailTex(register);
		return instance;
	}
	
	@Override
	public Icon getTexFromSideAndMeta(int i, int j) {
		return null;
	}

	@Override
	public Icon getIconFromDamage(int par1) {
		return null;
	}

	@Override
	public XieTexType getType() {
		return XieTexType.FAIL;
	}
	
	public static Icon getFailTex(IconRegister register) {
		// is it OK to just return null? do we even need this class?
		// TODO leaving it in just in case it is needed one day
		return null;
	}

}
