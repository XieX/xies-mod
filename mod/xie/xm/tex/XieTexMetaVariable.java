package mod.xie.xm.tex;

import java.util.HashMap;

import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieTexMetaVariable implements XieTex {

	private Icon [] textures;
	private Icon defaultTexture;
	
	public XieTexMetaVariable(IconRegister register, HashMap<Integer,String> texturesByMeta) {
		this(register, texturesByMeta, null);
	}

	public XieTexMetaVariable(IconRegister register, HashMap<Integer,String> texturesByMeta, String defaultTextureName) {
		defaultTexture = defaultTextureName == null ? XieFailTex.get().failTex(register) : register.func_94245_a(defaultTextureName);
		textures = new Icon[texturesByMeta.size()];
		for (int i=0; i < textures.length; i++) {
			String texName = texturesByMeta.get(i);
			textures[i] = texName == null ? defaultTexture : register.func_94245_a(texName);
		}
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
		return XieTexType.META_VARIABLE;
	}

}
