package mod.xie.xm.tex;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieTexSideVariable implements XieTex {

	private Icon [] textures;
	private Icon defaultTexture;
	
	public XieTexSideVariable(IconRegister register, String[] textureNames) {
		textures = new Icon[textureNames.length];
		for (int i = 0; i < textureNames.length; i++) {
			textures[i] = register.func_94245_a(textureNames[i]);
		}
	}

	public XieTexSideVariable(IconRegister register, String[] textureNames, String defaultTextureName) {
		this(register,textureNames);
		defaultTexture = register.func_94245_a(defaultTextureName);
	}

	@Override
	public Icon getTexFromSideAndMeta(int i, int j) {
		return this.getIconFromDamage(j);
	}

	@Override
	public Icon getIconFromDamage(int meta) {
		if (meta<textures.length) return textures[meta];
		if (defaultTexture!=null) return defaultTexture;
		return XieFailTex.getInstance().getFailTex();
	}

	@Override
	public XieTexType getType() {
		return XieTexType.SIDE_VARIABLE;
	}

}
