package mod.xie.xm.tex;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.item.Item;
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
	public XieTexType getType() {
		return XieTexType.FAIL;
	}

	public XieTex setTextureBase(Block textureBase) {
		if (textureBase!=null) base = new XieTexLink(iconRegister, textureBase);
		return base;
	}

	public XieTex setTextureBase(Item textureBase) {
		if (textureBase!=null) base = new XieTexLink(iconRegister, textureBase);
		return base;
	}
	
	public int getMaxMeta() {
		for (int i = 0; i<16; i++) {
			Icon icon = base.getIconFromDamage(i);
			if (icon==null) {
				return i-1;
			}
		}
		return super.getMaxMeta();
	}
}
