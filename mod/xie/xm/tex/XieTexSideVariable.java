package mod.xie.xm.tex;

import java.util.HashMap;

import mod.xie.xm.blocks.BlockSide;
import mod.xie.xm.file.XieLog;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieTexSideVariable extends XieTex {

	private Icon [] textures;
	private Icon defaultTexture;

	public XieTexSideVariable(IconRegister register, String[] textureFiles) {
		super(register);
		textures = new Icon[6];
		defaultTexture = null;
		parseSideVariantTextures(textureFiles);
		
		for (int i=0; i<6 && defaultTexture==null; i++) {
			if (textures[i]!=null) defaultTexture = textures[i];
		}
	}

	public XieTexSideVariable(IconRegister register, String[] textureNames, String defaultTextureName) {
		this(register,textureNames);
		defaultTexture = register(defaultTextureName);
	}

	@Override
	public Icon getTexFromSideAndMeta(int i, int j) {
		// ignore meta
		if (textures[i]==null) return defaultTexture;
		else return textures[i];
	}

	@Override
	public Icon getIconFromDamage(int meta) {
		return defaultTexture;
	}

	@Override
	public XieTexType getType() {
		return XieTexType.SIDE_VARIABLE;
	}

	protected void parseSideVariantTextures(String[] textureFiles) {
		for (int i=0; i < textureFiles.length; i++) {
			BlockSide side = parseSideValue(textureFiles[i]);
			if (side != null) {
				Icon tex = register(textureFiles[i]);
				this.setTexture(side,tex);
			} else XieLog.log().logIncomplete("Texture filename "+textureFiles[i]+" confused me, so I'm ignoring it. Sorry :3");
		}
	}

	protected void setTexture(BlockSide side, Icon tex) {
		if (side==BlockSide.ALL) {
			for (int i=0; i<6; i++) textures[i] = tex;
		} else if (side==BlockSide.ENDS) {
			textures[BlockSide.TOP.ordinal()] = tex;
			textures[BlockSide.BOTTOM.ordinal()] = tex;
		} else if (side==BlockSide.SIDES) {
			textures[BlockSide.NORTH.ordinal()] = tex;
			textures[BlockSide.EAST.ordinal()] = tex;
			textures[BlockSide.SOUTH.ordinal()] = tex;
			textures[BlockSide.WEST.ordinal()] = tex;
		} else {
			textures[side.ordinal()] = tex;
		}
	}

}
