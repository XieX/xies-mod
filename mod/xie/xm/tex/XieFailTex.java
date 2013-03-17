package mod.xie.xm.tex;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieFailTex implements XieTex {

	private static XieFailTex instance;
	
	public static XieFailTex get() {
		if (instance == null) instance = new XieFailTex();
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

	public Icon failTex(IconRegister register) {
		return null;
	}

}
