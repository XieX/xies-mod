package mod.xie.xm.tex;

import java.util.HashMap;

import mod.xie.xm.blocks.BlockSide;
import mod.xie.xm.file.XieLog;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.util.Icon;

public class XieTexSideVariable extends XieTex {

	private Icon [] textures;
	private Icon defaultTexture;

	public XieTexSideVariable(IconRegister register, String[] textureNames) {
		super(register);
		textures = new Icon[6];
		defaultTexture = null;
		parseSideVariantTextures(textureNames);
	}

	public XieTexSideVariable(IconRegister register, String[] textureNames, String defaultTextureName) {
		this(register,textureNames);
		defaultTexture = register(defaultTextureName);
	}

	@Override
	public Icon getIcon(int i, int j) {
		if (i<6 && textures[i]!=null) return textures[i];
		else if (defaultTexture!=null) return defaultTexture;
		else return super.getIcon(i, j);
	}

	@Override
	public Icon getIconFromDamage(int meta) {
		if (defaultTexture!=null) return defaultTexture;
		else return super.getIconFromDamage(meta);
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
			textures[BlockSide.TOP.value()] = tex;
			textures[BlockSide.BOTTOM.value()] = tex;
		} else if (side==BlockSide.SIDES) {
			textures[BlockSide.NORTH.value()] = tex;
			textures[BlockSide.EAST.value()] = tex;
			textures[BlockSide.SOUTH.value()] = tex;
			textures[BlockSide.WEST.value()] = tex;
		} else {
			textures[side.value()] = tex;
		}
	}

}
