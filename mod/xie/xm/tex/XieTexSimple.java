package mod.xie.xm.tex;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieTexSimple implements XieTex {

	private Icon texture;
	
	public XieTexSimple(IconRegister register, String string) {
		texture = register.func_94245_a(string);
	}

	@Override
	public Icon getTexFromSideAndMeta(int i, int j) {
		return texture;
	}

	@Override
	public Icon getIconFromDamage(int par1) {
		return texture;
	}

	@Override
	public XieTexType getType() {
		return XieTexType.SIMPLE;
	}

}
